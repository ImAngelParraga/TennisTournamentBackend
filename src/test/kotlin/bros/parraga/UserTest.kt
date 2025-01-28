package bros.parraga

import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import bros.parraga.domain.User
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.user.dto.CreateUserRequest
import bros.parraga.services.repositories.user.dto.UpdateUserRequest
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserTest : BaseIntegrationTest() {
    override val tables = listOf(UsersTable)

    private val testUser1 = CreateUserRequest("testUser1", "password123", "test1@email.com")
    private val testUser2 = CreateUserRequest("testUser2", "password456", "test2@email.com")

    @Test
    fun `should create a new user`() = testApplicationWithClient { client ->
        val response = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody(testUser1)
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val user = response.body<ApiResponse<User>>().data
        assertEquals(testUser1.username, user?.username)
        assertEquals(testUser1.email, user?.email)
    }

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
    fun `should update an existing user`() = testApplicationWithClient { client ->
        createTestData()

        val testUserUpdate = UpdateUserRequest(id = 1, username = "testUserUpdated", email = "updated@email.com")

        val response = client.put("/users") {
            contentType(ContentType.Application.Json)
            setBody(testUserUpdate)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val user = response.body<ApiResponse<User>>().data
        assertNotNull(user)
        assertEquals(testUserUpdate.username, user.username)
        assertEquals(testUserUpdate.email, user.email)
    }

    @Test
    fun `should delete an existing user`() = testApplicationWithClient { client ->
        createTestData()

        val response = client.delete("/users/1")
        assertEquals(HttpStatusCode.NoContent, response.status)

        val getResponse = client.get("/users/1")
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun `should return 400 when deleting with invalid id`() = testApplicationWithClient { client ->
        val response = client.delete("/users/invalid")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should return 404 when deleting a non-existent user`() = testApplicationWithClient { client ->
        val response = client.delete("/users/999")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private fun createTestData() {
        transaction {
            UserDAO.new {
                username = testUser1.username
                password = testUser1.password
                email = testUser1.email
                createdAt = Instant.now()
            }

            UserDAO.new {
                username = testUser2.username
                password = testUser2.password
                email = testUser2.email
                createdAt = Instant.now()
            }
        }
    }
}