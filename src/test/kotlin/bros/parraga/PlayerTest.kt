package bros.parraga

import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.PlayersTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import bros.parraga.domain.Player
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.player.dto.CreatePlayerRequest
import bros.parraga.services.repositories.player.dto.UpdatePlayerRequest
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

class PlayerTest : BaseIntegrationTest() {
    override val tables = listOf(UsersTable, PlayersTable)

    @Test
    fun `should create player for authenticated user`() = testApplicationWithClient { client ->
        val token = createAuthToken("clerk-user-1", "user1@email.com", "user1")

        val response = client.post("/players") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreatePlayerRequest("testPlayer1"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val player = response.body<ApiResponse<Player>>().data
        assertEquals("testPlayer1", player?.name)
        assertEquals(false, player?.external)
        assertEquals("user1", player?.user?.username)
    }

    @Test
    fun `should return 401 for unauthenticated player creation`() = testApplicationWithClient { client ->
        val response = client.post("/players") {
            contentType(ContentType.Application.Json)
            setBody(CreatePlayerRequest("testPlayer1"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return 409 when authenticated user creates second profile`() = testApplicationWithClient { client ->
        val token = createAuthToken("clerk-user-1", "user1@email.com", "user1")

        client.post("/players") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreatePlayerRequest("testPlayer1"))
        }

        val response = client.post("/players") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreatePlayerRequest("testPlayer2"))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `should update own player profile`() = testApplicationWithClient { client ->
        val token = createAuthToken("clerk-user-1", "user1@email.com", "user1")
        val createResponse = client.post("/players") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreatePlayerRequest("testPlayer1"))
        }
        val createdPlayer = createResponse.body<ApiResponse<Player>>().data
        val playerId = requireNotNull(createdPlayer).id

        val response = client.put("/players") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdatePlayerRequest(id = playerId, name = "testPlayerUpdated"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val player = response.body<ApiResponse<Player>>().data
        assertNotNull(player)
        assertEquals("testPlayerUpdated", player.name)
    }

    @Test
    fun `should return 403 when updating another users player`() = testApplicationWithClient { client ->
        val token1 = createAuthToken("clerk-user-1", "user1@email.com", "user1")
        val token2 = createAuthToken("clerk-user-2", "user2@email.com", "user2")

        val createResponse = client.post("/players") {
            header(HttpHeaders.Authorization, "Bearer $token1")
            contentType(ContentType.Application.Json)
            setBody(CreatePlayerRequest("testPlayer1"))
        }
        val createdPlayer = createResponse.body<ApiResponse<Player>>().data
        val playerId = requireNotNull(createdPlayer).id

        val response = client.put("/players") {
            header(HttpHeaders.Authorization, "Bearer $token2")
            contentType(ContentType.Application.Json)
            setBody(UpdatePlayerRequest(id = playerId, name = "hijack"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `should delete own player profile`() = testApplicationWithClient { client ->
        val token = createAuthToken("clerk-user-1", "user1@email.com", "user1")
        val createResponse = client.post("/players") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreatePlayerRequest("testPlayer1"))
        }
        val createdPlayer = createResponse.body<ApiResponse<Player>>().data
        val playerId = requireNotNull(createdPlayer).id

        val response = client.delete("/players/$playerId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `should return all players publicly`() = testApplicationWithClient { client ->
        createTestData()
        val response = client.get("/players")

        assertEquals(HttpStatusCode.OK, response.status)
        val players = response.body<ApiResponse<List<Player>>>().data
        assertEquals(2, players?.size)
        assertTrue { players?.any { it.name == "testPlayer1" } == true }
        assertTrue { players?.any { it.name == "testPlayer2" } == true }
    }

    private fun createTestData() {
        transaction {
            PlayerDAO.new {
                name = "testPlayer1"
                external = true
            }
            PlayerDAO.new {
                name = "testPlayer2"
                external = true
            }

            UserDAO.new {
                username = "seedUser"
                email = "seed@email.com"
                authProvider = "local"
                authSubject = null
            }
        }
    }
}
