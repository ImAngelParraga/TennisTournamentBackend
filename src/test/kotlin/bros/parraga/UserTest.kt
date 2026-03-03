package bros.parraga

import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import bros.parraga.domain.User
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserTest : BaseIntegrationTest() {
    override val tables = listOf(UsersTable)

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
}
