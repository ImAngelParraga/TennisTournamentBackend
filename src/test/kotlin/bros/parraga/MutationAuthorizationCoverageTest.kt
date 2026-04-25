package bros.parraga

import bros.parraga.db.DatabaseTables
import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.RacketDAO
import bros.parraga.db.schema.RacketStringingDAO
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentPhaseDAO
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UserTrainingDAO
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.RacketVisibility
import bros.parraga.domain.SetScore
import bros.parraga.domain.TennisScore
import bros.parraga.domain.TrainingVisibility
import bros.parraga.domain.TournamentStatus
import bros.parraga.services.repositories.club.dto.CreateClubRequest
import bros.parraga.services.repositories.club.dto.UpdateClubRequest
import bros.parraga.services.repositories.match.dto.UpdateMatchScoreRequest
import bros.parraga.services.repositories.player.dto.CreatePlayerRequest
import bros.parraga.services.repositories.player.dto.UpdatePlayerRequest
import bros.parraga.services.repositories.racket.dto.CreateRacketRequest
import bros.parraga.services.repositories.racket.dto.CreateRacketStringingRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketStringingRequest
import bros.parraga.services.repositories.training.dto.CreateTrainingRequest
import bros.parraga.services.repositories.training.dto.UpdateTrainingRequest
import bros.parraga.services.repositories.tournament.dto.AddPlayersRequest
import bros.parraga.services.repositories.tournament.dto.CreatePhaseRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentRequest
import bros.parraga.services.repositories.tournament.dto.TournamentPlayerRequest
import bros.parraga.services.repositories.tournament.dto.UpdateTournamentRequest
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class MutationAuthorizationCoverageTest : BaseIntegrationTest() {
    override val tables = DatabaseTables.all

    @Test
    fun `should return 401 for every authenticated mutation route`() = testApplicationWithClient { client ->
        val fixture = seedAuthorizationFixture()

        assertStatuses(
            expected = HttpStatusCode.Unauthorized,
            requests = listOf(
                namedRequest("POST /clubs") {
                    client.post("/clubs") {
                        contentType(ContentType.Application.Json)
                        setBody(CreateClubRequest(name = "Unauthorized Club", phoneNumber = null, address = null))
                    }.status
                },
                namedRequest("PUT /clubs") {
                    client.put("/clubs") {
                        contentType(ContentType.Application.Json)
                        setBody(UpdateClubRequest(id = fixture.clubId, name = "Unauthorized Update"))
                    }.status
                },
                namedRequest("DELETE /clubs/{id}") {
                    client.delete("/clubs/${fixture.clubId}").status
                },
                namedRequest("POST /clubs/{id}/admins/{userId}") {
                    client.post("/clubs/${fixture.clubId}/admins/${fixture.candidateUserId}").status
                },
                namedRequest("DELETE /clubs/{id}/admins/{userId}") {
                    client.delete("/clubs/${fixture.clubId}/admins/${fixture.adminUserId}").status
                },
                namedRequest("POST /players") {
                    client.post("/players") {
                        contentType(ContentType.Application.Json)
                        setBody(CreatePlayerRequest(name = "Unauthorized Player"))
                    }.status
                },
                namedRequest("PUT /players") {
                    client.put("/players") {
                        contentType(ContentType.Application.Json)
                        setBody(UpdatePlayerRequest(id = fixture.ownerPlayerId, name = "Unauthorized Player Update"))
                    }.status
                },
                namedRequest("DELETE /players/{id}") {
                    client.delete("/players/${fixture.ownerPlayerId}").status
                },
                namedRequest("POST /tournaments") {
                    client.post("/tournaments") {
                        contentType(ContentType.Application.Json)
                        setBody(createTournamentRequest(fixture.clubId, "Unauthorized Tournament"))
                    }.status
                },
                namedRequest("PUT /tournaments") {
                    client.put("/tournaments") {
                        contentType(ContentType.Application.Json)
                        setBody(UpdateTournamentRequest(id = fixture.phaseManagedTournamentId, name = "Unauthorized Tournament Update"))
                    }.status
                },
                namedRequest("DELETE /tournaments/{id}") {
                    client.delete("/tournaments/${fixture.deletableTournamentId}").status
                },
                namedRequest("POST /tournaments/{id}/start") {
                    client.post("/tournaments/${fixture.startableTournamentId}/start").status
                },
                namedRequest("POST /tournaments/{id}/reset") {
                    client.post("/tournaments/${fixture.resettableTournamentId}/reset").status
                },
                namedRequest("POST /tournaments/{id}/phases") {
                    client.post("/tournaments/${fixture.phaseManagedTournamentId}/phases") {
                        contentType(ContentType.Application.Json)
                        setBody(createKnockoutPhaseRequest())
                    }.status
                },
                namedRequest("POST /tournaments/{id}/players") {
                    client.post("/tournaments/${fixture.playerManagedTournamentId}/players") {
                        contentType(ContentType.Application.Json)
                        setBody(AddPlayersRequest(players = listOf(TournamentPlayerRequest(name = "Unauthorized Entrant"))))
                    }.status
                },
                namedRequest("DELETE /tournaments/{id}/players/{playerId}") {
                    client.delete(
                        "/tournaments/${fixture.playerManagedTournamentId}/players/${fixture.playerManagedTournamentPlayerId}"
                    ).status
                },
                namedRequest("PUT /matches/{id}/score") {
                    client.put("/matches/${fixture.scorableMatchId}/score") {
                        contentType(ContentType.Application.Json)
                        setBody(createScoreRequest())
                    }.status
                },
                namedRequest("POST /users") {
                    client.post("/users").status
                },
                namedRequest("PUT /users") {
                    client.put("/users").status
                },
                namedRequest("DELETE /users/{id}") {
                    client.delete("/users/${fixture.ownerUserId}").status
                },
                namedRequest("POST /users/me/trainings") {
                    client.post("/users/me/trainings") {
                        contentType(ContentType.Application.Json)
                        setBody(createTrainingRequest())
                    }.status
                },
                namedRequest("PUT /users/me/trainings/{id}") {
                    client.put("/users/me/trainings/${fixture.trainingId}") {
                        contentType(ContentType.Application.Json)
                        setBody(UpdateTrainingRequest(notes = "Unauthorized training update"))
                    }.status
                },
                namedRequest("DELETE /users/me/trainings/{id}") {
                    client.delete("/users/me/trainings/${fixture.trainingId}").status
                },
                namedRequest("POST /users/me/rackets") {
                    client.post("/users/me/rackets") {
                        contentType(ContentType.Application.Json)
                        setBody(CreateRacketRequest(displayName = "Unauthorized Racket"))
                    }.status
                },
                namedRequest("PUT /users/me/rackets/{id}") {
                    client.put("/users/me/rackets/${fixture.racketId}") {
                        contentType(ContentType.Application.Json)
                        setBody(UpdateRacketRequest(displayName = "Unauthorized Racket Update"))
                    }.status
                },
                namedRequest("DELETE /users/me/rackets/{id}") {
                    client.delete("/users/me/rackets/${fixture.racketId}").status
                },
                namedRequest("POST /users/me/rackets/{id}/stringings") {
                    client.post("/users/me/rackets/${fixture.racketId}/stringings") {
                        contentType(ContentType.Application.Json)
                        setBody(createStringingRequest())
                    }.status
                },
                namedRequest("PUT /users/me/rackets/{id}/stringings/{stringingId}") {
                    client.put("/users/me/rackets/${fixture.racketId}/stringings/${fixture.stringingId}") {
                        contentType(ContentType.Application.Json)
                        setBody(UpdateRacketStringingRequest(performanceNotes = "Unauthorized update"))
                    }.status
                },
                namedRequest("DELETE /users/me/rackets/{id}/stringings/{stringingId}") {
                    client.delete("/users/me/rackets/${fixture.racketId}/stringings/${fixture.stringingId}").status
                }
            )
        )
    }

    @Test
    fun `should return 403 for outsiders on club tournament and match manager mutations`() = testApplicationWithClient { client ->
        val fixture = seedAuthorizationFixture()
        val outsiderToken = outsiderToken()

        assertStatuses(
            expected = HttpStatusCode.Forbidden,
            requests = listOf(
                namedRequest("PUT /clubs") {
                    client.put("/clubs") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                        contentType(ContentType.Application.Json)
                        setBody(UpdateClubRequest(id = fixture.clubId, name = "Outsider Club Update"))
                    }.status
                },
                namedRequest("DELETE /clubs/{id}") {
                    client.delete("/clubs/${fixture.clubId}") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                    }.status
                },
                namedRequest("POST /clubs/{id}/admins/{userId}") {
                    client.post("/clubs/${fixture.clubId}/admins/${fixture.candidateUserId}") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                    }.status
                },
                namedRequest("DELETE /clubs/{id}/admins/{userId}") {
                    client.delete("/clubs/${fixture.clubId}/admins/${fixture.adminUserId}") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                    }.status
                },
                namedRequest("POST /tournaments") {
                    client.post("/tournaments") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                        contentType(ContentType.Application.Json)
                        setBody(createTournamentRequest(fixture.clubId, "Outsider Tournament"))
                    }.status
                },
                namedRequest("PUT /tournaments") {
                    client.put("/tournaments") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                        contentType(ContentType.Application.Json)
                        setBody(UpdateTournamentRequest(id = fixture.phaseManagedTournamentId, name = "Outsider Tournament Update"))
                    }.status
                },
                namedRequest("DELETE /tournaments/{id}") {
                    client.delete("/tournaments/${fixture.deletableTournamentId}") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                    }.status
                },
                namedRequest("POST /tournaments/{id}/start") {
                    client.post("/tournaments/${fixture.startableTournamentId}/start") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                    }.status
                },
                namedRequest("POST /tournaments/{id}/reset") {
                    client.post("/tournaments/${fixture.resettableTournamentId}/reset") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                    }.status
                },
                namedRequest("POST /tournaments/{id}/phases") {
                    client.post("/tournaments/${fixture.phaseManagedTournamentId}/phases") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                        contentType(ContentType.Application.Json)
                        setBody(createKnockoutPhaseRequest())
                    }.status
                },
                namedRequest("POST /tournaments/{id}/players") {
                    client.post("/tournaments/${fixture.playerManagedTournamentId}/players") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                        contentType(ContentType.Application.Json)
                        setBody(AddPlayersRequest(players = listOf(TournamentPlayerRequest(name = "Blocked Entrant"))))
                    }.status
                },
                namedRequest("DELETE /tournaments/{id}/players/{playerId}") {
                    client.delete(
                        "/tournaments/${fixture.playerManagedTournamentId}/players/${fixture.playerManagedTournamentPlayerId}"
                    ) {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                    }.status
                },
                namedRequest("PUT /matches/{id}/score") {
                    client.put("/matches/${fixture.scorableMatchId}/score") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                        contentType(ContentType.Application.Json)
                        setBody(createScoreRequest())
                    }.status
                }
            )
        )
    }

    @Test
    fun `club admins should be able to manage club tournament and match mutations`() = testApplicationWithClient { client ->
        val fixture = seedAuthorizationFixture()
        val adminToken = adminToken()

        val updateClubResponse = client.put("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateClubRequest(id = fixture.clubId, name = "Admin Updated Club"))
        }
        assertEquals(HttpStatusCode.OK, updateClubResponse.status)

        val addAdminResponse = client.post("/clubs/${fixture.clubId}/admins/${fixture.candidateUserId}") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, addAdminResponse.status)

        val removeAdminResponse = client.delete("/clubs/${fixture.clubId}/admins/${fixture.candidateUserId}") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, removeAdminResponse.status)

        val createTournamentResponse = client.post("/tournaments") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody(createTournamentRequest(fixture.clubId, "Admin Tournament"))
        }
        assertEquals(HttpStatusCode.Created, createTournamentResponse.status)

        val updateTournamentResponse = client.put("/tournaments") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateTournamentRequest(id = fixture.phaseManagedTournamentId, name = "Admin Tournament Update"))
        }
        assertEquals(HttpStatusCode.OK, updateTournamentResponse.status)

        val createPhaseResponse = client.post("/tournaments/${fixture.phaseManagedTournamentId}/phases") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody(createKnockoutPhaseRequest())
        }
        assertEquals(HttpStatusCode.Created, createPhaseResponse.status)

        val addPlayersResponse = client.post("/tournaments/${fixture.playerManagedTournamentId}/players") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody(AddPlayersRequest(players = listOf(TournamentPlayerRequest(name = "Admin Added Entrant"))))
        }
        assertEquals(HttpStatusCode.OK, addPlayersResponse.status)

        val removePlayerResponse = client.delete(
            "/tournaments/${fixture.playerManagedTournamentId}/players/${fixture.playerManagedTournamentPlayerId}"
        ) {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, removePlayerResponse.status)

        val deleteTournamentResponse = client.delete("/tournaments/${fixture.deletableTournamentId}") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.NoContent, deleteTournamentResponse.status)

        val startTournamentResponse = client.post("/tournaments/${fixture.startableTournamentId}/start") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, startTournamentResponse.status)

        val resetTournamentResponse = client.post("/tournaments/${fixture.resettableTournamentId}/reset") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resetTournamentResponse.status)

        val updateScoreResponse = client.put("/matches/${fixture.scorableMatchId}/score") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody(createScoreRequest())
        }
        assertEquals(HttpStatusCode.OK, updateScoreResponse.status)
    }

    @Test
    fun `should reject attempts to manage the owner via club admins endpoints`() = testApplicationWithClient { client ->
        val fixture = seedAuthorizationFixture()
        val ownerToken = ownerToken()

        val addOwnerAsAdminResponse = client.post("/clubs/${fixture.clubId}/admins/${fixture.ownerUserId}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.Forbidden, addOwnerAsAdminResponse.status)

        val removeOwnerAsAdminResponse = client.delete("/clubs/${fixture.clubId}/admins/${fixture.ownerUserId}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.Forbidden, removeOwnerAsAdminResponse.status)
    }

    @Test
    fun `should reject tournament updates that move into an unmanaged club`() = testApplicationWithClient { client ->
        val fixture = seedAuthorizationFixture()
        val ownerToken = ownerToken()

        val response = client.put("/tournaments") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateTournamentRequest(
                    id = fixture.phaseManagedTournamentId,
                    clubId = fixture.secondClubId
                )
            )
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `should reject player and racket mutations outside the authenticated owners boundary`() = testApplicationWithClient { client ->
        val fixture = seedAuthorizationFixture()
        val outsiderToken = outsiderToken()

        assertStatuses(
            expected = HttpStatusCode.Forbidden,
            requests = listOf(
                namedRequest("PUT /players for another user") {
                    client.put("/players") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                        contentType(ContentType.Application.Json)
                        setBody(UpdatePlayerRequest(id = fixture.ownerPlayerId, name = "Hijack"))
                    }.status
                },
                namedRequest("DELETE /players for another user") {
                    client.delete("/players/${fixture.ownerPlayerId}") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                    }.status
                },
                namedRequest("PUT /users/me/trainings/{id}") {
                    client.put("/users/me/trainings/${fixture.trainingId}") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                        contentType(ContentType.Application.Json)
                        setBody(UpdateTrainingRequest(notes = "Hijacked training"))
                    }.status
                },
                namedRequest("DELETE /users/me/trainings/{id}") {
                    client.delete("/users/me/trainings/${fixture.trainingId}") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                    }.status
                },
                namedRequest("PUT /users/me/rackets/{id}") {
                    client.put("/users/me/rackets/${fixture.racketId}") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                        contentType(ContentType.Application.Json)
                        setBody(UpdateRacketRequest(displayName = "Hijacked Racket"))
                    }.status
                },
                namedRequest("DELETE /users/me/rackets/{id}") {
                    client.delete("/users/me/rackets/${fixture.racketId}") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                    }.status
                },
                namedRequest("POST /users/me/rackets/{id}/stringings") {
                    client.post("/users/me/rackets/${fixture.racketId}/stringings") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                        contentType(ContentType.Application.Json)
                        setBody(createStringingRequest())
                    }.status
                },
                namedRequest("PUT /users/me/rackets/{id}/stringings/{stringingId}") {
                    client.put("/users/me/rackets/${fixture.racketId}/stringings/${fixture.stringingId}") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                        contentType(ContentType.Application.Json)
                        setBody(UpdateRacketStringingRequest(performanceNotes = "Hijacked Stringing"))
                    }.status
                },
                namedRequest("DELETE /users/me/rackets/{id}/stringings/{stringingId}") {
                    client.delete("/users/me/rackets/${fixture.racketId}/stringings/${fixture.stringingId}") {
                        header(HttpHeaders.Authorization, "Bearer $outsiderToken")
                    }.status
                }
            )
        )
    }

    private suspend fun assertStatuses(
        expected: HttpStatusCode,
        requests: List<Pair<String, suspend () -> HttpStatusCode>>
    ) {
        requests.forEach { (label, request) ->
            assertEquals(expected, request(), label)
        }
    }

    private fun namedRequest(
        label: String,
        request: suspend () -> HttpStatusCode
    ): Pair<String, suspend () -> HttpStatusCode> = label to request

    private fun createTournamentRequest(clubId: Int, name: String) = CreateTournamentRequest(
        name = name,
        description = null,
        surface = null,
        clubId = clubId,
        startDate = kotlinx.datetime.Instant.parse("2026-01-01T00:00:00Z"),
        endDate = kotlinx.datetime.Instant.parse("2026-01-02T00:00:00Z")
    )

    private fun createKnockoutPhaseRequest() = CreatePhaseRequest(
        phaseOrder = 1,
        format = PhaseFormat.KNOCKOUT,
        configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
    )

    private fun createScoreRequest() = UpdateMatchScoreRequest(
        score = TennisScore(sets = listOf(SetScore(player1Games = 6, player2Games = 4, tiebreak = null)))
    )

    private fun createStringingRequest() = CreateRacketStringingRequest(
        stringingDate = "2026-01-10",
        mainsTensionKg = 24.0,
        crossesTensionKg = 23.0,
        mainStringType = "Poly",
        crossStringType = "Poly",
        performanceNotes = null
    )

    private fun createTrainingRequest() = CreateTrainingRequest(
        trainingDate = "2026-01-11",
        durationMinutes = 60,
        notes = "Unauthorized training",
        visibility = TrainingVisibility.PRIVATE
    )

    private fun ownerToken() = createAuthToken("owner-subject", "owner@email.com", "Owner")

    private fun adminToken() = createAuthToken("admin-subject", "admin@email.com", "Admin")

    private fun outsiderToken() = createAuthToken("outsider-subject", "outsider@email.com", "Outsider")

    private fun seedAuthorizationFixture(): AuthorizationFixture = transaction {
        val owner = createUser(username = "owner", email = "owner@email.com", authSubject = "owner-subject")
        val admin = createUser(username = "admin", email = "admin@email.com", authSubject = "admin-subject")
        createUser(username = "outsider", email = "outsider@email.com", authSubject = "outsider-subject")
        val secondOwner = createUser(username = "owner2", email = "owner2@email.com", authSubject = "owner2-subject")
        val candidate = createUser(username = "candidate", email = "candidate@email.com", authSubject = "candidate-subject")

        val ownerPlayer = PlayerDAO.new {
            name = "Owner Player"
            external = false
            user = owner
        }

        val club = ClubDAO.new {
            name = "Managed Club"
            phoneNumber = null
            address = null
            user = owner
        }
        club.admins = SizedCollection(listOf(admin))

        val secondClub = ClubDAO.new {
            name = "Other Club"
            phoneNumber = null
            address = null
            user = secondOwner
        }

        val phaseManagedTournament = createTournament(club, TournamentStatus.DRAFT)
        phaseManagedTournament.players = SizedCollection(
            listOf(
                createExternalPlayer("Phase Player 1"),
                createExternalPlayer("Phase Player 2"),
                createExternalPlayer("Phase Player 3"),
                createExternalPlayer("Phase Player 4")
            )
        )

        val playerManagedTournamentPlayer = createExternalPlayer("Managed Tournament Player")
        val playerManagedTournament = createTournament(club, TournamentStatus.DRAFT)
        playerManagedTournament.players = SizedCollection(listOf(playerManagedTournamentPlayer))

        val deletableTournament = createTournament(club, TournamentStatus.DRAFT)

        val startableTournament = createTournament(club, TournamentStatus.DRAFT)
        startableTournament.players = SizedCollection(
            listOf(
                createExternalPlayer("Start Player 1"),
                createExternalPlayer("Start Player 2"),
                createExternalPlayer("Start Player 3"),
                createExternalPlayer("Start Player 4")
            )
        )
        TournamentPhaseDAO.new {
            tournament = startableTournament
            phaseOrder = 1
            format = PhaseFormat.KNOCKOUT.name
            rounds = 1
            configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
        }

        val resettableTournament = createTournament(club, TournamentStatus.STARTED)
        TournamentPhaseDAO.new {
            tournament = resettableTournament
            phaseOrder = 1
            format = PhaseFormat.KNOCKOUT.name
            rounds = 1
            configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
        }

        val scorableTournament = createTournament(club, TournamentStatus.STARTED)
        val matchPlayer1 = createExternalPlayer("Match Player 1")
        val matchPlayer2 = createExternalPlayer("Match Player 2")
        scorableTournament.players = SizedCollection(listOf(matchPlayer1, matchPlayer2))
        val scorablePhase = TournamentPhaseDAO.new {
            tournament = scorableTournament
            phaseOrder = 1
            format = PhaseFormat.KNOCKOUT.name
            rounds = 1
            configuration = PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff = false)
        }
        val scorableMatch = MatchDAO.new {
            phase = scorablePhase
            round = 1
            roundSlot = 1
            player1 = matchPlayer1
            player2 = matchPlayer2
            status = MatchStatus.SCHEDULED.name
        }

        val racket = RacketDAO.new {
            ownerUser = owner
            displayName = "Owner Racket"
            brand = "Yonex"
            model = "Ezone"
            stringPattern = "16x19"
            visibility = RacketVisibility.PRIVATE.name
        }
        val stringing = RacketStringingDAO.new {
            this.racket = racket
            stringingDate = LocalDate.parse("2026-01-05")
            mainsTensionKg = BigDecimal("25.00")
            crossesTensionKg = BigDecimal("24.00")
            mainStringType = "Poly"
            crossStringType = "Poly"
            performanceNotes = "Fresh"
        }
        val training = UserTrainingDAO.new {
            ownerUser = owner
            trainingDate = LocalDate.parse("2026-01-07")
            notes = "Owner training"
        }

        AuthorizationFixture(
            ownerUserId = owner.id.value,
            adminUserId = admin.id.value,
            candidateUserId = candidate.id.value,
            clubId = club.id.value,
            secondClubId = secondClub.id.value,
            ownerPlayerId = ownerPlayer.id.value,
            phaseManagedTournamentId = phaseManagedTournament.id.value,
            playerManagedTournamentId = playerManagedTournament.id.value,
            playerManagedTournamentPlayerId = playerManagedTournamentPlayer.id.value,
            deletableTournamentId = deletableTournament.id.value,
            startableTournamentId = startableTournament.id.value,
            resettableTournamentId = resettableTournament.id.value,
            scorableMatchId = scorableMatch.id.value,
            trainingId = training.id.value,
            racketId = racket.id.value,
            stringingId = stringing.id.value
        )
    }

    private fun createUser(username: String, email: String, authSubject: String): UserDAO = UserDAO.new {
        this.username = username
        this.email = email
        authProvider = "clerk"
        this.authSubject = authSubject
    }

    private fun createExternalPlayer(name: String): PlayerDAO = PlayerDAO.new {
        this.name = name
        external = true
        user = null
    }

    private fun createTournament(club: ClubDAO, status: TournamentStatus): TournamentDAO {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        return TournamentDAO.new {
            name = "${status.name.lowercase()} tournament"
            description = null
            surface = null
            this.status = status.name
            this.club = club
            startDate = start
            endDate = start.plus(1, ChronoUnit.DAYS)
        }
    }

    private data class AuthorizationFixture(
        val ownerUserId: Int,
        val adminUserId: Int,
        val candidateUserId: Int,
        val clubId: Int,
        val secondClubId: Int,
        val ownerPlayerId: Int,
        val phaseManagedTournamentId: Int,
        val playerManagedTournamentId: Int,
        val playerManagedTournamentPlayerId: Int,
        val deletableTournamentId: Int,
        val startableTournamentId: Int,
        val resettableTournamentId: Int,
        val scorableMatchId: Int,
        val trainingId: Int,
        val racketId: Int,
        val stringingId: Int
    )
}
