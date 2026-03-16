package bros.parraga.services.repositories.tournament

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.lockPhaseRow
import bros.parraga.db.lockTournamentRow
import bros.parraga.db.schema.*
import bros.parraga.domain.*
import bros.parraga.errors.ConflictException
import bros.parraga.services.PhaseExecutionService
import bros.parraga.services.TournamentProgressionService
import bros.parraga.services.repositories.tournament.dto.*
import io.ktor.server.plugins.*
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import parraga.bros.tournament.domain.Format
import parraga.bros.tournament.domain.Phase
import parraga.bros.tournament.services.TournamentService
import java.time.Instant
import parraga.bros.tournament.domain.SeededParticipant as LibSeededParticipant

class TournamentRepositoryImpl : TournamentRepository {
    override suspend fun getTournaments(): List<TournamentBasic> = dbQuery { TournamentDAO.all().map { it.toBasic() } }

    override suspend fun getTournament(id: Int): TournamentBasic = dbQuery { TournamentDAO[id].toBasic() }

    override suspend fun createTournament(request: CreateTournamentRequest): TournamentBasic = dbQuery {
        val requestedStartDate = request.startDate.toJavaInstant()
        val requestedEndDate = request.endDate.toJavaInstant()
        assertTournamentDatesValid(requestedStartDate, requestedEndDate)

        TournamentDAO.new {
            name = request.name
            description = request.description
            surface = request.surface
            status = TournamentStatus.DRAFT.name
            club = ClubDAO[request.clubId]
            startDate = requestedStartDate
            endDate = requestedEndDate
        }.toBasic()
    }

    override suspend fun updateTournament(request: UpdateTournamentRequest): TournamentBasic = dbQuery {
        val tournament = TournamentDAO.findById(request.id)
            ?: throw EntityNotFoundException(DaoEntityID(request.id, TournamentsTable), TournamentDAO)
        val status = TournamentStatus.valueOf(tournament.status)
        assertUpdateAllowedForStatus(status, request)
        if (status == TournamentStatus.DRAFT) {
            val requestedStartDate = request.startDate?.toJavaInstant() ?: tournament.startDate
            val requestedEndDate = request.endDate?.toJavaInstant() ?: tournament.endDate
            assertTournamentDatesValid(requestedStartDate, requestedEndDate)
        }

        tournament.apply {
            request.name?.let { name = it }
            request.description?.let { description = it }
            request.surface?.let { surface = it }
            if (status == TournamentStatus.DRAFT) {
                request.clubId?.let { club = ClubDAO[it] }
                request.startDate?.let { startDate = it.toJavaInstant() }
                request.endDate?.let { endDate = it.toJavaInstant() }
            }
            updatedAt = Instant.now()
        }
        tournament.toBasic()
    }

    override suspend fun deleteTournament(id: Int) = dbQuery {
        val tournament = TournamentDAO[id]
        assertTournamentMutable(tournament, "deleted")
        tournament.delete()
    }

    override suspend fun createPhase(tournamentId: Int, request: CreatePhaseRequest): TournamentPhase = dbQuery {
        require(request.phaseOrder > 0) { "phaseOrder must be greater than 0" }

        val tournament = TournamentDAO[tournamentId]
        assertTournamentMutable(tournament, "modified")
        if (tournament.phases.any { it.phaseOrder == request.phaseOrder }) {
            throw ConflictException("Phase order ${request.phaseOrder} already exists for tournament $tournamentId")
        }
        if (request.phaseOrder > 1 && tournament.phases.none { it.phaseOrder == request.phaseOrder - 1 }) {
            throw ConflictException(
                "Phase order ${request.phaseOrder} requires previous phase ${request.phaseOrder - 1} to exist"
            )
        }
        assertPhaseConfigurationValid(
            phases = tournament.phases.map { it.toPlannedPhaseDefinition() } + request.toPlannedPhaseDefinition(),
            initialEntrantCount = tournament.players.count().toInt()
        )
        val rounds = computeInitialRounds(request)

        TournamentPhaseDAO.new {
            this.tournament = tournament
            phaseOrder = request.phaseOrder
            format = request.format.name
            this.rounds = rounds
            configuration = request.configuration
        }.toDomain()
    }

    override suspend fun getTournamentPlayers(tournamentId: Int): List<Player> = dbQuery {
        val tournament = TournamentDAO[tournamentId]
        tournament.players.map { it.toDomain() }
    }

    override suspend fun getTournamentPhases(tournamentId: Int): List<TournamentPhaseSummary> = dbQuery {
        TournamentPhaseDAO.find { TournamentPhasesTable.tournamentId eq tournamentId }
            .sortedBy { it.phaseOrder }
            .map { it.toSummary() }
    }

    override suspend fun getTournamentMatches(tournamentId: Int): List<Match> = dbQuery {
        val phaseIds = TournamentPhaseDAO.find { TournamentPhasesTable.tournamentId eq tournamentId }
            .map { it.id }
        if (phaseIds.isEmpty()) return@dbQuery emptyList()

        MatchDAO.find { MatchesTable.phaseId inList phaseIds }
            .sortedBy { it.id.value }
            .map { it.toDomain() }
    }

    override suspend fun getTournamentBracket(tournamentId: Int): TournamentBracket = dbQuery {
        val tournament = TournamentDAO[tournamentId]
        val phases = TournamentPhaseDAO.find { TournamentPhasesTable.tournamentId eq tournamentId }
            .sortedBy { it.phaseOrder }
        if (phases.isEmpty()) {
            return@dbQuery TournamentBracket(tournament.id.value, emptyList())
        }

        val phaseIds = phases.map { it.id }
        val matches = MatchDAO.find { MatchesTable.phaseId inList phaseIds }
            .sortedBy { it.id.value }
            .toList()

        val matchesByPhase = matches.groupBy { it.phase.id.value }
        val phaseBrackets = phases.map { phase ->
            val phaseMatches = matchesByPhase[phase.id.value].orEmpty()
            val rounds = phaseMatches.groupBy { it.round }
                .toSortedMap()
                .map { (round, roundMatches) ->
                    TournamentBracketRound(round, roundMatches.map { it.toDomain() })
                }

            TournamentBracketPhase(
                id = phase.id.value,
                tournamentId = tournament.id.value,
                phaseOrder = phase.phaseOrder,
                format = PhaseFormat.valueOf(phase.format),
                rounds = rounds
            )
        }

        TournamentBracket(tournament.id.value, phaseBrackets)
    }

    override suspend fun addPlayersToTournament(
        tournamentId: Int,
        request: AddPlayersRequest
    ) = dbQuery {
        val tournament = TournamentDAO[tournamentId]
        assertTournamentMutable(tournament, "modified")
        assertNoDuplicateSeedInRequest(request)

        request.players.forEach { request ->
            val player = getOrCreatePlayer(request)
            request.seed?.let { seed ->
                assertSeedAvailable(tournamentId = tournament.id.value, seed = seed, excludePlayerId = player.id.value)
            }

            val association = TournamentPlayerDAO.find {
                (TournamentPlayersTable.tournamentId eq tournament.id.value) and
                    (TournamentPlayersTable.playerId eq player.id.value)
            }.firstOrNull()

            if (association == null) {
                TournamentPlayerDAO.new {
                    this.tournament = tournament
                    this.player = player
                    this.seed = request.seed
                }
            } else if (request.seed != null && association.seed != request.seed) {
                association.seed = request.seed
            }
        }

        assertDraftTournamentPhaseConfigurationValid(tournament)
    }

    override suspend fun removePlayerFromTournament(tournamentId: Int, playerId: Int) = dbQuery {
        val tournament = TournamentDAO[tournamentId]
        assertTournamentMutable(tournament, "modified")

        val association = TournamentPlayerDAO.find {
            TournamentPlayersTable.tournamentId.eq(tournamentId) and TournamentPlayersTable.playerId.eq(playerId)
        }.firstOrNull() ?: throw NotFoundException(
            "No association found between tournament $tournamentId and player $playerId"
        )

        association.delete()
        assertDraftTournamentPhaseConfigurationValid(tournament)
    }

    override suspend fun startTournament(id: Int): TournamentPhase = dbQuery {
        lockTournamentRow(id)
        val tournament = TournamentDAO[id]
        require(tournament.phases.count() > 0) { "Tournament has no phases" }

        val participants = TournamentPlayerDAO.find { TournamentPlayersTable.tournamentId eq id }
            .map { association -> LibSeededParticipant(association.player.id.value, association.seed) }
            .sortedWith(compareBy<LibSeededParticipant> { it.seed ?: Int.MAX_VALUE }.thenBy { it.playerId })
        require(participants.size >= 2) { "Tournament must have at least 2 players" }

        val status = TournamentStatus.valueOf(tournament.status)
        if (status != TournamentStatus.DRAFT && status != TournamentStatus.STARTED) {
            throw ConflictException("Tournament $id cannot be started from status ${tournament.status}")
        }
        assertPhaseConfigurationValid(
            phases = tournament.phases.map { it.toPlannedPhaseDefinition() },
            initialEntrantCount = participants.size
        )

        val firstPhase = getFirstPhase(tournament)
        lockPhaseRow(firstPhase.id.value)
        if (firstPhase.matches.any()) {
            if (status != TournamentStatus.STARTED) {
                tournament.status = TournamentStatus.STARTED.name
                tournament.updatedAt = Instant.now()
            }
            return@dbQuery firstPhase.toDomain()
        }
        PhaseExecutionService.startPhase(firstPhase, participants)

        tournament.status = TournamentStatus.STARTED.name
        tournament.updatedAt = Instant.now()

        TournamentPhaseDAO[firstPhase.id.value].toDomain()
    }

    override suspend fun resetTournament(id: Int): TournamentPhase = dbQuery {
        val tournament = TournamentDAO[id]
        val status = TournamentStatus.valueOf(tournament.status)
        if (status != TournamentStatus.STARTED) {
            throw ConflictException("Tournament $id can only be reset from status STARTED")
        }

        val phaseIds = tournament.phases.map { it.id }
        val matches = if (phaseIds.isEmpty()) {
            emptyList()
        } else {
            MatchDAO.find { MatchesTable.phaseId inList phaseIds }.toList()
        }

        if (matches.any { it.status == MatchStatus.COMPLETED.name }) {
            throw ConflictException(
                "Tournament $id has completed matches and cannot be reset. Cancel or abandon it instead."
            )
        }

        val matchIds = matches.map { it.id }
        if (matchIds.isNotEmpty()) {
            MatchDependenciesTable.deleteWhere {
                (MatchDependenciesTable.matchId inList matchIds) or (MatchDependenciesTable.requiredMatchId inList matchIds)
            }
            MatchesTable.deleteWhere { MatchesTable.id inList matchIds }
        }
        if (phaseIds.isNotEmpty()) {
            SwissRankingsTable.deleteWhere { SwissRankingsTable.phaseId inList phaseIds }
            GroupsTable.deleteWhere { GroupsTable.phaseId inList phaseIds }
        }

        tournament.phases.forEach { phase ->
            phase.rounds = initialRoundsFor(phase)
            phase.updatedAt = Instant.now()
        }

        tournament.status = TournamentStatus.DRAFT.name
        tournament.updatedAt = Instant.now()

        getFirstPhase(tournament).toDomain()
    }

    private fun assertUpdateAllowedForStatus(status: TournamentStatus, request: UpdateTournamentRequest) {
        if (status == TournamentStatus.DRAFT) return

        val requestedCompetitionChanges = request.clubId != null || request.startDate != null || request.endDate != null
        if (requestedCompetitionChanges) {
            throw ConflictException(
                "Tournament ${request.id} is $status and only metadata fields (name, description, surface) can be updated"
            )
        }
    }

    private fun assertNoDuplicateSeedInRequest(request: AddPlayersRequest) {
        val nonNullSeeds = request.players.mapNotNull { it.seed }
        val duplicateSeeds = nonNullSeeds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicateSeeds.isNotEmpty()) {
            throw ConflictException("Duplicate seeds in request: $duplicateSeeds")
        }
    }

    private fun assertSeedAvailable(tournamentId: Int, seed: Int, excludePlayerId: Int? = null) {
        val conflictingAssociation = TournamentPlayerDAO.find {
            (TournamentPlayersTable.tournamentId eq tournamentId) and (TournamentPlayersTable.seed eq seed)
        }.firstOrNull {
            excludePlayerId == null || it.player.id.value != excludePlayerId
        }

        if (conflictingAssociation != null) {
            throw ConflictException("Seed $seed is already assigned in tournament $tournamentId")
        }
    }

    private fun isPowerOfTwo(value: Int): Boolean = value > 0 && (value and (value - 1)) == 0

    private fun computeInitialRounds(request: CreatePhaseRequest): Int {
        return when (request.format) {
            PhaseFormat.KNOCKOUT -> {
                request.configuration as? PhaseConfiguration.KnockoutConfig
                    ?: throw IllegalArgumentException("Knockout configuration is required")
                1
            }

            PhaseFormat.GROUP -> {
                val groupConfig = request.configuration as? PhaseConfiguration.GroupConfig
                    ?: throw IllegalArgumentException("Group configuration is required")
                if (groupConfig.teamsPerGroup % 2 == 0) groupConfig.teamsPerGroup - 1 else groupConfig.teamsPerGroup
            }

            PhaseFormat.SWISS -> {
                request.configuration as? PhaseConfiguration.SwissConfig
                    ?: throw IllegalArgumentException("Swiss configuration is required")
                1
            }
        }
    }

    private fun initialRoundsFor(phase: TournamentPhaseDAO): Int {
        return when (PhaseFormat.valueOf(phase.format)) {
            PhaseFormat.KNOCKOUT -> 1
            PhaseFormat.GROUP -> {
                val config = phase.configuration as? PhaseConfiguration.GroupConfig
                    ?: throw IllegalArgumentException("Group configuration is required")
                if (config.teamsPerGroup % 2 == 0) config.teamsPerGroup - 1 else config.teamsPerGroup
            }

            PhaseFormat.SWISS -> 1
        }
    }

    private fun assertDraftTournamentPhaseConfigurationValid(tournament: TournamentDAO) {
        assertPhaseConfigurationValid(
            phases = tournament.phases.map { it.toPlannedPhaseDefinition() },
            initialEntrantCount = tournament.players.count().toInt()
        )
    }

    private fun assertPhaseConfigurationValid(
        phases: List<PlannedPhaseDefinition>,
        initialEntrantCount: Int
    ) {
        var projectedEntrants = initialEntrantCount
        phases.sortedBy { it.phaseOrder }.forEach { phase ->
            projectedEntrants = validatePhaseAndComputeAdvancers(phase, projectedEntrants)
        }
    }

    private fun validatePhaseAndComputeAdvancers(
        phase: PlannedPhaseDefinition,
        projectedEntrants: Int
    ): Int {
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
                if (config.thirdPlacePlayoff) {
                    require(config.qualifiers == 1) {
                        "Phase ${phase.phaseOrder} knockout thirdPlacePlayoff requires qualifiers=1 but got ${config.qualifiers}"
                    }
                    require(projectedEntrants >= 4) {
                        "Phase ${phase.phaseOrder} knockout thirdPlacePlayoff requires at least 4 entrants but projected entrants are $projectedEntrants"
                    }
                }
                config.qualifiers
            }

            PhaseFormat.GROUP -> {
                val config = phase.configuration as? PhaseConfiguration.GroupConfig
                    ?: throw IllegalArgumentException("Group configuration is required")
                require(config.groupCount > 0) { "Phase ${phase.phaseOrder} groupCount must be greater than 0" }
                require(config.teamsPerGroup > 1) { "Phase ${phase.phaseOrder} teamsPerGroup must be greater than 1" }
                require(config.advancingPerGroup in 1..config.teamsPerGroup) {
                    "Phase ${phase.phaseOrder} advancingPerGroup must be between 1 and teamsPerGroup"
                }

                val requiredEntrants = config.groupCount * config.teamsPerGroup
                require(projectedEntrants == requiredEntrants) {
                    "Phase ${phase.phaseOrder} group configuration requires exactly $requiredEntrants entrants but projected entrants are $projectedEntrants"
                }
                config.groupCount * config.advancingPerGroup
            }

            PhaseFormat.SWISS -> {
                val config = phase.configuration as? PhaseConfiguration.SwissConfig
                    ?: throw IllegalArgumentException("Swiss configuration is required")
                require(config.pointsPerWin > 0) { "Phase ${phase.phaseOrder} pointsPerWin must be greater than 0" }
                require(config.advancingCount == null || config.advancingCount >= 2) {
                    "Phase ${phase.phaseOrder} advancingCount must be at least 2 when provided"
                }
                require(config.advancingCount == null || config.advancingCount <= projectedEntrants) {
                    "Phase ${phase.phaseOrder} swiss advancingCount=${config.advancingCount} cannot exceed projected entrants $projectedEntrants"
                }
                config.advancingCount ?: projectedEntrants
            }
        }
    }

    private fun allowedKnockoutQualifiers(projectedEntrants: Int): List<Int> {
        val qualifiers = mutableListOf<Int>()
        var value = 1
        while (value < projectedEntrants) {
            if (isPowerOfTwo(value)) qualifiers += value
            value *= 2
        }
        return qualifiers
    }

    private fun assertTournamentDatesValid(startDate: Instant, endDate: Instant) {
        require(!startDate.isAfter(endDate)) {
            "Tournament startDate must be on or before endDate"
        }
    }

    private fun assertTournamentMutable(tournament: TournamentDAO, operation: String) {
        val status = TournamentStatus.valueOf(tournament.status)
        if (status != TournamentStatus.DRAFT) {
            throw ConflictException("Tournament ${tournament.id.value} is $status and cannot be $operation")
        }
        if (tournament.phases.any { phase -> phase.matches.any() }) {
            throw ConflictException("Tournament ${tournament.id.value} has already started and cannot be $operation")
        }
    }

    private fun getFirstPhase(tournament: TournamentDAO): TournamentPhaseDAO {
        val phaseOne = tournament.phases.filter { it.phaseOrder == 1 }
        if (phaseOne.isEmpty()) {
            throw ConflictException("Tournament ${tournament.id.value} must define a phase with phaseOrder=1 before start")
        }
        if (phaseOne.size > 1) {
            throw ConflictException("Tournament ${tournament.id.value} has multiple phases with phaseOrder=1")
        }
        return phaseOne.first()
    }

    private fun getOrCreatePlayer(request: TournamentPlayerRequest) = when {
        request.playerId != null -> {
            PlayerDAO.findById(request.playerId)
                ?: throw EntityNotFoundException(
                    DaoEntityID(request.playerId, PlayersTable),
                    PlayerDAO
                )
        }

        request.name != null -> {
            PlayerDAO.new {
                name = request.name
                external = true
            }
        }

        else -> throw IllegalArgumentException("Invalid player request")
    }

    private fun TournamentPhaseDAO.toPlannedPhaseDefinition() = PlannedPhaseDefinition(
        phaseOrder = phaseOrder,
        format = PhaseFormat.valueOf(format),
        configuration = configuration
    )

    private fun CreatePhaseRequest.toPlannedPhaseDefinition() = PlannedPhaseDefinition(
        phaseOrder = phaseOrder,
        format = format,
        configuration = configuration
    )

    private data class PlannedPhaseDefinition(
        val phaseOrder: Int,
        val format: PhaseFormat,
        val configuration: PhaseConfiguration
    )
}

