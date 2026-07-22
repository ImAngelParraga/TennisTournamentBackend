package bros.parraga

import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.RatingEventDAO
import bros.parraga.db.schema.RatingEventsTable
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentPhaseDAO
import bros.parraga.db.schema.TournamentPlayerDAO
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.Match
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.RatingEvent
import bros.parraga.domain.SetScore
import bros.parraga.domain.TennisScore
import bros.parraga.domain.TournamentBasic
import bros.parraga.domain.TournamentPhase
import bros.parraga.domain.TournamentStatus
import bros.parraga.domain.User
import bros.parraga.routes.ApiResponse
import bros.parraga.services.rating.EloCalculator
import bros.parraga.services.repositories.match.dto.UpdateMatchScoreRequest
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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RatingIntegrationTest : BaseIntegrationTest() {

    @Test
    fun `four player knockout records match events plus champion and finalist bonuses`() =
        testApplicationWithClient { client ->
            val fixture = createRegisteredKnockout(playerCount = 4)
            val token = createAuthToken(fixture.ownerSubject, fixture.ownerEmail, "owner")

            val startResponse = client.post("/tournaments/${fixture.tournamentId}/start") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, startResponse.status)

            scoreAllScheduled(client, token, fixture.tournamentId)

            val tournament = client.get("/tournaments/${fixture.tournamentId}")
                .body<ApiResponse<TournamentBasic>>().data
            assertEquals(TournamentStatus.COMPLETED, tournament?.status)

            val championPlayerId = transaction {
                assertNotNull(TournamentDAO[fixture.tournamentId].champion).id.value
            }

            transaction {
                val allEvents = RatingEventDAO.all().toList()
                val matchEvents = allEvents.filter { it.reason == "MATCH" }
                val bonusEvents = allEvents.filter { it.reason == "TOURNAMENT_BONUS" }

                // 3 played matches (2 semifinals + final), 2 rating events each.
                assertEquals(6, matchEvents.size)
                // Champion + finalist.
                assertEquals(2, bonusEvents.size)

                // Pre-bonus rating for each registered player is the ratingAfter of
                // their last MATCH event; the service computes the field average from
                // exactly those values, so the bonus must match the formula fed with them.
                val preBonusRatings = fixture.playerIds.map { playerId ->
                    RatingEventDAO.find {
                        (RatingEventsTable.playerId eq playerId) and (RatingEventsTable.reason eq "MATCH")
                    }
                        .sortedWith(
                            compareByDescending<RatingEventDAO> { it.createdAt }.thenByDescending { it.id.value }
                        )
                        .first()
                        .ratingAfter
                }
                val avgFieldRating = preBonusRatings.average()
                val (expectedChampBonus, expectedFinalistBonus) =
                    EloCalculator.tournamentBonus(4, 1, avgFieldRating)

                val championBonus = bonusEvents.first { it.player.id.value == championPlayerId }
                val finalistBonus = bonusEvents.first { it.player.id.value != championPlayerId }
                assertEquals(expectedChampBonus, championBonus.delta)
                assertEquals(expectedFinalistBonus, finalistBonus.delta)
            }

            // GET /users surfaces rating + ratedMatches; the champion played 2 matches.
            val championUserId = fixture.userIds[fixture.playerIds.indexOf(championPlayerId)]
            val users = client.get("/users").body<ApiResponse<List<User>>>().data
            assertNotNull(users)
            val championUser = users.first { it.id == championUserId }
            assertTrue(championUser.rating > 1000, "champion should have gained rating")
            assertEquals(2, championUser.ratedMatches)

            // Rating history: newest first, ending with the completion bonus.
            val history = client.get("/users/$championUserId/rating-history")
                .body<ApiResponse<List<RatingEvent>>>().data
            assertNotNull(history)
            assertEquals(3, history.size) // 2 MATCH + 1 TOURNAMENT_BONUS
            assertEquals("TOURNAMENT_BONUS", history.first().reason)
            assertEquals(2, history.count { it.reason == "MATCH" })
            assertTrue(
                history.zipWithNext().all { (newer, older) -> newer.createdAt >= older.createdAt },
                "history must be ordered newest first"
            )
        }

    @Test
    fun `walkover match produces no rating event`() = testApplicationWithClient { client ->
        val fixture = createRegisteredKnockout(playerCount = 3)
        val token = createAuthToken(fixture.ownerSubject, fixture.ownerEmail, "owner")

        val startResponse = client.post("/tournaments/${fixture.tournamentId}/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val phase = startResponse.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")
        val walkover = phase.matches.first { it.status == MatchStatus.WALKOVER }

        transaction {
            val walkoverEvents = RatingEventDAO.find { RatingEventsTable.matchId eq walkover.id }.toList()
            assertTrue(walkoverEvents.isEmpty(), "walkovers are not a played match and earn no rating")
            // Nothing else was scored, so the walkover produced the only completion.
            assertTrue(RatingEventDAO.all().empty())
        }
    }

    @Test
    fun `played matches do not change rating before tournament completion`() = testApplicationWithClient { client ->
        val fixture = createRegisteredKnockout(playerCount = 4)
        val token = createAuthToken(fixture.ownerSubject, fixture.ownerEmail, "owner")

        val phase = client.post("/tournaments/${fixture.tournamentId}/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")

        val firstMatch = phase.matches.first { it.status == MatchStatus.SCHEDULED }
        val scoreResponse = client.put("/matches/${firstMatch.id}/score") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(twoSetWin())
        }
        assertEquals(HttpStatusCode.OK, scoreResponse.status)

        transaction {
            assertTrue(RatingEventDAO.all().empty(), "ratings are awarded only when the tournament completes")
            fixture.playerIds.forEach { playerId ->
                val player = PlayerDAO[playerId]
                assertEquals(1000, player.rating)
                assertEquals(0, player.ratedMatches)
            }
        }
    }

    @Test
    fun `resetting a completed tournament reverts rating events`() = testApplicationWithClient { client ->
        val fixture = createRegisteredKnockout(playerCount = 4)
        val token = createAuthToken(fixture.ownerSubject, fixture.ownerEmail, "owner")

        val startResponse = client.post("/tournaments/${fixture.tournamentId}/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)

        scoreAllScheduled(client, token, fixture.tournamentId)

        transaction {
            assertTrue(RatingEventDAO.all().count() > 0)
        }

        val resetResponse = client.post("/tournaments/${fixture.tournamentId}/reset") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resetResponse.status)

        transaction {
            assertTrue(RatingEventDAO.all().empty())
            fixture.playerIds.forEach { playerId ->
                val player = PlayerDAO[playerId]
                assertEquals(1000, player.rating)
                assertEquals(0, player.ratedMatches)
                assertEquals(null, player.lastRatedAt)
            }
        }
    }

    @Test
    fun `registered player beating a guest earns a fixed guest win and keeps the provisional window`() =
        testApplicationWithClient { client ->
            val fixture = createGuestTournament()
            val token = createAuthToken(fixture.ownerSubject, fixture.ownerEmail, "owner")

            val phase = client.post("/tournaments/${fixture.tournamentId}/start") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")

            scoreGuestMatch(client, token, phase, fixture, registeredWins = true)

            transaction {
                val guestWins = RatingEventDAO.find {
                    (RatingEventsTable.playerId eq fixture.registeredPlayerId) and
                        (RatingEventsTable.reason eq "GUEST_WIN")
                }.toList()
                assertEquals(1, guestWins.size)
                assertEquals(10, guestWins.single().delta)
                assertEquals(1010, guestWins.single().ratingAfter)

                val player = PlayerDAO[fixture.registeredPlayerId]
                // Guest matches never consume the provisional-K window.
                assertEquals(0, player.ratedMatches)
            }

            val users = client.get("/users").body<ApiResponse<List<User>>>().data
            assertNotNull(users)
            val registeredUser = users.first { it.id == fixture.registeredUserId }
            assertEquals(0, registeredUser.ratedMatches)
            // 1010 from the guest win, plus a +10 completion bonus as the sole champion.
            assertEquals(1020, registeredUser.rating)
        }

    @Test
    fun `registered player losing to a guest drops the fixed guest loss`() = testApplicationWithClient { client ->
        val fixture = createGuestTournament()
        val token = createAuthToken(fixture.ownerSubject, fixture.ownerEmail, "owner")

        val phase = client.post("/tournaments/${fixture.tournamentId}/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")

        scoreGuestMatch(client, token, phase, fixture, registeredWins = false)

        transaction {
            val guestLosses = RatingEventDAO.find {
                (RatingEventsTable.playerId eq fixture.registeredPlayerId) and
                    (RatingEventsTable.reason eq "GUEST_LOSS")
            }.toList()
            assertEquals(1, guestLosses.size)
            assertEquals(-5, guestLosses.single().delta)
            assertEquals(995, guestLosses.single().ratingAfter)

            val player = PlayerDAO[fixture.registeredPlayerId]
            assertEquals(0, player.ratedMatches)
        }
    }

    @Test
    fun `identical rescore of a completed match creates no new rating event`() = testApplicationWithClient { client ->
        val fixture = createGuestTournament()
        val token = createAuthToken(fixture.ownerSubject, fixture.ownerEmail, "owner")

        val phase = client.post("/tournaments/${fixture.tournamentId}/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")

        val match = phase.matches.first { it.status == MatchStatus.SCHEDULED }
        val registeredIsPlayer1 = match.player1?.id == fixture.registeredPlayerId
        val body = if (registeredIsPlayer1) twoSetWin() else twoSetLoss()

        val first = client.put("/matches/${match.id}/score") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, first.status)
        val countAfterFirst = transaction { RatingEventDAO.all().count() }

        val second = client.put("/matches/${match.id}/score") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, second.status)
        val countAfterSecond = transaction { RatingEventDAO.all().count() }

        assertEquals(countAfterFirst, countAfterSecond)
    }

    private suspend fun scoreAllScheduled(client: HttpClient, token: String, tournamentId: Int) {
        while (true) {
            val scheduled = client.get("/tournaments/$tournamentId/matches")
                .body<ApiResponse<List<Match>>>()
                .data
                ?.filter { it.status == MatchStatus.SCHEDULED && it.player1 != null && it.player2 != null }
                .orEmpty()
            if (scheduled.isEmpty()) break
            scheduled.forEach { match ->
                val response = client.put("/matches/${match.id}/score") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(twoSetWin())
                }
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }
    }

    private suspend fun scoreGuestMatch(
        client: HttpClient,
        token: String,
        phase: TournamentPhase,
        fixture: GuestFixture,
        registeredWins: Boolean
    ) {
        val match = phase.matches.first { it.status == MatchStatus.SCHEDULED }
        val registeredIsPlayer1 = match.player1?.id == fixture.registeredPlayerId
        // twoSetWin makes player1 win; twoSetLoss makes player2 win.
        val body = if (registeredIsPlayer1 == registeredWins) twoSetWin() else twoSetLoss()
        val response = client.put("/matches/${match.id}/score") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun createRegisteredKnockout(playerCount: Int): RegisteredFixture = transaction {
        val owner = UserDAO.new {
            username = "rating-owner"
            email = "rating-owner@email.com"
            authProvider = "clerk"
            authSubject = "rating-owner-subject"
        }
        val club = ClubDAO.new {
            name = "rating-club"
            phoneNumber = "123456789"
            address = "court 1"
            user = owner
        }
        val tournament = TournamentDAO.new {
            name = "rating-tournament"
            description = null
            surface = null
            this.club = club
            startDate = Instant.now()
            endDate = Instant.now().plus(1, ChronoUnit.DAYS)
        }

        val players = (1..playerCount).map { index ->
            val playerUser = UserDAO.new {
                username = "rating-player-$index"
                email = "rating-player-$index@email.com"
                authProvider = "clerk"
                authSubject = "rating-player-$index-subject"
            }
            val player = PlayerDAO.new {
                name = "rating-player-$index"
                external = false
                user = playerUser
            }
            TournamentPlayerDAO.new {
                this.player = player
                this.tournament = tournament
                seed = null
            }
            player.id.value to playerUser.id.value
        }

        TournamentPhaseDAO.new {
            this.tournament = tournament
            phaseOrder = 1
            format = PhaseFormat.KNOCKOUT.name
            rounds = 1
            configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
        }

        RegisteredFixture(
            tournamentId = tournament.id.value,
            ownerSubject = "rating-owner-subject",
            ownerEmail = "rating-owner@email.com",
            playerIds = players.map { it.first },
            userIds = players.map { it.second }
        )
    }

    private fun createGuestTournament(): GuestFixture = transaction {
        val owner = UserDAO.new {
            username = "guest-owner"
            email = "guest-owner@email.com"
            authProvider = "clerk"
            authSubject = "guest-owner-subject"
        }
        val club = ClubDAO.new {
            name = "guest-club"
            phoneNumber = "123456789"
            address = "court 1"
            user = owner
        }
        val tournament = TournamentDAO.new {
            name = "guest-tournament"
            description = null
            surface = null
            this.club = club
            startDate = Instant.now()
            endDate = Instant.now().plus(1, ChronoUnit.DAYS)
        }

        val registeredUser = UserDAO.new {
            username = "guest-registered-user"
            email = "guest-registered@email.com"
            authProvider = "clerk"
            authSubject = "guest-registered-subject"
        }
        val registered = PlayerDAO.new {
            name = "guest-registered-player"
            external = false
            user = registeredUser
        }
        val guest = PlayerDAO.new {
            name = "guest-external-player"
            external = true
            user = null
        }
        TournamentPlayerDAO.new {
            this.player = registered
            this.tournament = tournament
            seed = 1
        }
        TournamentPlayerDAO.new {
            this.player = guest
            this.tournament = tournament
            seed = 2
        }

        TournamentPhaseDAO.new {
            this.tournament = tournament
            phaseOrder = 1
            format = PhaseFormat.KNOCKOUT.name
            rounds = 1
            configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
        }

        GuestFixture(
            tournamentId = tournament.id.value,
            ownerSubject = "guest-owner-subject",
            ownerEmail = "guest-owner@email.com",
            registeredPlayerId = registered.id.value,
            registeredUserId = registeredUser.id.value
        )
    }

    private fun twoSetWin() = UpdateMatchScoreRequest(
        score = TennisScore(sets = listOf(SetScore(6, 4, null), SetScore(6, 4, null)))
    )

    private fun twoSetLoss() = UpdateMatchScoreRequest(
        score = TennisScore(sets = listOf(SetScore(4, 6, null), SetScore(4, 6, null)))
    )

    private data class RegisteredFixture(
        val tournamentId: Int,
        val ownerSubject: String,
        val ownerEmail: String,
        val playerIds: List<Int>,
        val userIds: List<Int>
    )

    private data class GuestFixture(
        val tournamentId: Int,
        val ownerSubject: String,
        val ownerEmail: String,
        val registeredPlayerId: Int,
        val registeredUserId: Int
    )
}
