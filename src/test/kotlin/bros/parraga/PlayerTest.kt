package bros.parraga

import bros.parraga.db.schema.*
import bros.parraga.domain.Player
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.player.dto.CreatePlayerRequest
import bros.parraga.services.repositories.player.dto.UpdatePlayerRequest
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayerTest : BaseIntegrationTest() {
    override val tables = listOf(PlayersTable, UsersTable, TournamentPlayersTable, TournamentsTable, ClubsTable)

    private val testPlayer1 = CreatePlayerRequest("testPlayer1", true)
    private val testPlayer2 = CreatePlayerRequest("testPlayer2", true)
    private val testPlayerUpdate = UpdatePlayerRequest(id = 1, name = "testPlayerUpdated", external = true)

    @Test
    fun `should create a new player`() = testApplicationWithClient { client ->
        val response = client.post("/players") {
            contentType(ContentType.Application.Json)
            setBody(testPlayer1)
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val player = response.body<ApiResponse<Player>>().data
        assertEquals(testPlayer1.name, player?.name)
        assertEquals(testPlayer1.external, player?.external)
    }

    @Test
    fun `should return all players`() = testApplicationWithClient { client ->
        println("Transaction")
        createTestData()

        val response = client.get("/players")

        assertEquals(HttpStatusCode.OK, response.status)

        val players = response.body<ApiResponse<List<Player>>>().data
        assertEquals(2, players?.size)
        assertTrue { players?.any { it.name == testPlayer1.name } == true }
        assertTrue { players?.any { it.name == testPlayer2.name } == true }
    }

    @Test
    fun `should return a player by id`() = testApplicationWithClient { client ->
        createTestData()

        val response = client.get("/players/1")

        assertEquals(HttpStatusCode.OK, response.status)
        val player = response.body<ApiResponse<Player>>().data
        assertNotNull(player)
        assertEquals(testPlayer1.name, player.name)
        assertEquals(testPlayer1.external, player.external)
    }

    @Test
    fun `should return 404 for non existing player`() = testApplicationWithClient { client ->

        val response = client.get("/players/999")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should return 400 for invalid player id`() = testApplicationWithClient { client ->

        val response = client.get("/players/invalid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }


    @Test
    fun `should update an existing player`() = testApplicationWithClient { client ->
        createTestData()

        val response = client.put("/players") {
            contentType(ContentType.Application.Json)
            setBody(testPlayerUpdate)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val player = response.body<ApiResponse<Player>>().data
        assertNotNull(player)
        assertEquals(testPlayerUpdate.name, player.name)
        assertEquals(testPlayerUpdate.external, player.external)
    }

    @Test
    fun `should delete an existing player`() = testApplicationWithClient { client ->
        createTestData()

        val response = client.delete("/players/1")
        assertEquals(HttpStatusCode.NoContent, response.status)

        val getResponse = client.get("/players/1")
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun `should return 400 when deleting with invalid id`() = testApplicationWithClient { client ->
        val response = client.delete("/players/invalid")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should return 404 when deleting a non-existent player`() = testApplicationWithClient { client ->
        val response = client.delete("/players/999")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private fun createTestData() {
        transaction {
            PlayerDAO.new {
                name = testPlayer1.name
                external = testPlayer1.external
            }
            PlayerDAO.new {
                name = testPlayer2.name
                external = testPlayer2.external
            }
        }
    }
}