package bros.parraga

import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.ClubContactRequest
import bros.parraga.domain.UserRole
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.club.dto.CreateClubContactRequest
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
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ClubContactRequestTest : BaseIntegrationTest() {

    @Test
    fun `should accept anonymous club contact request`() = testApplicationWithClient { client ->
        val response = client.post("/club-contact-requests") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateClubContactRequest(
                    clubName = "Club de Tenis Ribera",
                    contactName = "Ana García",
                    email = "ana@clubribera.com",
                    phone = "+34 600 000 000",
                    message = "Queremos publicar nuestros torneos."
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val created = response.body<ApiResponse<ClubContactRequest>>().data
        assertNotNull(created)
        assertEquals("Club de Tenis Ribera", created.clubName)
        assertEquals("ana@clubribera.com", created.email)
    }

    @Test
    fun `should return 400 for blank or invalid fields`() = testApplicationWithClient { client ->
        val blankClub = client.post("/club-contact-requests") {
            contentType(ContentType.Application.Json)
            setBody(CreateClubContactRequest(clubName = "  ", contactName = "Ana", email = "ana@club.com"))
        }
        assertEquals(HttpStatusCode.BadRequest, blankClub.status)

        val badEmail = client.post("/club-contact-requests") {
            contentType(ContentType.Application.Json)
            setBody(CreateClubContactRequest(clubName = "Club", contactName = "Ana", email = "not-an-email"))
        }
        assertEquals(HttpStatusCode.BadRequest, badEmail.status)
    }

    @Test
    fun `should delete contact request only as platform admin`() = testApplicationWithClient { client ->
        transaction {
            UserDAO.new {
                username = "platform-admin"
                email = "platform@email.com"
                authProvider = "clerk"
                authSubject = "platform-admin-sub"
                role = UserRole.PLATFORM_ADMIN.name
            }
            UserDAO.new {
                username = "regular"
                email = "regular@email.com"
                authProvider = "clerk"
                authSubject = "regular-sub"
            }
        }

        val created = client.post("/club-contact-requests") {
            contentType(ContentType.Application.Json)
            setBody(CreateClubContactRequest(clubName = "Club", contactName = "Ana", email = "ana@club.com"))
        }.body<ApiResponse<ClubContactRequest>>().data
        assertNotNull(created)

        val adminToken = createAuthToken("platform-admin-sub", "platform@email.com", "platform admin")
        val regularToken = createAuthToken("regular-sub", "regular@email.com", "regular")

        val forbidden = client.delete("/club-contact-requests/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $regularToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)

        val deleted = client.delete("/club-contact-requests/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)

        val remaining = client.get("/club-contact-requests") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }.body<ApiResponse<List<ClubContactRequest>>>().data
        assertNotNull(remaining)
        assertEquals(0, remaining.size)

        val missing = client.delete("/club-contact-requests/999") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.NotFound, missing.status)
    }

    @Test
    fun `should list contact requests only for platform admin`() = testApplicationWithClient { client ->
        transaction {
            UserDAO.new {
                username = "platform-admin"
                email = "platform@email.com"
                authProvider = "clerk"
                authSubject = "platform-admin-sub"
                role = UserRole.PLATFORM_ADMIN.name
            }
            UserDAO.new {
                username = "regular"
                email = "regular@email.com"
                authProvider = "clerk"
                authSubject = "regular-sub"
            }
        }

        client.post("/club-contact-requests") {
            contentType(ContentType.Application.Json)
            setBody(CreateClubContactRequest(clubName = "Club", contactName = "Ana", email = "ana@club.com"))
        }

        val unauthenticated = client.get("/club-contact-requests")
        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)

        val regular = client.get("/club-contact-requests") {
            header(HttpHeaders.Authorization, "Bearer ${createAuthToken("regular-sub", "regular@email.com", "regular")}")
        }
        assertEquals(HttpStatusCode.Forbidden, regular.status)

        val admin = client.get("/club-contact-requests") {
            header(
                HttpHeaders.Authorization,
                "Bearer ${createAuthToken("platform-admin-sub", "platform@email.com", "platform admin")}"
            )
        }
        assertEquals(HttpStatusCode.OK, admin.status)
        val requests = admin.body<ApiResponse<List<ClubContactRequest>>>().data
        assertNotNull(requests)
        assertEquals(1, requests.size)
        assertEquals("Club", requests[0].clubName)
    }
}
