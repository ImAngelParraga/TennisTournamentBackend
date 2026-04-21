package bros.parraga

import bros.parraga.db.DatabaseTables
import bros.parraga.db.schema.AchievementDAO
import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.User
import bros.parraga.domain.Achievement
import bros.parraga.domain.AchievementRuleType
import io.ktor.client.statement.bodyAsText
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.user.dto.CreateUserRequest
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserTest : BaseIntegrationTest() {
    override val tables = DatabaseTables.all

    private val testUser1 = CreateUserRequest("testUser1", "password123", "test1@email.com")
    private val testUser2 = CreateUserRequest("testUser2", "password456", "test2@email.com")

    @Test
    fun `should return all users`() = testApplicationWithClient { client ->
        createTestData()

        val response = client.get("/users")

        assertEquals(HttpStatusCode.OK, response.status)

        val users = response.body<ApiResponse<List<User>>>().data
        assertEquals(2, users?.size)
        assertTrue { users?.any { it.username == testUser1.username } == true }
        assertTrue { users?.any { it.username == testUser2.username } == true }

        val rawResponse = client.get("/users")
        assertTrue("achievements" !in rawResponse.bodyAsText())
    }

    @Test
    fun `should return a user by id`() = testApplicationWithClient { client ->
        createTestData()

        val response = client.get("/users/1")

        assertEquals(HttpStatusCode.OK, response.status)
        val user = response.body<ApiResponse<User>>().data
        assertNotNull(user)
        assertEquals(testUser1.username, user.username)
        assertEquals(testUser1.email, user.email)
        assertTrue(user.achievements.isEmpty())
    }

    @Test
    fun `should return tournament winner achievement only on single user response`() = testApplicationWithClient { client ->
        createTournamentWinnerData()

        val detailResponse = client.get("/users/1")

        assertEquals(HttpStatusCode.OK, detailResponse.status)
        val user = detailResponse.body<ApiResponse<User>>().data
        assertNotNull(user)
        assertEquals(2, user.achievements.size)
        assertEquals(
            listOf(
                Achievement(
                    id = user.achievements[0].id,
                    key = "tournament_winner",
                    name = "Tournament Winner",
                    description = "Awarded for winning at least one tournament."
                ),
                Achievement(
                    id = user.achievements[1].id,
                    key = "two_titles",
                    name = "Two Titles",
                    description = "Awarded for winning at least two tournaments."
                )
            ),
            user.achievements
        )

        val listResponse = client.get("/users")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertTrue("achievements" !in listResponse.bodyAsText())
    }

    @Test
    fun `should return 404 for non existing user`() = testApplicationWithClient { client ->
        val response = client.get("/users/999")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return 400 for invalid user id`() = testApplicationWithClient { client ->
        val response = client.get("/users/invalid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should require auth for user creation`() = testApplicationWithClient { client ->
        val response = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody(testUser1)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should block user creation even with auth`() = testApplicationWithClient { client ->
        val token = createAuthToken("clerk-user-1", "user1@email.com", "user1")
        val response = client.post("/users") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(testUser1)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `should block user update even with auth`() = testApplicationWithClient { client ->
        val token = createAuthToken("clerk-user-1", "user1@email.com", "user1")
        val response = client.put("/users") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"id":1,"username":"updated"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `should block user delete even with auth`() = testApplicationWithClient { client ->
        val token = createAuthToken("clerk-user-1", "user1@email.com", "user1")
        val response = client.delete("/users/1") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    private fun createTestData() {
        transaction {
            AchievementDAO.new {
                key = "tournament_winner"
                name = "Tournament Winner"
                description = "Awarded for winning at least one tournament."
                ruleType = AchievementRuleType.TOURNAMENT_WINS_AT_LEAST.name
                threshold = 1
                active = true
            }

            UserDAO.new {
                username = testUser1.username
                email = testUser1.email
                authProvider = "local"
                authSubject = null
            }

            UserDAO.new {
                username = testUser2.username
                email = testUser2.email
                authProvider = "local"
                authSubject = null
            }
        }
    }

    private fun createTournamentWinnerData() {
        transaction {
            AchievementDAO.new {
                key = "tournament_winner"
                name = "Tournament Winner"
                description = "Awarded for winning at least one tournament."
                ruleType = AchievementRuleType.TOURNAMENT_WINS_AT_LEAST.name
                threshold = 1
                active = true
            }
            AchievementDAO.new {
                key = "two_titles"
                name = "Two Titles"
                description = "Awarded for winning at least two tournaments."
                ruleType = AchievementRuleType.TOURNAMENT_WINS_AT_LEAST.name
                threshold = 2
                active = true
            }

            val user = UserDAO.new {
                username = testUser1.username
                email = testUser1.email
                authProvider = "clerk"
                authSubject = "winner-subject"
            }

            val clubOwner = UserDAO.new {
                username = "clubOwner"
                email = "owner@email.com"
                authProvider = "clerk"
                authSubject = "owner-subject"
            }

            val club = ClubDAO.new {
                name = "winners-club"
                phoneNumber = "123456789"
                address = "court 1"
                this.user = clubOwner
            }

            val player = PlayerDAO.new {
                name = "winner-player"
                external = false
                this.user = user
            }

            repeat(2) { index ->
                TournamentDAO.new {
                    name = "tournament-$index"
                    description = null
                    surface = null
                    this.club = club
                    status = bros.parraga.domain.TournamentStatus.COMPLETED.name
                    champion = player
                    startDate = Instant.now()
                    endDate = Instant.now()
                }
            }
        }
    }
}
