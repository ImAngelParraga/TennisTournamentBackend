package bros.parraga

import bros.parraga.db.schema.RatingEventDAO
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.Match
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.Player
import bros.parraga.domain.SetScore
import bros.parraga.domain.TennisScore
import bros.parraga.domain.TournamentBasic
import bros.parraga.domain.TournamentPhase
import bros.parraga.domain.TournamentStatus
import bros.parraga.domain.TournamentVisibility
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.match.dto.UpdateMatchScoreRequest
import bros.parraga.services.repositories.tournament.dto.AddPlayersRequest
import bros.parraga.services.repositories.tournament.dto.CreatePhaseRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentRequest
import bros.parraga.services.repositories.tournament.dto.JoinTournamentByCodeRequest
import bros.parraga.services.repositories.tournament.dto.TournamentPlayerRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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

class PrivateTournamentTest : BaseIntegrationTest() {
    @Test
    fun `private tournament is invite-only hidden from public list and skips public Elo`() =
        testApplicationWithClient { client ->
            val ownerToken = createAuthToken("private-owner", "private-owner@example.com", "Private Owner")
            val playerToken = createAuthToken("private-player", "private-player@example.com", "Private Player")
            val strangerToken = createAuthToken("private-stranger", "private-stranger@example.com", "Stranger")

            transaction {
                UserDAO.new {
                    username = "private-extra"
                    email = "private-extra@example.com"
                    authProvider = "clerk"
                    authSubject = "private-extra-subject"
                }
            }

            val tournament = client.post("/tournaments") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
                contentType(ContentType.Application.Json)
                setBody(
                    CreateTournamentRequest(
                        name = "Private Knockout",
                        description = null,
                        surface = null,
                        clubId = null,
                        startDate = kotlinx.datetime.Instant.parse("2026-02-01T00:00:00Z"),
                        endDate = kotlinx.datetime.Instant.parse("2026-02-02T00:00:00Z")
                    )
                )
            }.body<ApiResponse<TournamentBasic>>().data ?: error("missing tournament")

            assertEquals(null, tournament.clubId)
            assertEquals(TournamentVisibility.PRIVATE, tournament.visibility)
            assertNotNull(tournament.ownerUserId)
            assertEquals(8, tournament.inviteCode?.length)

            val publicList = client.get("/tournaments").body<ApiResponse<List<TournamentBasic>>>().data.orEmpty()
            assertTrue(publicList.none { it.id == tournament.id })

            val myTournaments = client.get("/users/me/tournaments") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
            }.body<ApiResponse<List<TournamentBasic>>>().data.orEmpty()
            assertTrue(myTournaments.any { it.id == tournament.id })

            val anonymousDetail = client.get("/tournaments/${tournament.id}")
            assertEquals(HttpStatusCode.NotFound, anonymousDetail.status)

            val strangerDetail = client.get("/tournaments/${tournament.id}") {
                header(HttpHeaders.Authorization, "Bearer $strangerToken")
            }
            assertEquals(HttpStatusCode.NotFound, strangerDetail.status)

            val joinResponse = client.post("/tournaments/join") {
                header(HttpHeaders.Authorization, "Bearer $playerToken")
                contentType(ContentType.Application.Json)
                setBody(JoinTournamentByCodeRequest(tournament.inviteCode!!))
            }
            assertEquals(HttpStatusCode.OK, joinResponse.status)

            val addExtra = client.post("/tournaments/${tournament.id}/players") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
                contentType(ContentType.Application.Json)
                setBody(AddPlayersRequest(listOf(TournamentPlayerRequest(email = "private-extra@example.com"))))
            }
            assertEquals(HttpStatusCode.OK, addExtra.status)

            val players = client.get("/tournaments/${tournament.id}/players") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
            }.body<ApiResponse<List<Player>>>().data ?: error("missing players")
            assertEquals(2, players.size)

            val createPhase = client.post("/tournaments/${tournament.id}/phases") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
                contentType(ContentType.Application.Json)
                setBody(
                    CreatePhaseRequest(
                        phaseOrder = 1,
                        format = PhaseFormat.KNOCKOUT,
                        configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, createPhase.status)

            val joinRequest = client.post("/tournaments/${tournament.id}/join-requests") {
                header(HttpHeaders.Authorization, "Bearer $strangerToken")
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            assertEquals(HttpStatusCode.Conflict, joinRequest.status)

            client.post("/tournaments/${tournament.id}/start") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
            }.body<ApiResponse<TournamentPhase>>().data ?: error("missing phase")

            scoreAll(client, ownerToken, tournament.id)

            val completed = client.get("/tournaments/${tournament.id}") {
                header(HttpHeaders.Authorization, "Bearer $ownerToken")
            }.body<ApiResponse<TournamentBasic>>().data ?: error("missing completed")
            assertEquals(TournamentStatus.COMPLETED, completed.status)

            transaction {
                assertTrue(RatingEventDAO.all().empty(), "private tournaments must not create public Elo events")
            }
        }

    private suspend fun scoreAll(client: HttpClient, token: String, tournamentId: Int) {
        while (true) {
            val scheduled = client.get("/tournaments/$tournamentId/matches") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<ApiResponse<List<Match>>>().data.orEmpty()
                .filter { it.status == MatchStatus.SCHEDULED && it.player1 != null && it.player2 != null }
            if (scheduled.isEmpty()) return
            scheduled.forEach { match ->
                val response = client.put("/matches/${match.id}/score") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(UpdateMatchScoreRequest(TennisScore(listOf(SetScore(6, 4, null), SetScore(6, 4, null)))))
                }
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }
    }
}
