package bros.parraga

import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentJoinRequestDAO
import bros.parraga.db.schema.TournamentJoinRequestsTable
import bros.parraga.db.schema.TournamentPhaseDAO
import bros.parraga.db.schema.TournamentPlayerDAO
import bros.parraga.db.schema.TournamentPlayersTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.TournamentJoinRequest
import bros.parraga.domain.TournamentJoinRequestStatus
import bros.parraga.domain.TournamentStatus
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.tournament.dto.AcceptTournamentJoinRequest
import bros.parraga.services.repositories.tournament.dto.AddPlayersRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentJoinRequest
import bros.parraga.services.repositories.tournament.dto.DecideTournamentJoinRequest
import bros.parraga.services.repositories.tournament.dto.TournamentPlayerRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TournamentJoinRequestTest : BaseIntegrationTest() {
    @Test
    fun `join request endpoints require authentication`() = testApplicationWithClient { client ->
        val fixture = seedTournament()

        val createResponse = client.post("/tournaments/${fixture.tournamentId}/join-requests") {
            contentType(ContentType.Application.Json)
            setBody(CreateTournamentJoinRequest())
        }
        assertEquals(HttpStatusCode.Unauthorized, createResponse.status)

        val managerListResponse = client.get("/tournaments/${fixture.tournamentId}/join-requests")
        assertEquals(HttpStatusCode.Unauthorized, managerListResponse.status)

        val myRequestsResponse = client.get("/users/me/tournament-join-requests")
        assertEquals(HttpStatusCode.Unauthorized, myRequestsResponse.status)
    }

    @Test
    fun `player can request join and missing player profile is auto-created`() = testApplicationWithClient { client ->
        val fixture = seedTournament()
        val playerToken = createAuthToken("player-subject", "player@email.com", "Player User")

        val response = client.post("/tournaments/${fixture.tournamentId}/join-requests") {
            header(HttpHeaders.Authorization, "Bearer $playerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTournamentJoinRequest(playerName = "Join Player", note = "Please add me"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val request = response.body<ApiResponse<TournamentJoinRequest>>().data ?: error("missing request")
        assertEquals(TournamentJoinRequestStatus.PENDING, request.status)
        assertEquals("Join Player", request.player.name)
        assertEquals("Please add me", request.playerNote)

        val myRequests = client.get("/users/me/tournament-join-requests") {
            header(HttpHeaders.Authorization, "Bearer $playerToken")
        }.body<ApiResponse<List<TournamentJoinRequest>>>().data ?: error("missing my requests")

        assertEquals(listOf(request.id), myRequests.map { it.id })
        transaction {
            val player = PlayerDAO[request.player.id]
            assertEquals(false, player.external)
            assertNotNull(player.user)
        }
    }

    @Test
    fun `duplicate pending and accepted requests are conflicts`() = testApplicationWithClient { client ->
        val fixture = seedTournament()
        val ownerToken = createAuthToken("owner-subject", "owner@email.com", "Owner")
        val playerToken = createAuthToken("player-subject", "player@email.com", "Player User")

        val first = createJoinRequest(client, fixture.tournamentId, playerToken)
        val duplicatePending = client.post("/tournaments/${fixture.tournamentId}/join-requests") {
            header(HttpHeaders.Authorization, "Bearer $playerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTournamentJoinRequest())
        }
        assertEquals(HttpStatusCode.Conflict, duplicatePending.status)

        val acceptResponse = client.post("/tournaments/${fixture.tournamentId}/join-requests/${first.id}/accept") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(AcceptTournamentJoinRequest())
        }
        assertEquals(HttpStatusCode.OK, acceptResponse.status)

        val duplicateAccepted = client.post("/tournaments/${fixture.tournamentId}/join-requests") {
            header(HttpHeaders.Authorization, "Bearer $playerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTournamentJoinRequest())
        }
        assertEquals(HttpStatusCode.Conflict, duplicateAccepted.status)
    }

    @Test
    fun `manager can accept join request and add player to tournament`() = testApplicationWithClient { client ->
        val fixture = seedTournament()
        val ownerToken = createAuthToken("owner-subject", "owner@email.com", "Owner")
        val playerToken = createAuthToken("player-subject", "player@email.com", "Player User")
        val request = createJoinRequest(client, fixture.tournamentId, playerToken)

        val response = client.post("/tournaments/${fixture.tournamentId}/join-requests/${request.id}/accept") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(AcceptTournamentJoinRequest(seed = 1, note = "Accepted"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val accepted = response.body<ApiResponse<TournamentJoinRequest>>().data ?: error("missing accepted request")
        assertEquals(TournamentJoinRequestStatus.ACCEPTED, accepted.status)
        assertEquals("Accepted", accepted.managerNote)

        transaction {
            val association = TournamentPlayerDAO.find {
                (TournamentPlayersTable.tournamentId eq fixture.tournamentId) and
                    (TournamentPlayersTable.playerId eq request.player.id)
            }.firstOrNull()
            assertNotNull(association)
            assertEquals(1, association.seed)
        }
    }

    @Test
    fun `rejected request observes cooldown until manager allows resubmit`() = testApplicationWithClient { client ->
        val fixture = seedTournament()
        val ownerToken = createAuthToken("owner-subject", "owner@email.com", "Owner")
        val playerToken = createAuthToken("player-subject", "player@email.com", "Player User")
        val request = createJoinRequest(client, fixture.tournamentId, playerToken)

        val rejectResponse = client.post("/tournaments/${fixture.tournamentId}/join-requests/${request.id}/reject") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(DecideTournamentJoinRequest(note = "Full"))
        }
        assertEquals(HttpStatusCode.OK, rejectResponse.status)

        val blockedResubmit = client.post("/tournaments/${fixture.tournamentId}/join-requests") {
            header(HttpHeaders.Authorization, "Bearer $playerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTournamentJoinRequest(note = "Trying again"))
        }
        assertEquals(HttpStatusCode.Conflict, blockedResubmit.status)

        val unlockResponse = client.post("/tournaments/${fixture.tournamentId}/join-requests/${request.id}/allow-resubmit") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, unlockResponse.status)

        val resubmit = client.post("/tournaments/${fixture.tournamentId}/join-requests") {
            header(HttpHeaders.Authorization, "Bearer $playerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTournamentJoinRequest(note = "Trying again"))
        }
        assertEquals(HttpStatusCode.OK, resubmit.status)
        val pendingAgain = resubmit.body<ApiResponse<TournamentJoinRequest>>().data ?: error("missing request")
        assertEquals(TournamentJoinRequestStatus.PENDING, pendingAgain.status)
        assertEquals("Trying again", pendingAgain.playerNote)
    }

    @Test
    fun `starting tournament expires pending join requests`() = testApplicationWithClient { client ->
        val fixture = seedTournament(playerCount = 2, withPhase = true)
        val ownerToken = createAuthToken("owner-subject", "owner@email.com", "Owner")
        val playerToken = createAuthToken("player-subject", "player@email.com", "Player User")
        val request = createJoinRequest(client, fixture.tournamentId, playerToken)

        val startResponse = client.post("/tournaments/${fixture.tournamentId}/start") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)

        transaction {
            assertEquals(TournamentJoinRequestStatus.EXPIRED.name, TournamentJoinRequestDAO[request.id].status)
        }
    }

    @Test
    fun `manual manager add marks matching pending join request accepted`() = testApplicationWithClient { client ->
        val fixture = seedTournament()
        val ownerToken = createAuthToken("owner-subject", "owner@email.com", "Owner")
        val playerToken = createAuthToken("player-subject", "player@email.com", "Player User")
        val request = createJoinRequest(client, fixture.tournamentId, playerToken)

        val addResponse = client.post("/tournaments/${fixture.tournamentId}/players") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(AddPlayersRequest(listOf(TournamentPlayerRequest(playerId = request.player.id))))
        }
        assertEquals(HttpStatusCode.OK, addResponse.status)

        transaction {
            val joinRequest = TournamentJoinRequestDAO[request.id]
            assertEquals(TournamentJoinRequestStatus.ACCEPTED.name, joinRequest.status)
            assertEquals(fixture.ownerUserId, joinRequest.decidedBy?.id?.value)
        }
    }

    @Test
    fun `outsider cannot manage tournament join requests`() = testApplicationWithClient { client ->
        val fixture = seedTournament()
        val playerToken = createAuthToken("player-subject", "player@email.com", "Player User")
        val outsiderToken = createAuthToken("outsider-subject", "outsider@email.com", "Outsider")
        val request = createJoinRequest(client, fixture.tournamentId, playerToken)

        val listResponse = client.get("/tournaments/${fixture.tournamentId}/join-requests") {
            header(HttpHeaders.Authorization, "Bearer $outsiderToken")
        }
        assertEquals(HttpStatusCode.Forbidden, listResponse.status)

        val acceptResponse = client.post("/tournaments/${fixture.tournamentId}/join-requests/${request.id}/accept") {
            header(HttpHeaders.Authorization, "Bearer $outsiderToken")
            contentType(ContentType.Application.Json)
            setBody(AcceptTournamentJoinRequest())
        }
        assertEquals(HttpStatusCode.Forbidden, acceptResponse.status)
    }

    private suspend fun createJoinRequest(
        client: io.ktor.client.HttpClient,
        tournamentId: Int,
        token: String
    ): TournamentJoinRequest {
        val response = client.post("/tournaments/$tournamentId/join-requests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTournamentJoinRequest(playerName = "Join Player"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body<ApiResponse<TournamentJoinRequest>>().data ?: error("missing request")
    }

    private fun seedTournament(playerCount: Int = 0, withPhase: Boolean = false): JoinFixture = transaction {
        val owner = UserDAO.new {
            username = "owner"
            email = "owner@email.com"
            authProvider = "clerk"
            authSubject = "owner-subject"
        }
        UserDAO.new {
            username = "outsider"
            email = "outsider@email.com"
            authProvider = "clerk"
            authSubject = "outsider-subject"
        }
        val club = ClubDAO.new {
            name = "Club"
            phoneNumber = null
            address = null
            user = owner
        }
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val tournament = TournamentDAO.new {
            name = "Tournament"
            description = null
            surface = null
            status = TournamentStatus.DRAFT.name
            this.club = club
            startDate = now
            endDate = now.plus(1, ChronoUnit.DAYS)
        }
        repeat(playerCount) { index ->
            val player = PlayerDAO.new {
                name = "Existing Player $index"
                external = true
            }
            TournamentPlayerDAO.new {
                this.tournament = tournament
                this.player = player
            }
        }
        if (withPhase) {
            TournamentPhaseDAO.new {
                this.tournament = tournament
                phaseOrder = 1
                format = PhaseFormat.KNOCKOUT.name
                rounds = 1
                configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
            }
        }

        JoinFixture(ownerUserId = owner.id.value, tournamentId = tournament.id.value)
    }

    private data class JoinFixture(
        val ownerUserId: Int,
        val tournamentId: Int
    )
}
