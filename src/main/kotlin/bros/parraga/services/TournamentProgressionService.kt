package bros.parraga.services

import bros.parraga.db.lockTournamentRowInCurrentTransaction
import bros.parraga.db.lockMatchRowInCurrentTransaction
import bros.parraga.db.lockPhaseRowInCurrentTransaction
import bros.parraga.db.schema.GroupStandingDAO
import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.MatchDependenciesTable
import bros.parraga.db.schema.MatchDependencyDAO
import bros.parraga.db.schema.MatchesTable
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.GroupStandingsTable
import bros.parraga.db.schema.SwissRankingsTable
import bros.parraga.db.schema.TournamentPhaseDAO
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.Outcome
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.TournamentStatus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant

object TournamentProgressionService {
    fun onMatchCompleted(match: MatchDAO) {
        when (PhaseFormat.valueOf(match.phase.format)) {
            PhaseFormat.KNOCKOUT -> progressKnockout(match)
            PhaseFormat.SWISS -> progressSwiss(match)
            PhaseFormat.GROUP -> progressGroup(match)
        }
    }

    private fun progressKnockout(match: MatchDAO) {
        lockPhaseRowInCurrentTransaction(match.phase.id.value)
        val winner = match.winner ?: return
        val loser = when {
            match.player1?.id == winner.id -> match.player2
            match.player2?.id == winner.id -> match.player1
            else -> null
        }

        MatchDependencyDAO.find { MatchDependenciesTable.requiredMatchId eq match.id }.forEach { dependency ->
            lockMatchRowInCurrentTransaction(dependency.matchId.value)
            val dependentMatch = MatchDAO[dependency.matchId]
            val outcome = Outcome.valueOf(dependency.requiredOutcome)
            val player = when (outcome) {
                Outcome.WINNER -> winner
                Outcome.LOSER -> loser
            } ?: return@forEach

            if (dependentMatch.player1?.id == player.id || dependentMatch.player2?.id == player.id) return@forEach

            when {
                dependentMatch.player1 == null -> dependentMatch.player1 = player
                dependentMatch.player2 == null -> dependentMatch.player2 = player
                else -> return@forEach
            }

            dependentMatch.updatedAt = Instant.now()
        }

        maybeAdvanceOrCompletePhase(match.phase)
    }

    private fun progressGroup(match: MatchDAO) {
        val phase = match.phase
        lockPhaseRowInCurrentTransaction(phase.id.value)
        updateGroupStandings(match)
        maybeAdvanceOrCompletePhase(phase)
    }

    private fun progressSwiss(match: MatchDAO) {
        val phase = match.phase
        lockPhaseRowInCurrentTransaction(phase.id.value)
        val currentRound = match.round

        val roundMatches = MatchDAO.find {
            (MatchesTable.phaseId eq phase.id) and (MatchesTable.round eq currentRound)
        }.toList()

        val roundComplete = roundMatches.all {
            it.status == MatchStatus.COMPLETED.name || it.status == MatchStatus.WALKOVER.name
        }
        if (!roundComplete) return

        val pointsByPlayer = computeSwissPoints(phase, currentRound)
        PhaseExecutionService.recordSwissRankings(phase, currentRound, pointsByPlayer)

        if (currentRound < phase.rounds) {
            val nextRound = currentRound + 1
            val existingNextRound = MatchDAO.find {
                (MatchesTable.phaseId eq phase.id) and (MatchesTable.round eq nextRound)
            }
            if (!existingNextRound.any()) {
                PhaseExecutionService.createNextSwissRound(phase)
            }
        }

        maybeAdvanceOrCompletePhase(phase)
    }

    private fun maybeAdvanceOrCompletePhase(phase: TournamentPhaseDAO) {
        val tournament = phase.tournament
        lockTournamentRowInCurrentTransaction(tournament.id.value)
        if (TournamentStatus.valueOf(tournament.status) != TournamentStatus.STARTED) return

        val phaseMatches = phase.matches.toList()
        if (phaseMatches.isEmpty()) return

        val allFinished = phaseMatches.all {
            it.status == MatchStatus.COMPLETED.name || it.status == MatchStatus.WALKOVER.name
        }
        if (!allFinished) return

        val hasLaterPhases = tournament.phases.any { it.phaseOrder > phase.phaseOrder }
        if (hasLaterPhases) {
            PhaseExecutionService.startNextPhaseIfNeeded(phase)
            return
        }

        tournament.champion = resolveTournamentWinner(phase)
        tournament.status = TournamentStatus.COMPLETED.name
        tournament.updatedAt = Instant.now()
    }

    private fun resolveTournamentWinner(phase: TournamentPhaseDAO): PlayerDAO {
        val candidateIds = when (PhaseFormat.valueOf(phase.format)) {
            PhaseFormat.KNOCKOUT -> resolveKnockoutWinnerIds(phase)
            PhaseFormat.GROUP -> resolveGroupWinnerIds(phase)
            PhaseFormat.SWISS -> resolveSwissWinnerIds(phase)
        }
        val winnerId = candidateIds.distinct().sorted().firstOrNull()
            ?: throw IllegalStateException("Could not resolve tournament winner for phase ${phase.id.value}")
        return PlayerDAO[winnerId]
    }

    private fun resolveKnockoutWinnerIds(phase: TournamentPhaseDAO): List<Int> {
        val highestRound = phase.matches.maxOfOrNull { it.round } ?: return emptyList()
        return phase.matches
            .filter { it.round == highestRound }
            .filter { match ->
                val dependencies = match.matchDependencies.toList()
                dependencies.isEmpty() || dependencies.all { it.requiredOutcome == Outcome.WINNER.name }
            }
            .mapNotNull { it.winner?.id?.value }
            .distinct()
    }

    private fun resolveGroupWinnerIds(phase: TournamentPhaseDAO): List<Int> {
        val groupIds = phase.matches.mapNotNull { it.group?.id }.distinct()
        if (groupIds.isEmpty()) return emptyList()

        val standings = GroupStandingDAO.find { GroupStandingsTable.groupId inList groupIds }
            .toList()
        val maxPoints = standings.maxOfOrNull { it.points } ?: return emptyList()
        return standings
            .filter { it.points == maxPoints }
            .map { it.player.id.value }
            .distinct()
    }

    private fun resolveSwissWinnerIds(phase: TournamentPhaseDAO): List<Int> {
        val finalRoundRankings = SwissRankingsTable.selectAll().where {
            (SwissRankingsTable.phaseId eq phase.id) and (SwissRankingsTable.round eq phase.rounds)
        }.toList()
        val maxPoints = finalRoundRankings.maxOfOrNull { it[SwissRankingsTable.points] } ?: return emptyList()
        return finalRoundRankings
            .filter { it[SwissRankingsTable.points] == maxPoints }
            .map { it[SwissRankingsTable.playerId].value }
            .distinct()
    }

    private fun updateGroupStandings(match: MatchDAO) {
        val group = match.group ?: return
        val winner = match.winner ?: return
        val player1 = match.player1 ?: return
        val player2 = match.player2 ?: return

        val player1Standing = GroupStandingDAO.find {
            (bros.parraga.db.schema.GroupStandingsTable.groupId eq group.id) and
                (bros.parraga.db.schema.GroupStandingsTable.playerId eq player1.id)
        }.first()
        val player2Standing = GroupStandingDAO.find {
            (bros.parraga.db.schema.GroupStandingsTable.groupId eq group.id) and
                (bros.parraga.db.schema.GroupStandingsTable.playerId eq player2.id)
        }.first()

        player1Standing.matchesPlayed += 1
        player2Standing.matchesPlayed += 1
        player1Standing.updatedAt = Instant.now()
        player2Standing.updatedAt = Instant.now()

        val winnerStanding = when (winner.id) {
            player1.id -> player1Standing
            player2.id -> player2Standing
            else -> return
        }
        winnerStanding.wins += 1
        winnerStanding.points += 1
        winnerStanding.updatedAt = Instant.now()
    }

    private fun computeSwissPoints(phase: TournamentPhaseDAO, throughRound: Int): Map<Int, Int> {
        val pointsPerWin = (phase.configuration as? PhaseConfiguration.SwissConfig)?.pointsPerWin ?: 1
        val matchesSoFar = MatchDAO.find {
            (MatchesTable.phaseId eq phase.id) and (MatchesTable.round lessEq throughRound)
        }.toList()

        val pointsByPlayer = mutableMapOf<Int, Int>().withDefault { 0 }
        matchesSoFar.forEach { roundMatch ->
            val winnerId = roundMatch.winner?.id?.value ?: return@forEach
            pointsByPlayer[winnerId] = (pointsByPlayer[winnerId] ?: 0) + pointsPerWin
        }
        return pointsByPlayer
    }
}
