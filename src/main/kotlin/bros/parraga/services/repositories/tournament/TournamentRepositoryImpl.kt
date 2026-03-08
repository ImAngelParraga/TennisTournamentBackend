package bros.parraga.services.repositories.tournament

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.lockPhaseRow
import bros.parraga.db.lockTournamentRow
import bros.parraga.db.schema.*
import bros.parraga.domain.*
import bros.parraga.errors.ConflictException
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
import kotlin.math.ceil
import kotlin.math.log2
import parraga.bros.tournament.domain.Match as LibMatch
import parraga.bros.tournament.domain.SeededParticipant as LibSeededParticipant

class TournamentRepositoryImpl : TournamentRepository {
    override suspend fun getTournaments(): List<TournamentBasic> = dbQuery { TournamentDAO.all().map { it.toBasic() } }

    override suspend fun getTournament(id: Int): TournamentBasic = dbQuery { TournamentDAO[id].toBasic() }

    override suspend fun createTournament(request: CreateTournamentRequest): TournamentBasic = dbQuery {
        TournamentDAO.new {
            name = request.name
            description = request.description
            surface = request.surface
            status = TournamentStatus.DRAFT.name
            club = ClubDAO[request.clubId]
            startDate = request.startDate.toJavaInstant()
            endDate = request.endDate.toJavaInstant()
        }.toBasic()
    }

    override suspend fun updateTournament(request: UpdateTournamentRequest): TournamentBasic = dbQuery {
        val tournament = TournamentDAO.findById(request.id)
            ?: throw EntityNotFoundException(DaoEntityID(request.id, TournamentsTable), TournamentDAO)
        val status = TournamentStatus.valueOf(tournament.status)
        assertUpdateAllowedForStatus(status, request)

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
        require(request.format == PhaseFormat.KNOCKOUT) { "Only KNOCKOUT phases are supported without rounds" }

        val knockoutConfig = request.configuration as? PhaseConfiguration.KnockoutConfig
            ?: throw IllegalArgumentException("Knockout configuration is required")
        require(knockoutConfig.qualifiers >= 1) { "qualifiers must be greater than 0" }
        require(isPowerOfTwo(knockoutConfig.qualifiers)) { "qualifiers must be a power of two" }

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

        TournamentPhaseDAO.new {
            this.tournament = tournament
            phaseOrder = request.phaseOrder
            format = request.format.name
            rounds = 1 // computed at start based on qualifiers and player count
            configuration = knockoutConfig
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

        val firstPhase = getFirstPhase(tournament)
        lockPhaseRow(firstPhase.id.value)
        if (firstPhase.matches.any()) {
            if (status != TournamentStatus.STARTED) {
                tournament.status = TournamentStatus.STARTED.name
                tournament.updatedAt = Instant.now()
            }
            return@dbQuery firstPhase.toDomain()
        }

        val format = PhaseFormat.valueOf(firstPhase.format)
        val roundsToPlay = when (format) {
            PhaseFormat.KNOCKOUT -> {
                val config = firstPhase.configuration as? PhaseConfiguration.KnockoutConfig
                    ?: throw IllegalArgumentException("Knockout configuration is required")
                computeKnockoutRounds(participants.size, config.qualifiers)
            }

            else -> firstPhase.rounds
        }

        firstPhase.rounds = roundsToPlay
        firstPhase.updatedAt = Instant.now()

        val phaseLib = Phase(
            firstPhase.phaseOrder,
            Format.valueOf(firstPhase.format),
            roundsToPlay,
            firstPhase.configuration.toPhaseConfigurationLib(),
            emptyList()
        )

        val allMatches = TournamentService.startPhaseWithParticipants(phaseLib, participants)
        val firstPhaseMatches = if (format == PhaseFormat.KNOCKOUT) {
            allMatches.filter { it.round <= roundsToPlay }
        } else {
            allMatches
        }

        saveMatchesForPhase(firstPhaseMatches, firstPhase)
        applyWalkovers(firstPhase)

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

        val firstPhase = getFirstPhase(tournament)
        firstPhase.rounds = 1
        firstPhase.updatedAt = Instant.now()

        tournament.status = TournamentStatus.DRAFT.name
        tournament.updatedAt = Instant.now()

        TournamentPhaseDAO[firstPhase.id.value].toDomain()
    }

    fun saveMatchesForPhase(
        matches: List<LibMatch>,
        phaseDao: TournamentPhaseDAO
    ) {
        val fakeIdsToRealMatches = mutableMapOf<Int, MatchDAO>()
        val roundSlotsByMatchId = computeRoundSlotsByMatchId(matches)

        matches.forEach { match ->
            val player1Dao = match.player1Id?.let { PlayerDAO[it] }
            val player2Dao = match.player2Id?.let { PlayerDAO[it] }
            val winnerDao = match.winnerId?.let {
                if (match.winnerId == match.player1Id) player1Dao else player2Dao
            }
            val tennisScore = TennisScore.fromLib(match.score)

            val matchDao = MatchDAO.new {
                phase = phaseDao
                round = match.round
                roundSlot = roundSlotsByMatchId.getValue(match.id)
                player1 = player1Dao
                player2 = player2Dao
                winner = winnerDao
                score = tennisScore
                status = match.status.name
            }

            fakeIdsToRealMatches[match.id] = matchDao
        }

        matches.forEach { match ->
            val matchDao = fakeIdsToRealMatches[match.id]
                ?: throw IllegalStateException("Missing match mapping for id ${match.id}")

            match.dependencies.forEach { dependency ->
                val requiredMatch = fakeIdsToRealMatches[dependency.requiredMatchId]
                    ?: throw IllegalStateException("Missing dependency match id ${dependency.requiredMatchId}")
                require(match.id != dependency.requiredMatchId) {
                    "Match ${match.id} cannot depend on itself."
                }

                MatchDependencyDAO.new {
                    matchId = matchDao.id
                    this.requiredMatch = requiredMatch
                    requiredOutcome = dependency.requiredOutcome.name
                }
            }
        }
    }

    private fun computeRoundSlotsByMatchId(matches: List<LibMatch>): Map<Int, Int> {
        return matches
            .groupBy { it.round }
            .flatMap { (_, roundMatches) ->
                roundMatches
                    .sortedBy { it.id }
                    .mapIndexed { index, match -> match.id to index + 1 }
            }
            .toMap()
    }

    private fun applyWalkovers(phaseDao: TournamentPhaseDAO) {
        val walkovers = MatchDAO.find {
            (MatchesTable.phaseId eq phaseDao.id) and (MatchesTable.status eq MatchStatus.WALKOVER.name)
        }.toList()

        walkovers.forEach { match ->
            if (match.winner == null) {
                match.winner = match.player1 ?: match.player2
                match.updatedAt = Instant.now()
            }
            TournamentProgressionService.onMatchCompleted(match)
        }
    }

    private fun computeKnockoutRounds(playerCount: Int, qualifiers: Int): Int {
        require(playerCount >= 2) { "Tournament must have at least 2 players" }
        require(qualifiers >= 1) { "qualifiers must be greater than 0" }
        require(isPowerOfTwo(qualifiers)) { "qualifiers must be a power of two" }
        require(qualifiers < playerCount) { "qualifiers must be less than player count" }

        val totalRounds = ceil(log2(playerCount.toDouble())).toInt()
        val targetRounds = log2(qualifiers.toDouble()).toInt()
        val roundsToPlay = totalRounds - targetRounds
        require(roundsToPlay > 0) { "Computed rounds must be greater than 0" }
        return roundsToPlay
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
}

