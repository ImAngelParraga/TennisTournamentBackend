package bros.parraga.services.repositories.tournament

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.*
import bros.parraga.domain.*
import bros.parraga.services.TournamentProgressionService
import bros.parraga.services.repositories.tournament.dto.*
import io.ktor.server.plugins.*
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.and
import parraga.bros.tournament.domain.Format
import parraga.bros.tournament.domain.Phase
import parraga.bros.tournament.services.TournamentService
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.log2
import parraga.bros.tournament.domain.Match as LibMatch

class TournamentRepositoryImpl : TournamentRepository {
    override suspend fun getTournaments(): List<TournamentBasic> = dbQuery { TournamentDAO.all().map { it.toBasic() } }

    override suspend fun getTournament(id: Int): TournamentBasic = dbQuery { TournamentDAO[id].toBasic() }

    override suspend fun createTournament(request: CreateTournamentRequest): TournamentBasic = dbQuery {
        TournamentDAO.new {
            name = request.name
            description = request.description
            surface = request.surface
            club = ClubDAO[request.clubId]
            startDate = request.startDate.toJavaInstant()
            endDate = request.endDate.toJavaInstant()
        }.toBasic()
    }

    override suspend fun updateTournament(request: UpdateTournamentRequest): TournamentBasic = dbQuery {
        TournamentDAO.findByIdAndUpdate(request.id) { tournamentDAO ->
            tournamentDAO.apply {
                request.name?.let { name = it }
                request.description?.let { description = it }
                request.surface?.let { surface = it }
                request.clubId?.let { club = ClubDAO[it] }
                request.startDate?.let { startDate = it.toJavaInstant() }
                request.endDate?.let { endDate = it.toJavaInstant() }
                updatedAt = Instant.now()
            }
        }?.toBasic()
            ?: throw EntityNotFoundException(
                DaoEntityID(request.id, TournamentsTable),
                TournamentDAO
            )
    }

    override suspend fun deleteTournament(id: Int) = dbQuery { TournamentDAO[id].delete() }

    override suspend fun createPhase(tournamentId: Int, request: CreatePhaseRequest): TournamentPhase = dbQuery {
        require(request.phaseOrder > 0) { "phaseOrder must be greater than 0" }
        require(request.format == PhaseFormat.KNOCKOUT) { "Only KNOCKOUT phases are supported without rounds" }

        val knockoutConfig = request.configuration as? PhaseConfiguration.KnockoutConfig
            ?: throw IllegalArgumentException("Knockout configuration is required")
        require(knockoutConfig.qualifiers >= 1) { "qualifiers must be greater than 0" }
        require(isPowerOfTwo(knockoutConfig.qualifiers)) { "qualifiers must be a power of two" }

        val tournament = TournamentDAO[tournamentId]
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

    override suspend fun addPlayersToTournament(
        tournamentId: Int,
        request: AddPlayersRequest
    ) = dbQuery {
        val tournament = TournamentDAO[tournamentId]

        request.players.forEach { request ->
            val player = getOrCreatePlayer(request)

            if (tournament.players.none { it.id == player.id }) {
                TournamentPlayerDAO.new {
                    this.tournament = tournament
                    this.player = player
                }
            }
        }
    }

    override suspend fun removePlayerFromTournament(tournamentId: Int, playerId: Int) = dbQuery {
        val association = TournamentPlayerDAO.find {
            TournamentPlayersTable.tournamentId.eq(tournamentId) and TournamentPlayersTable.playerId.eq(playerId)
        }.firstOrNull() ?: throw NotFoundException(
            "No association found between tournament $tournamentId and player $playerId"
        )

        association.delete()
    }

    override suspend fun startTournament(id: Int): TournamentPhase = dbQuery {
        val tournament = TournamentDAO[id]
        require(tournament.phases.count() > 0) { "Tournament has no phases" }
        require(tournament.players.count() >= 2) { "Tournament must have at least 2 players" }

        val firstPhase = tournament.phases.first { it.phaseOrder == 1 }
        if (firstPhase.matches.any()) {
            return@dbQuery firstPhase.toDomain()
        }

        val playerIds = tournament.players.map { it.id.value }
        val format = PhaseFormat.valueOf(firstPhase.format)
        val roundsToPlay = when (format) {
            PhaseFormat.KNOCKOUT -> {
                val config = firstPhase.configuration as? PhaseConfiguration.KnockoutConfig
                    ?: throw IllegalArgumentException("Knockout configuration is required")
                computeKnockoutRounds(playerIds.size, config.qualifiers)
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

        val allMatches = TournamentService.startPhase(phaseLib, playerIds)
        val firstPhaseMatches = if (format == PhaseFormat.KNOCKOUT) {
            allMatches.filter { it.round <= roundsToPlay }
        } else {
            allMatches
        }

        saveMatchesForPhase(firstPhaseMatches, firstPhase)
        applyWalkovers(firstPhase)

        TournamentPhaseDAO[firstPhase.id.value].toDomain()
    }

    fun saveMatchesForPhase(
        matches: List<LibMatch>,
        phaseDao: TournamentPhaseDAO
    ) {
        val fakeIdsToRealMatches = mutableMapOf<Int, MatchDAO>()

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

                MatchDependencyDAO.new {
                    matchId = matchDao.id
                    this.requiredMatch = requiredMatch
                    requiredOutcome = dependency.requiredOutcome.name
                }
            }
        }
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

    private fun isPowerOfTwo(value: Int): Boolean = value > 0 && (value and (value - 1)) == 0

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
