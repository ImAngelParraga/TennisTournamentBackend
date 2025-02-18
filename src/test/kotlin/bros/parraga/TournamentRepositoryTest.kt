package bros.parraga

import bros.parraga.db.schema.*
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.TournamentPhase
import bros.parraga.routes.ApiResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.transaction
import parraga.bros.tournament.domain.Format
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test

class TournamentRepositoryTest : BaseIntegrationTest() {

    @Test
    fun `should create and save all matches in a tournament`() = testApplicationWithClient { client ->
        createTestData()

        val response = client.post("/tournaments/1/start").body<ApiResponse<TournamentPhase>>()

        print(response)

        //assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun createTestData() {
        transaction {
            val user = UserDAO.new {
                username = "testUser"
                password = "password123"
                email = ""
            }

            val club = ClubDAO.new {
                name = "testClub"
                phoneNumber = "123456789"
                address = "testAddress"
                this.user = user
            }

            val date = Instant.now()
            val tournament = TournamentDAO.new {
                name = "testTournament"
                description = "testDescription"
                surface = null
                this.club = club
                startDate = date
                endDate = date.plus(1, ChronoUnit.DAYS)
            }

            repeat(5) {
                val player = PlayerDAO.new {
                    name = "testPlayer$it"
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
                configuration = PhaseConfiguration.KnockoutConfig(false)
            }

        }
    }
}