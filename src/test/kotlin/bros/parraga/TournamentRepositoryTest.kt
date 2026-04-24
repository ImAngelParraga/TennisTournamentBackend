package bros.parraga

import bros.parraga.db.schema.*
import bros.parraga.domain.Match
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.SeedingStrategy
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
import bros.parraga.services.repositories.tournament.dto.CreateTournamentRequest
import bros.parraga.services.repositories.tournament.dto.TournamentPlayerRequest
import bros.parraga.services.repositories.tournament.dto.UpdateTournamentRequest
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Instant as KotlinInstant
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll

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
    fun `should treat repeated start as idempotent and avoid duplicate matches`() = testApplicationWithClient { client ->
        createTestData(playerCount = 8)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val firstStart = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, firstStart.status)
        val firstPhase = firstStart.body<ApiResponse<TournamentPhase>>().data ?: error("missing first started phase")

        val secondStart = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, secondStart.status)
        val secondPhase = secondStart.body<ApiResponse<TournamentPhase>>().data ?: error("missing second started phase")

        assertEquals(firstPhase.id, secondPhase.id)
        assertEquals(
            firstPhase.matches.map { it.id }.toSet(),
            secondPhase.matches.map { it.id }.toSet()
        )

        val matches = client.get("/tournaments/1/matches")
            .body<ApiResponse<List<Match>>>()
            .data ?: error("missing tournament matches")
        assertEquals(firstPhase.matches.size, matches.size)
    }

    @Test
    fun `concurrent start requests should not create duplicate bracket matches`() = testApplicationWithClient { client ->
        createTestData(playerCount = 8)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val statuses = coroutineScope {
            (1..5).map {
                async {
                    client.post("/tournaments/1/start") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }.status
                }
            }.awaitAll()
        }
        statuses.forEach { status -> assertEquals(HttpStatusCode.OK, status) }

        val matches = client.get("/tournaments/1/matches")
            .body<ApiResponse<List<Match>>>()
            .data ?: error("missing tournament matches")
        assertEquals(7, matches.size)
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
    fun `should return conflict when adding players with duplicate seeds in same request`() = testApplicationWithClient { client ->
        createTestData(playerCount = 0)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val addPlayerResponse = client.post("/tournaments/1/players") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                AddPlayersRequest(
                    listOf(
                        TournamentPlayerRequest(name = "seeded-1", seed = 1),
                        TournamentPlayerRequest(name = "seeded-2", seed = 1)
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.Conflict, addPlayerResponse.status)
    }

    @Test
    fun `should return bad request when creating tournament with start date after end date`() = testApplicationWithClient { client ->
        createTestData(playerCount = 0, initialPhaseOrders = emptyList())
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val response = client.post("/tournaments") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateTournamentRequest(
                    name = "invalid-dates",
                    description = null,
                    surface = null,
                    clubId = 1,
                    startDate = KotlinInstant.parse("2026-03-20T10:00:00Z"),
                    endDate = KotlinInstant.parse("2026-03-19T10:00:00Z")
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ApiResponse<TournamentBasic>>()
        assertTrue(body.message!!.contains("startDate must be on or before endDate"))
    }

    @Test
    fun `should return bad request when updating tournament with start date after end date`() = testApplicationWithClient { client ->
        createTestData(playerCount = 0, initialPhaseOrders = emptyList())
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val response = client.put("/tournaments") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateTournamentRequest(
                    id = 1,
                    startDate = KotlinInstant.parse("2026-03-21T10:00:00Z"),
                    endDate = KotlinInstant.parse("2026-03-20T10:00:00Z")
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ApiResponse<TournamentBasic>>()
        assertTrue(body.message!!.contains("startDate must be on or before endDate"))
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
    fun `should create a group phase through the API`() = testApplicationWithClient { client ->
        createTestData(initialPhaseOrders = emptyList(), playerCount = 4)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val response = client.post("/tournaments/1/phases") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreatePhaseRequest(
                    phaseOrder = 1,
                    format = PhaseFormat.GROUP,
                    configuration = PhaseConfiguration.GroupConfig(
                        groupCount = 2,
                        teamsPerGroup = 2,
                        advancingPerGroup = 1
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<ApiResponse<TournamentPhase>>()
        assertEquals(PhaseFormat.GROUP, body.data?.format)
    }

    @Test
    fun `should return bad request when creating phase with entrant count that does not match group configuration`() =
        testApplicationWithClient { client ->
            createTestData(initialPhaseOrders = emptyList(), playerCount = 5)
            val token = createAuthToken("owner-subject", "owner@email.com", "owner")

            val response = client.post("/tournaments/1/phases") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    CreatePhaseRequest(
                        phaseOrder = 1,
                        format = PhaseFormat.GROUP,
                        configuration = PhaseConfiguration.GroupConfig(
                            groupCount = 2,
                            teamsPerGroup = 2,
                            advancingPerGroup = 1
                        )
                    )
                )
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.body<ApiResponse<TournamentPhase>>()
            assertTrue(body.message!!.contains("requires exactly 4 entrants"))
            assertTrue(body.message!!.contains("projected entrants are 5"))
        }

    @Test
    fun `should return bad request when knockout qualifiers do not fit projected entrants from previous phase`() =
        testApplicationWithClient { client ->
            createTestData(initialPhaseOrders = emptyList(), playerCount = 5)
            val token = createAuthToken("owner-subject", "owner@email.com", "owner")

            val swissResponse = client.post("/tournaments/1/phases") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    CreatePhaseRequest(
                        phaseOrder = 1,
                        format = PhaseFormat.SWISS,
                        configuration = PhaseConfiguration.SwissConfig(pointsPerWin = 1, advancingCount = 4)
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, swissResponse.status)

            val knockoutResponse = client.post("/tournaments/1/phases") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    CreatePhaseRequest(
                        phaseOrder = 2,
                        format = PhaseFormat.KNOCKOUT,
                        configuration = PhaseConfiguration.KnockoutConfig(
                            thirdPlacePlayoff = false,
                            qualifiers = 4
                        )
                    )
                )
            }

            assertEquals(HttpStatusCode.BadRequest, knockoutResponse.status)
            val body = knockoutResponse.body<ApiResponse<TournamentPhase>>()
            assertTrue(body.message!!.contains("qualifiers=4"))
            assertTrue(body.message!!.contains("allowed values are 1, 2"))
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
    fun `starting a swiss tournament should create only round one and generate the next round after results`() =
        testApplicationWithClient { client ->
            createTestData(
                playerCount = 5,
                phaseSpecs = listOf(
                    PhaseSpec(
                        order = 1,
                        format = PhaseFormat.SWISS,
                        configuration = PhaseConfiguration.SwissConfig(pointsPerWin = 1, advancingCount = null)
                    )
                )
            )
            val token = createAuthToken("owner-subject", "owner@email.com", "owner")

            val startResponse = client.post("/tournaments/1/start") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, startResponse.status)
            val phase = startResponse.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")

            assertTrue(phase.matches.all { it.round == 1 })
            assertEquals(1, phase.matches.count { it.status == MatchStatus.WALKOVER })

            phase.matches
                .filter { it.status == MatchStatus.SCHEDULED }
                .forEach { match ->
                    val scoreResponse = client.put("/matches/${match.id}/score") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(twoSetWin())
                    }
                    assertEquals(HttpStatusCode.OK, scoreResponse.status)
                }

            val allMatches = client.get("/tournaments/1/matches")
                .body<ApiResponse<List<Match>>>()
                .data ?: error("missing tournament matches")

            assertTrue(allMatches.any { it.round == 2 })

            transaction {
                val rankings = SwissRankingsTable
                    .selectAll()
                    .where {
                        (SwissRankingsTable.phaseId eq phase.id) and (SwissRankingsTable.round eq 1)
                    }
                    .count()
                assertEquals(5, rankings.toInt())
            }
        }

    @Test
    fun `swiss phase should use advancingCount when starting the next knockout phase`() = testApplicationWithClient { client ->
        createTestData(
            playerCount = 5,
            phaseSpecs = listOf(
                PhaseSpec(
                    order = 1,
                    format = PhaseFormat.SWISS,
                    configuration = PhaseConfiguration.SwissConfig(pointsPerWin = 1, advancingCount = 4)
                ),
                PhaseSpec(
                    order = 2,
                    format = PhaseFormat.KNOCKOUT,
                    configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
                )
            )
        )
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val swissPhaseId = startResponse.body<ApiResponse<TournamentPhase>>().data?.id ?: error("missing swiss phase")

        while (true) {
            val swissMatches = client.get("/tournaments/1/matches")
                .body<ApiResponse<List<Match>>>()
                .data
                ?.filter { it.phaseId == swissPhaseId && it.status == MatchStatus.SCHEDULED }
                .orEmpty()

            if (swissMatches.isEmpty()) break

            swissMatches.forEach { match ->
                val scoreResponse = client.put("/matches/${match.id}/score") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(twoSetWin())
                }
                assertEquals(HttpStatusCode.OK, scoreResponse.status)
            }
        }

        val allMatches = client.get("/tournaments/1/matches")
            .body<ApiResponse<List<Match>>>()
            .data ?: error("missing tournament matches")

        val knockoutMatches = allMatches.filter { it.phaseId != swissPhaseId }
        assertEquals(3, knockoutMatches.size)
    }

    @Test
    fun `swiss phase should advance all players by default when advancingCount is not provided`() = testApplicationWithClient { client ->
        createTestData(
            playerCount = 5,
            phaseSpecs = listOf(
                PhaseSpec(
                    order = 1,
                    format = PhaseFormat.SWISS,
                    configuration = PhaseConfiguration.SwissConfig(pointsPerWin = 1, advancingCount = null)
                ),
                PhaseSpec(
                    order = 2,
                    format = PhaseFormat.KNOCKOUT,
                    configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
                )
            )
        )
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val swissPhaseId = startResponse.body<ApiResponse<TournamentPhase>>().data?.id ?: error("missing swiss phase")

        while (true) {
            val swissMatches = client.get("/tournaments/1/matches")
                .body<ApiResponse<List<Match>>>()
                .data
                ?.filter { it.phaseId == swissPhaseId && it.status == MatchStatus.SCHEDULED }
                .orEmpty()

            if (swissMatches.isEmpty()) break

            swissMatches.forEach { match ->
                val scoreResponse = client.put("/matches/${match.id}/score") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(twoSetWin())
                }
                assertEquals(HttpStatusCode.OK, scoreResponse.status)
            }
        }

        val allMatches = client.get("/tournaments/1/matches")
            .body<ApiResponse<List<Match>>>()
            .data ?: error("missing tournament matches")

        val knockoutMatches = allMatches.filter { it.phaseId != swissPhaseId }
        assertEquals(7, knockoutMatches.size)
    }

    @Test
    fun `completing a group phase should advance winners into the next knockout phase`() = testApplicationWithClient { client ->
        createTestData(
            playerCount = 4,
            phaseSpecs = listOf(
                PhaseSpec(
                    order = 1,
                    format = PhaseFormat.GROUP,
                    configuration = PhaseConfiguration.GroupConfig(
                        groupCount = 2,
                        teamsPerGroup = 2,
                        advancingPerGroup = 1
                    )
                ),
                PhaseSpec(
                    order = 2,
                    format = PhaseFormat.KNOCKOUT,
                    configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
                )
            )
        )
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val phaseOne = startResponse.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")
        assertEquals(PhaseFormat.GROUP, phaseOne.format)
        assertTrue(phaseOne.matches.all { it.groupId != null })

        phaseOne.matches.forEach { match ->
            val scoreResponse = client.put("/matches/${match.id}/score") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(twoSetWin())
            }
            assertEquals(HttpStatusCode.OK, scoreResponse.status)
        }

        val allMatches = client.get("/tournaments/1/matches")
            .body<ApiResponse<List<Match>>>()
            .data ?: error("missing tournament matches")
        val knockoutMatches = allMatches.filter { it.phaseId != phaseOne.id }
        assertEquals(1, knockoutMatches.size)
        assertTrue(knockoutMatches.single().player1 != null)
        assertTrue(knockoutMatches.single().player2 != null)

        transaction {
            val standings = GroupStandingDAO.all().toList()
            assertEquals(4, standings.size)
        }
    }

    @Test
    fun `should return bad request when adding players would invalidate existing phase configuration`() =
        testApplicationWithClient { client ->
            createTestData(
                playerCount = 4,
                phaseSpecs = listOf(
                    PhaseSpec(
                        order = 1,
                        format = PhaseFormat.GROUP,
                        configuration = PhaseConfiguration.GroupConfig(
                            groupCount = 2,
                            teamsPerGroup = 2,
                            advancingPerGroup = 1
                        )
                    )
                )
            )
            val token = createAuthToken("owner-subject", "owner@email.com", "owner")

            val response = client.post("/tournaments/1/players") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(AddPlayersRequest(listOf(TournamentPlayerRequest(name = "extra-player"))))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.body<ApiResponse<String>>()
            assertTrue(body.message!!.contains("requires exactly 4 entrants"))

            val players = client.get("/tournaments/1/players").body<ApiResponse<List<bros.parraga.domain.Player>>>()
            assertEquals(4, players.data?.size)
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

        transaction {
            val championId = TournamentDAO[1].champion?.id?.value
            assertEquals(scoreBody.data?.winnerId, championId)
        }
    }

    @Test
    fun `group-only tournament should persist a single winner when top points are tied`() = testApplicationWithClient { client ->
        createTestData(
            playerCount = 4,
            phaseSpecs = listOf(
                PhaseSpec(
                    order = 1,
                    format = PhaseFormat.GROUP,
                    configuration = PhaseConfiguration.GroupConfig(
                        groupCount = 1,
                        teamsPerGroup = 4,
                        advancingPerGroup = 1
                    )
                )
            )
        )
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val phase = startResponse.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")

        val desiredWinnersByPair = mapOf(
            setOf(1, 2) to 2,
            setOf(1, 3) to 1,
            setOf(1, 4) to 1,
            setOf(2, 3) to 2,
            setOf(2, 4) to 4,
            setOf(3, 4) to 3
        )

        phase.matches.forEach { match ->
            val player1Id = match.player1?.id ?: error("missing player1")
            val player2Id = match.player2?.id ?: error("missing player2")
            val desiredWinnerId = desiredWinnersByPair.getValue(setOf(player1Id, player2Id))
            val request = if (player1Id == desiredWinnerId) twoSetWin() else twoSetLoss()
            val scoreResponse = client.put("/matches/${match.id}/score") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            assertEquals(HttpStatusCode.OK, scoreResponse.status)
        }

        val tournamentResponse = client.get("/tournaments/1")
        val tournamentBody = tournamentResponse.body<ApiResponse<TournamentBasic>>()
        assertEquals(TournamentStatus.COMPLETED, tournamentBody.data?.status)

        transaction {
            val championId = TournamentDAO[1].champion?.id?.value
            assertEquals(1, championId)
        }
    }

    @Test
    fun `swiss-only tournament should persist a single winner from top points`() = testApplicationWithClient { client ->
        createTestData(
            playerCount = 5,
            phaseSpecs = listOf(
                PhaseSpec(
                    order = 1,
                    format = PhaseFormat.SWISS,
                    configuration = PhaseConfiguration.SwissConfig(pointsPerWin = 1)
                )
            )
        )
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val swissPhaseId = startResponse.body<ApiResponse<TournamentPhase>>().data?.id ?: error("missing swiss phase")

        while (true) {
            val swissMatches = client.get("/tournaments/1/matches")
                .body<ApiResponse<List<Match>>>()
                .data
                ?.filter { it.phaseId == swissPhaseId && it.status == MatchStatus.SCHEDULED }
                .orEmpty()

            if (swissMatches.isEmpty()) break

            swissMatches.forEach { match ->
                val scoreResponse = client.put("/matches/${match.id}/score") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(twoSetWin())
                }
                assertEquals(HttpStatusCode.OK, scoreResponse.status)
            }
        }

        val tournamentResponse = client.get("/tournaments/1")
        val tournamentBody = tournamentResponse.body<ApiResponse<TournamentBasic>>()
        assertEquals(TournamentStatus.COMPLETED, tournamentBody.data?.status)

        transaction {
            val expectedChampionId = SwissRankingsTable.selectAll()
                .where { (SwissRankingsTable.phaseId eq swissPhaseId) and (SwissRankingsTable.round eq 3) }
                .let { rows ->
                    val allRows = rows.toList()
                    val maxPoints = allRows.maxOf { it[SwissRankingsTable.points] }
                    allRows.filter { it[SwissRankingsTable.points] == maxPoints }
                        .map { it[SwissRankingsTable.playerId].value }
                        .min()
                }
            val championId = TournamentDAO[1].champion?.id?.value

            assertEquals(expectedChampionId, championId)
        }
    }

    @Test
    fun `should return conflict when scoring an already completed match`() = testApplicationWithClient { client ->
        createTestData(playerCount = 2)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val phase = startResponse.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")
        val finalMatchId = phase.matches.first().id

        val firstScoreResponse = client.put("/matches/$finalMatchId/score") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateMatchScoreRequest(
                    score = TennisScore(
                        sets = listOf(
                            SetScore(6, 4, null),
                            SetScore(6, 3, null)
                        )
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.OK, firstScoreResponse.status)

        val secondScoreResponse = client.put("/matches/$finalMatchId/score") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateMatchScoreRequest(
                    score = TennisScore(
                        sets = listOf(
                            SetScore(6, 0, null),
                            SetScore(6, 0, null)
                        )
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Conflict, secondScoreResponse.status)
    }

    @Test
    fun `should allow idempotent replay when scoring completed match with same payload`() = testApplicationWithClient { client ->
        createTestData(playerCount = 2)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val phase = startResponse.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")
        val finalMatchId = phase.matches.first().id
        val request = UpdateMatchScoreRequest(
            score = TennisScore(
                sets = listOf(
                    SetScore(6, 4, null),
                    SetScore(6, 3, null)
                )
            )
        )

        val firstScoreResponse = client.put("/matches/$finalMatchId/score") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        assertEquals(HttpStatusCode.OK, firstScoreResponse.status)
        val firstCompletedAt = firstScoreResponse.body<ApiResponse<Match>>().data?.completedAt
        assertTrue(firstCompletedAt != null)

        val secondScoreResponse = client.put("/matches/$finalMatchId/score") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        assertEquals(HttpStatusCode.OK, secondScoreResponse.status)
        val secondScoreBody = secondScoreResponse.body<ApiResponse<Match>>()
        assertEquals(MatchStatus.COMPLETED, secondScoreBody.data?.status)
        assertEquals(request.score, secondScoreBody.data?.score)
        assertEquals(firstCompletedAt, secondScoreBody.data?.completedAt)
    }

    @Test
    fun `should return conflict when scoring a match without both players assigned`() = testApplicationWithClient { client ->
        createTestData(playerCount = 4)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val phase = startResponse.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")
        val pendingMatch = phase.matches.first {
            it.round > 1 && (it.player1 == null || it.player2 == null)
        }

        val scoreResponse = client.put("/matches/${pendingMatch.id}/score") {
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

        assertEquals(HttpStatusCode.Conflict, scoreResponse.status)
    }

    @Test
    fun `should return conflict when scoring a walkover match`() = testApplicationWithClient { client ->
        createTestData(playerCount = 5)
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)
        val phase = startResponse.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")
        val walkoverMatch = phase.matches.first { it.status == MatchStatus.WALKOVER }
        assertTrue(walkoverMatch.completedAt != null)

        val scoreResponse = client.put("/matches/${walkoverMatch.id}/score") {
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

        assertEquals(HttpStatusCode.Conflict, scoreResponse.status)
    }

    @Test
    fun `should return bad request when score payload has no sets`() = testApplicationWithClient { client ->
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
                    score = TennisScore(sets = emptyList())
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, scoreResponse.status)
    }

    @Test
    fun `partial seeded strategy should avoid seeded players facing each other in round 1`() = testApplicationWithClient { client ->
        createTestData(
            playerCount = 8,
            seedingStrategy = SeedingStrategy.PARTIAL_SEEDED,
            seededPlayerIndices = mapOf(
                0 to 1,
                1 to 2
            )
        )
        val token = createAuthToken("owner-subject", "owner@email.com", "owner")

        val startResponse = client.post("/tournaments/1/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, startResponse.status)

        val phase = startResponse.body<ApiResponse<TournamentPhase>>().data ?: error("missing started phase")
        val round1Matches = phase.matches.filter { it.round == 1 }

        val seededIds = transaction {
            TournamentPlayerDAO.find { TournamentPlayersTable.tournamentId eq 1 }
                .filter { it.seed == 1 || it.seed == 2 }
                .map { it.player.id.value }
                .toSet()
        }

        val seededMatches = round1Matches.filter { it.player1?.id in seededIds || it.player2?.id in seededIds }
        assertEquals(2, seededMatches.size)
        seededMatches.forEach { match ->
            val bothSeeded = match.player1?.id in seededIds && match.player2?.id in seededIds
            assertTrue(!bothSeeded)
        }
    }

    private fun createTestData(
        thirdPlacePlayoff: Boolean = false,
        initialPhaseOrders: List<Int> = listOf(1),
        playerCount: Int = 5,
        seedingStrategy: SeedingStrategy = SeedingStrategy.INPUT_ORDER,
        seededPlayerIndices: Map<Int, Int> = emptyMap(),
        phaseSpecs: List<PhaseSpec>? = null
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

            repeat(playerCount) { index ->
                val player = PlayerDAO.new {
                    name = "testPlayer$index"
                    external = true
                    this.user = null
                }

                TournamentPlayerDAO.new {
                    this.player = player
                    this.tournament = tournament
                    seed = seededPlayerIndices[index]
                }
            }

            val effectivePhaseSpecs = phaseSpecs ?: initialPhaseOrders.map { phaseOrder ->
                PhaseSpec(
                    order = phaseOrder,
                    format = PhaseFormat.KNOCKOUT,
                    configuration = PhaseConfiguration.KnockoutConfig(
                        thirdPlacePlayoff = thirdPlacePlayoff,
                        seedingStrategy = seedingStrategy
                    )
                )
            }

            effectivePhaseSpecs.forEach { phaseSpec ->
                TournamentPhaseDAO.new {
                    this.tournament = tournament
                    this.phaseOrder = phaseSpec.order
                    format = phaseSpec.format.name
                    rounds = 3
                    configuration = phaseSpec.configuration
                }
            }
        }
    }

    private fun twoSetWin() = UpdateMatchScoreRequest(
        score = TennisScore(
            sets = listOf(
                SetScore(6, 4, null),
                SetScore(6, 4, null)
            )
        )
    )

    private fun twoSetLoss() = UpdateMatchScoreRequest(
        score = TennisScore(
            sets = listOf(
                SetScore(4, 6, null),
                SetScore(4, 6, null)
            )
        )
    )

    private data class PhaseSpec(
        val order: Int,
        val format: PhaseFormat,
        val configuration: PhaseConfiguration
    )
}



