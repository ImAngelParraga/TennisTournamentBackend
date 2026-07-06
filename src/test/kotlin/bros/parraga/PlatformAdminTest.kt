package bros.parraga

import bros.parraga.db.schema.ClubAdminsTable
import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.ClubsTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import bros.parraga.domain.Club
import bros.parraga.domain.User
import bros.parraga.domain.UserRole
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.club.dto.CreateClubRequest
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlatformAdminTest : BaseIntegrationTest() {

    @Test
    fun `should return 403 for club creation by regular user`() = testApplicationWithClient { client ->
        seedPlatformFixture()
        val token = createAuthToken("regular-sub", "regular@email.com", "regular")

        val response = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest(name = "Self Service Club", phoneNumber = null, address = null, ownerUserId = 1))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `should allow platform admin to create club for another user`() = testApplicationWithClient { client ->
        val fixture = seedPlatformFixture()
        val adminToken = createAuthToken("platform-admin-sub", "platform@email.com", "platform admin")

        val response = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateClubRequest(
                    name = "Provisioned Club",
                    phoneNumber = null,
                    address = null,
                    ownerUserId = fixture.regularUserId
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val club = response.body<ApiResponse<Club>>().data
        assertNotNull(club)
        assertEquals(fixture.regularUserId, club.user.id)
    }

    @Test
    fun `should return 404 for club creation with unknown owner`() = testApplicationWithClient { client ->
        seedPlatformFixture()
        val adminToken = createAuthToken("platform-admin-sub", "platform@email.com", "platform admin")

        val response = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest(name = "Orphan Club", phoneNumber = null, address = null, ownerUserId = 999))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return 403 for club deletion by its owner`() = testApplicationWithClient { client ->
        val fixture = seedPlatformFixture()
        val ownerToken = createAuthToken("club-owner-sub", "owner@email.com", "club owner")

        val response = client.delete("/clubs/${fixture.clubId}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `should allow platform admin to delete club`() = testApplicationWithClient { client ->
        val fixture = seedPlatformFixture()
        val adminToken = createAuthToken("platform-admin-sub", "platform@email.com", "platform admin")

        val response = client.delete("/clubs/${fixture.clubId}") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should expose role and managed club ids on users me`() = testApplicationWithClient { client ->
        val fixture = seedPlatformFixture()

        val ownerMe = client.get("/users/me") {
            header(HttpHeaders.Authorization, "Bearer ${createAuthToken("club-owner-sub", "owner@email.com", "club owner")}")
        }.body<ApiResponse<User>>().data
        assertNotNull(ownerMe)
        assertEquals(UserRole.USER, ownerMe.role)
        assertEquals(listOf(fixture.clubId), ownerMe.managedClubIds)

        val clubAdminMe = client.get("/users/me") {
            header(HttpHeaders.Authorization, "Bearer ${createAuthToken("club-admin-sub", "clubadmin@email.com", "club admin")}")
        }.body<ApiResponse<User>>().data
        assertNotNull(clubAdminMe)
        assertEquals(listOf(fixture.clubId), clubAdminMe.managedClubIds)

        val regularMe = client.get("/users/me") {
            header(HttpHeaders.Authorization, "Bearer ${createAuthToken("regular-sub", "regular@email.com", "regular")}")
        }.body<ApiResponse<User>>().data
        assertNotNull(regularMe)
        assertEquals(emptyList(), regularMe.managedClubIds)

        val platformAdminMe = client.get("/users/me") {
            header(HttpHeaders.Authorization, "Bearer ${createAuthToken("platform-admin-sub", "platform@email.com", "platform admin")}")
        }.body<ApiResponse<User>>().data
        assertNotNull(platformAdminMe)
        assertEquals(UserRole.PLATFORM_ADMIN, platformAdminMe.role)
    }

    private data class PlatformFixture(
        val platformAdminUserId: Int,
        val ownerUserId: Int,
        val clubAdminUserId: Int,
        val regularUserId: Int,
        val clubId: Int
    )

    private fun seedPlatformFixture(): PlatformFixture = transaction {
        val platformAdmin = UserDAO.new {
            username = "platform-admin"
            email = "platform@email.com"
            authProvider = "clerk"
            authSubject = "platform-admin-sub"
            role = UserRole.PLATFORM_ADMIN.name
        }

        val owner = UserDAO.new {
            username = "club-owner"
            email = "owner@email.com"
            authProvider = "clerk"
            authSubject = "club-owner-sub"
        }

        val clubAdmin = UserDAO.new {
            username = "club-admin"
            email = "clubadmin@email.com"
            authProvider = "clerk"
            authSubject = "club-admin-sub"
        }

        val regular = UserDAO.new {
            username = "regular"
            email = "regular@email.com"
            authProvider = "clerk"
            authSubject = "regular-sub"
        }

        val club = ClubDAO.new {
            name = "Provisioned Club"
            phoneNumber = null
            address = null
            user = owner
        }

        ClubAdminsTable.insert {
            it[clubId] = DaoEntityID(club.id.value, ClubsTable)
            it[userId] = DaoEntityID(clubAdmin.id.value, UsersTable)
        }

        PlatformFixture(
            platformAdminUserId = platformAdmin.id.value,
            ownerUserId = owner.id.value,
            clubAdminUserId = clubAdmin.id.value,
            regularUserId = regular.id.value,
            clubId = club.id.value
        )
    }
}
