package bros.parraga

import bros.parraga.db.schema.*
import bros.parraga.domain.Match
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.SetScore
import bros.parraga.domain.TennisScore
import bros.parraga.domain.TournamentBasic
import bros.parraga.domain.TournamentBracket
import bros.parraga.domain.TournamentPhase
import bros.parraga.domain.TournamentStatus
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.match.dto.UpdateMatchScoreRequest
import bros.parraga.services.repositories.tournament.dto.AddPlayersRequest
import bros.parraga.services.repositories.tournament.dto.CreatePhaseRequest
import bros.parraga.services.repositories.tournament.dto.TournamentPlayerRequest
import bros.parraga.services.repositories.tournament.dto.UpdateTournamentRequest
import io.ktor.client.call.*
import io.ktor.client.request.*
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

class TournamentRepositoryTest : BaseIntegrationTest() {

    @Test
    fun `should create and save all matches in a tournament`() = testApplicationWithClient { client ->
        createTestData()
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val response = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ApiResponse<TournamentPhase>>()
        assertTrue(body.data?.matches?.isNotEmpty() == true)
    }

    @Test
    fun `bracket endpoint groups matches by round`() = testApplicationWithClient { client ->
        createTestData(thirdPlacePlayoff = true)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<ApiResponse<TournamentPhase>>()

        val response = client.get("/tournaments/1/bracket").body<ApiResponse<TournamentBracket>>()
        val bracket = requireNotNull(response.data)
        assertEquals(1, bracket.phases.size)
        val rounds = bracket.phases.first().rounds
        assertTrue(rounds.isNotEmpty())
        val finalRound = rounds.maxOf { it.round }
        val finalMatches = rounds.first { it.round == finalRound }.matches
        assertEquals(2, finalMatches.size)
    }

    @Test
    fun `should return conflict when adding players after tournament start`() = testApplicationWithClient { client ->
        createTestData()
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)

        val addPlayerResponse = client.post("/tournaments/1/players") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(AddPlayersRequest(listOf(TournamentPlayerRequest(name = "late-player"))))
        }
        assertEquals(HttpStatusCode.Conflict, addPlayerResponse.status)
    }

    @Test
    fun `should allow metadata update when tournament already started`() = testApplicationWithClient { client ->
        createTestData()
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)

        val updateResponse = client.put("/tournaments") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateTournamentRequest(id = 1, name = "updated-name", description = "fixed typo"))
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val body = updateResponse.body<ApiResponse<TournamentBasic>>()
        assertEquals("updated-name", body.data?.name)
        assertEquals("fixed typo", body.data?.description)
    }

    @Test
    fun `should return conflict when updating competition fields after tournament start`() = testApplicationWithClient { client ->
        createTestData()
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)

        val updateResponse = client.put("/tournaments") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateTournamentRequest(id = 1, clubId = 1))
        }

        assertEquals(HttpStatusCode.Conflict, updateResponse.status)
    }

    @Test
    fun `should return conflict when creating phase order without previous phase`() = testApplicationWithClient { client ->
        createTestData(initialPhaseOrders = emptyList())
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val response = client.post("/tournaments/1/phases") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreatePhaseRequest(
                    phaseOrder = 2,
                    format = bros.parraga.domain.PhaseFormat.KNOCKOUT,
                    configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
                )
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `should return conflict when starting tournament without phase order one`() = testApplicationWithClient { client ->
        createTestData(initialPhaseOrders = listOf(2))
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val response = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `should reset started tournament when no match was completed`() = testApplicationWithClient { client ->
        createTestData(playerCount = 4)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)

        val resetResponse = client.post("/tournaments/1/reset") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, resetResponse.status)
        val resetBody = resetResponse.body<ApiResponse<TournamentPhase>>()
        assertTrue(resetBody.data?.matches?.isEmpty() == true)

        val tournamentResponse = client.get("/tournaments/1")
        val tournamentBody = tournamentResponse.body<ApiResponse<TournamentBasic>>()
        assertEquals(TournamentStatus.DRAFT, tournamentBody.data?.status)
    }

    @Test
    fun `should return conflict when resetting tournament with completed matches`() = testApplicationWithClient { client ->
        createTestData(playerCount = 2)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val phase = startResponse.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")
        val finalMatchId = phase.matches.first().id

        val scoreResponse = client.put("/matches/$finalMatchId/score") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateMatchScoreRequest(
                    score = TennisScore(
                        sets = listOf(
                            SetScore(6, 4, null),
                            SetScore(6, 4, null)
                        )
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.OK, scoreResponse.status)

        val resetResponse = client.post("/tournaments/1/reset") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, resetResponse.status)
    }

    @Test
    fun `should mark tournament as completed when final match is scored`() = testApplicationWithClient { client ->
        createTestData(playerCount = 2)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val phase = startResponse.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")
        val finalMatchId = phase.matches.first().id

        val scoreResponse = client.put("/matches/$finalMatchId/score") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateMatchScoreRequest(
                    score = TennisScore(
                        sets = listOf(
                            SetScore(6, 2, null),
                            SetScore(6, 3, null)
                        )
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.OK, scoreResponse.status)
        val scoreBody = scoreResponse.body<ApiResponse<Match>>()
        assertEquals(bros.parraga.domain.MatchStatus.COMPLETED, scoreBody.data?.status)

        val tournamentResponse = client.get("/tournaments/1")
        val tournamentBody = tournamentResponse.body<ApiResponse<TournamentBasic>>()
        assertEquals(HttpStatusCode.OK, tournamentResponse.status)
        assertEquals(TournamentStatus.COMPLETED, tournamentBody.data?.status)
    }

    private fun createTestData(
        thirdPlacePlayoff: Boolean = false,
        initialPhaseOrders: List<Int> = listOf(1),
        playerCount: Int = 5
    ) {
        transaction {
            val user = UserDAO.new {
                username = "testUser"
                email = ""
                authProvider = "clerk"
                authSubject = "owner-subject"
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

            repeat(playerCount) {
                val player = PlayerDAO.new {
                    name = "testPlayer$it"
                    external = true
                    this.user = null
                }

                tournament.players = SizedCollection(tournament.players + player)
            }

            initialPhaseOrders.forEach { phaseOrder ->
                TournamentPhaseDAO.new {
                    this.tournament = tournament
                    this.phaseOrder = phaseOrder
                    format = Format.KNOCKOUT.name
                    rounds = 3
                    configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff)
                }
            }
        }
    }
}



