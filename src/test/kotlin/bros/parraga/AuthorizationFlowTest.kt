package bros.parraga

import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentPhaseDAO
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.services.repositories.club.dto.CreateClubRequest
import bros.parraga.services.repositories.player.dto.CreatePlayerRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.transaction
import parraga.bros.tournament.domain.Format
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthorizationFlowTest : BaseIntegrationTest() {

    @Test
    fun `should return 401 for unauthenticated write endpoint`() = testApplicationWithClient { client ->
        val response = client.post("/clubs") {
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest(name = "club", phoneNumber = null, address = null))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `should return 403 for non manager tournament start`() = testApplicationWithClient { client ->
        seedManagedTournament()
        val outsiderToken = createAuthToken("outsider-sub", "outsider@email.com", "outsider")

        val response = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $outsiderToken")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `should allow owner to add admin and admin to start tournament`() = testApplicationWithClient { client ->
        seedManagedTournament()
        val ownerToken = createAuthToken("owner-sub", "owner@email.com", "owner")
        val adminToken = createAuthToken("admin-sub", "admin@email.com", "admin")

        val addAdminResponse = client.post("/clubs/1/admins/2") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, addAdminResponse.status)

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
    }

    @Test
    fun `should auto provision local user from token on first write`() = testApplicationWithClient { client ->
        val token = createAuthToken("new-clerk-sub", "newuser@email.com", "newuser")

        val createPlayerResponse = client.post("/players") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreatePlayerRequest(name = "new player"))
        }
        assertEquals(HttpStatusCode.Created, createPlayerResponse.status)

        val users = transaction { UserDAO.all().map { it.toDomain() } }
        assertTrue(users.any { it.authSubject == "new-clerk-sub" })
    }

    private fun seedManagedTournament() {
        transaction {
            val owner = UserDAO.new {
                username = "owner"
                email = "owner@email.com"
                authProvider = "clerk"
                authSubject = "owner-sub"
            }

            UserDAO.new {
                username = "admin"
                email = "admin@email.com"
                authProvider = "clerk"
                authSubject = "admin-sub"
            }

            UserDAO.new {
                username = "outsider"
                email = "outsider@email.com"
                authProvider = "clerk"
                authSubject = "outsider-sub"
            }

            val club = ClubDAO.new {
                name = "Club 1"
                phoneNumber = null
                address = null
                user = owner
            }

            val date = Instant.now()
            val tournament = TournamentDAO.new {
                name = "Tournament 1"
                description = null
                surface = null
                this.club = club
                startDate = date
                endDate = date.plus(1, ChronoUnit.DAYS)
            }

            repeat(4) {
                val player = PlayerDAO.new {
                    name = "Player $it"
                    external = true
                    this.user = null
                }
                tournament.players = SizedCollection(tournament.players + player)
            }

            TournamentPhaseDAO.new {
                this.tournament = tournament
                phaseOrder = 1
                format = Format.KNOCKOUT.name
                rounds = 3
                configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
            }
        }
    }
}
