package bros.parraga.services.repositories.tournament

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.PlayersTable
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentJoinRequestDAO
import bros.parraga.db.schema.TournamentJoinRequestsTable
import bros.parraga.db.schema.TournamentPhaseDAO
import bros.parraga.db.schema.TournamentPlayerDAO
import bros.parraga.db.schema.TournamentPlayersTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.TournamentJoinRequestStatus
import bros.parraga.domain.TournamentStatus
import bros.parraga.errors.ConflictException
import bros.parraga.errors.ForbiddenException
import bros.parraga.services.repositories.tournament.dto.AcceptTournamentJoinRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentJoinRequest
import bros.parraga.services.repositories.tournament.dto.DecideTournamentJoinRequest
import io.ktor.server.plugins.NotFoundException
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.and
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val JOIN_REQUEST_NOTE_MAX_LENGTH = 500

class TournamentJoinRequestRepositoryImpl : TournamentJoinRequestRepository {
    override suspend fun createJoinRequest(
        tournamentId: Int,
        userId: Int,
        request: CreateTournamentJoinRequest
    ): CreateJoinRequestResult = dbQuery {
        val tournament = TournamentDAO[tournamentId]
        assertTournamentAcceptsJoinRequests(tournament)

        val user = UserDAO[userId]
        val player = findOrCreatePlayer(user, request.playerName)
        if (isTournamentPlayer(tournament.id.value, player.id.value)) {
            throw ConflictException("Player ${player.id.value} is already registered for tournament $tournamentId")
        }

        val playerNote = normalizeNote(request.note)
        val now = Instant.now()
        val existing = findRequest(tournament.id.value, player.id.value)
        if (existing == null) {
            val created = TournamentJoinRequestDAO.new {
                this.tournament = tournament
                this.player = player
                requester = user
                status = TournamentJoinRequestStatus.PENDING.name
                this.playerNote = playerNote
                requestedAt = now
            }
            return@dbQuery CreateJoinRequestResult(created.toDomain(), created = true)
        }

        when (TournamentJoinRequestStatus.valueOf(existing.status)) {
            TournamentJoinRequestStatus.PENDING ->
                throw ConflictException("Join request ${existing.id.value} is already pending")

            TournamentJoinRequestStatus.ACCEPTED ->
                throw ConflictException("Join request ${existing.id.value} has already been accepted")

            TournamentJoinRequestStatus.REJECTED -> {
                val resubmitAfter = existing.resubmitAfter
                if (resubmitAfter != null && now.isBefore(resubmitAfter)) {
                    throw ConflictException("Join request ${existing.id.value} cannot be resubmitted until $resubmitAfter")
                }
                existing.moveBackToPending(playerNote, now)
            }

            TournamentJoinRequestStatus.WITHDRAWN,
            TournamentJoinRequestStatus.EXPIRED -> existing.moveBackToPending(playerNote, now)
        }

        CreateJoinRequestResult(existing.toDomain(), created = false)
    }

    override suspend fun withdrawJoinRequest(tournamentId: Int, requestId: Int, userId: Int) = dbQuery {
        val request = requireRequest(tournamentId, requestId)
        if (request.requester.id.value != userId) {
            throw ForbiddenException("Join request $requestId does not belong to the authenticated user")
        }
        if (TournamentJoinRequestStatus.valueOf(request.status) != TournamentJoinRequestStatus.PENDING) {
            throw ConflictException("Only pending join requests can be withdrawn")
        }

        val now = Instant.now()
        request.status = TournamentJoinRequestStatus.WITHDRAWN.name
        request.withdrawnAt = now
        request.updatedAt = now
        request.toDomain()
    }

    override suspend fun getJoinRequestsForTournament(
        tournamentId: Int,
        status: TournamentJoinRequestStatus?
    ) = dbQuery {
        TournamentDAO[tournamentId]
        val requests = TournamentJoinRequestDAO.find {
            if (status == null) {
                TournamentJoinRequestsTable.tournamentId eq tournamentId
            } else {
                (TournamentJoinRequestsTable.tournamentId eq tournamentId) and
                    (TournamentJoinRequestsTable.status eq status.name)
            }
        }
        requests.sortedByDescending { it.requestedAt }.map { it.toDomain() }
    }

    override suspend fun getJoinRequestsForUser(userId: Int, status: TournamentJoinRequestStatus?) = dbQuery {
        UserDAO[userId]
        val requests = TournamentJoinRequestDAO.find {
            if (status == null) {
                TournamentJoinRequestsTable.requesterUserId eq userId
            } else {
                (TournamentJoinRequestsTable.requesterUserId eq userId) and
                    (TournamentJoinRequestsTable.status eq status.name)
            }
        }
        requests.sortedByDescending { it.requestedAt }.map { it.toDomain() }
    }

    override suspend fun acceptJoinRequest(
        tournamentId: Int,
        requestId: Int,
        managerUserId: Int,
        request: AcceptTournamentJoinRequest
    ) = dbQuery {
        val joinRequest = requireRequest(tournamentId, requestId)
        val tournament = joinRequest.tournament
        assertTournamentMutable(tournament, "accept join requests")
        if (TournamentJoinRequestStatus.valueOf(joinRequest.status) != TournamentJoinRequestStatus.PENDING) {
            throw ConflictException("Only pending join requests can be accepted")
        }
        if (isTournamentPlayer(tournamentId, joinRequest.player.id.value)) {
            throw ConflictException("Player ${joinRequest.player.id.value} is already registered for tournament $tournamentId")
        }
        request.seed?.let { assertSeedAvailable(tournamentId, it) }

        TournamentPlayerDAO.new {
            this.tournament = tournament
            player = joinRequest.player
            seed = request.seed
        }
        assertDraftTournamentPhaseConfigurationValid(tournament)

        val now = Instant.now()
        joinRequest.status = TournamentJoinRequestStatus.ACCEPTED.name
        joinRequest.managerNote = normalizeNote(request.note)
        joinRequest.decidedBy = UserDAO[managerUserId]
        joinRequest.decidedAt = now
        joinRequest.updatedAt = now
        joinRequest.toDomain()
    }

    override suspend fun rejectJoinRequest(
        tournamentId: Int,
        requestId: Int,
        managerUserId: Int,
        request: DecideTournamentJoinRequest
    ) = dbQuery {
        val joinRequest = requireRequest(tournamentId, requestId)
        if (TournamentJoinRequestStatus.valueOf(joinRequest.status) != TournamentJoinRequestStatus.PENDING) {
            throw ConflictException("Only pending join requests can be rejected")
        }

        val now = Instant.now()
        joinRequest.status = TournamentJoinRequestStatus.REJECTED.name
        joinRequest.managerNote = normalizeNote(request.note)
        joinRequest.decidedBy = UserDAO[managerUserId]
        joinRequest.decidedAt = now
        joinRequest.resubmitAfter = now.plus(7, ChronoUnit.DAYS)
        joinRequest.updatedAt = now
        joinRequest.toDomain()
    }

    override suspend fun allowResubmit(tournamentId: Int, requestId: Int, managerUserId: Int) = dbQuery {
        val joinRequest = requireRequest(tournamentId, requestId)
        if (TournamentJoinRequestStatus.valueOf(joinRequest.status) != TournamentJoinRequestStatus.REJECTED) {
            throw ConflictException("Only rejected join requests can be unlocked for resubmission")
        }

        val now = Instant.now()
        joinRequest.resubmitAfter = null
        joinRequest.resubmitUnlockedBy = UserDAO[managerUserId]
        joinRequest.resubmitUnlockedAt = now
        joinRequest.updatedAt = now
        joinRequest.toDomain()
    }

    private fun findOrCreatePlayer(user: UserDAO, requestedName: String?): PlayerDAO {
        PlayerDAO.find { PlayersTable.userId eq user.id }.firstOrNull()?.let { return it }
        val playerName = normalizePlayerName(requestedName) ?: user.username
        return PlayerDAO.new {
            name = playerName.take(255)
            external = false
            this.user = user
        }
    }

    private fun normalizePlayerName(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }

    private fun normalizeNote(value: String?): String? {
        val note = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        require(note.length <= JOIN_REQUEST_NOTE_MAX_LENGTH) {
            "Join request notes must be $JOIN_REQUEST_NOTE_MAX_LENGTH characters or fewer"
        }
        return note
    }

    private fun requireRequest(tournamentId: Int, requestId: Int): TournamentJoinRequestDAO {
        val request = TournamentJoinRequestDAO.findById(requestId)
            ?: throw EntityNotFoundException(DaoEntityID(requestId, TournamentJoinRequestsTable), TournamentJoinRequestDAO)
        if (request.tournament.id.value != tournamentId) {
            throw NotFoundException("Join request $requestId was not found for tournament $tournamentId")
        }
        return request
    }

    private fun findRequest(tournamentId: Int, playerId: Int): TournamentJoinRequestDAO? =
        TournamentJoinRequestDAO.find {
            (TournamentJoinRequestsTable.tournamentId eq tournamentId) and
                (TournamentJoinRequestsTable.playerId eq playerId)
        }.firstOrNull()

    private fun isTournamentPlayer(tournamentId: Int, playerId: Int): Boolean =
        TournamentPlayerDAO.find {
            (TournamentPlayersTable.tournamentId eq tournamentId) and
                (TournamentPlayersTable.playerId eq playerId)
        }.firstOrNull() != null

    private fun assertTournamentAcceptsJoinRequests(tournament: TournamentDAO) {
        if (TournamentStatus.valueOf(tournament.status) != TournamentStatus.DRAFT) {
            throw ConflictException("Tournament ${tournament.id.value} is ${tournament.status} and cannot accept join requests")
        }
    }

    private fun assertTournamentMutable(tournament: TournamentDAO, operation: String) {
        if (TournamentStatus.valueOf(tournament.status) != TournamentStatus.DRAFT) {
            throw ConflictException("Tournament ${tournament.id.value} is ${tournament.status} and cannot $operation")
        }
        if (tournament.phases.any { phase -> phase.matches.any() }) {
            throw ConflictException("Tournament ${tournament.id.value} has already started and cannot $operation")
        }
    }

    private fun assertSeedAvailable(tournamentId: Int, seed: Int) {
        val conflictingAssociation = TournamentPlayerDAO.find {
            (TournamentPlayersTable.tournamentId eq tournamentId) and (TournamentPlayersTable.seed eq seed)
        }.firstOrNull()

        if (conflictingAssociation != null) {
            throw ConflictException("Seed $seed is already assigned in tournament $tournamentId")
        }
    }

    private fun assertDraftTournamentPhaseConfigurationValid(tournament: TournamentDAO) {
        assertPhaseConfigurationValid(
            phases = tournament.phases.map { it.toPlannedPhaseDefinition() },
            initialEntrantCount = tournament.players.count().toInt()
        )
    }

    private fun assertPhaseConfigurationValid(phases: List<PlannedPhaseDefinition>, initialEntrantCount: Int) {
        var projectedEntrants = initialEntrantCount
        phases.sortedBy { it.phaseOrder }.forEach { phase ->
            projectedEntrants = validatePhaseAndComputeAdvancers(phase, projectedEntrants)
        }
    }

    private fun validatePhaseAndComputeAdvancers(phase: PlannedPhaseDefinition, projectedEntrants: Int): Int {
        require(projectedEntrants >= 2) {
            "Phase ${phase.phaseOrder} ${phase.format.name.lowercase()} requires at least 2 entrants but projected entrants are $projectedEntrants"
        }

        return when (phase.format) {
            PhaseFormat.KNOCKOUT -> {
                val config = phase.configuration as? PhaseConfiguration.KnockoutConfig
                    ?: throw IllegalArgumentException("Knockout configuration is required")
                val allowedQualifiers = allowedKnockoutQualifiers(projectedEntrants)
                require(config.qualifiers in allowedQualifiers) {
                    "Phase ${phase.phaseOrder} knockout qualifiers=${config.qualifiers} are invalid for projected entrants $projectedEntrants; allowed values are ${allowedQualifiers.joinToString()}"
                }
                config.qualifiers
            }

            PhaseFormat.GROUP -> {
                val config = phase.configuration as? PhaseConfiguration.GroupConfig
                    ?: throw IllegalArgumentException("Group configuration is required")
                val requiredEntrants = config.groupCount * config.teamsPerGroup
                require(projectedEntrants == requiredEntrants) {
                    "Phase ${phase.phaseOrder} group configuration requires exactly $requiredEntrants entrants but projected entrants are $projectedEntrants"
                }
                config.groupCount * config.advancingPerGroup
            }

            PhaseFormat.SWISS -> {
                val config = phase.configuration as? PhaseConfiguration.SwissConfig
                    ?: throw IllegalArgumentException("Swiss configuration is required")
                config.advancingCount ?: projectedEntrants
            }
        }
    }

    private fun allowedKnockoutQualifiers(projectedEntrants: Int): List<Int> {
        val qualifiers = mutableListOf<Int>()
        var value = 1
        while (value < projectedEntrants) {
            if (value > 0 && (value and (value - 1)) == 0) qualifiers += value
            value *= 2
        }
        return qualifiers
    }

    private fun TournamentPhaseDAO.toPlannedPhaseDefinition() = PlannedPhaseDefinition(
        phaseOrder = phaseOrder,
        format = PhaseFormat.valueOf(format),
        configuration = configuration
    )

    private fun TournamentJoinRequestDAO.moveBackToPending(playerNote: String?, now: Instant) {
        status = TournamentJoinRequestStatus.PENDING.name
        this.playerNote = playerNote
        managerNote = null
        decidedBy = null
        decidedAt = null
        withdrawnAt = null
        resubmitAfter = null
        resubmitUnlockedBy = null
        resubmitUnlockedAt = null
        requestedAt = now
        updatedAt = now
    }

    private data class PlannedPhaseDefinition(
        val phaseOrder: Int,
        val format: PhaseFormat,
        val configuration: PhaseConfiguration
    )
}
