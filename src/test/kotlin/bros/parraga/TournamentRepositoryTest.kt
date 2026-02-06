package bros.parraga

import bros.parraga.db.schema.*
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.TournamentBracket
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TournamentRepositoryTest : BaseIntegrationTest() {

    @Test
    fun `should create and save all matches in a tournament`() = testApplicationWithClient { client ->
        createTestData()

        val response = client.post("/tournaments/1/start").body<ApiResponse<TournamentPhase>>()

        print(response)

        //assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `bracket endpoint groups matches by round`() = testApplicationWithClient { client ->
        createTestData(thirdPlacePlayoff = true)

        client.post("/tournaments/1/start").body<ApiResponse<TournamentPhase>>()

        val response = client.get("/tournaments/1/bracket").body<ApiResponse<TournamentBracket>>()
        val bracket = requireNotNull(response.data)
        assertEquals(1, bracket.phases.size)
        val rounds = bracket.phases.first().rounds
        assertTrue(rounds.isNotEmpty())
        val finalRound = rounds.maxOf { it.round }
        val finalMatches = rounds.first { it.round == finalRound }.matches
        assertEquals(2, finalMatches.size)
    }

    private fun createTestData(thirdPlacePlayoff: Boolean = false) {
        transaction {
            val user = UserDAO.new {
                username = "testUser"
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
                configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff)
            }

        }
    }
}



