package bros.parraga.services

import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.MatchDependenciesTable
import bros.parraga.db.schema.MatchDependencyDAO
import bros.parraga.db.schema.MatchesTable
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.TournamentPhaseDAO
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.Outcome
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.TournamentStatus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import java.time.Instant

object TournamentProgressionService {
    fun onMatchCompleted(match: MatchDAO) {
        when (PhaseFormat.valueOf(match.phase.format)) {
            PhaseFormat.KNOCKOUT -> progressKnockout(match)
            PhaseFormat.SWISS -> progressSwiss(match)
            PhaseFormat.GROUP -> Unit
        }
    }

    private fun progressKnockout(match: MatchDAO) {
        val winner = match.winner ?: return
        val loser = when {
            match.player1?.id == winner.id -> match.player2
            match.player2?.id == winner.id -> match.player1
            else -> null
        }

        MatchDependencyDAO.find { MatchDependenciesTable.requiredMatchId eq match.id }.forEach { dependency ->
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

        maybeMarkTournamentCompleted(match.phase)
    }

    private fun progressSwiss(match: MatchDAO) {
        val phase = match.phase
        val currentRound = match.round
        if (currentRound >= phase.rounds) {
            maybeMarkTournamentCompleted(phase)
            return
        }

        val roundMatches = MatchDAO.find {
            (MatchesTable.phaseId eq phase.id) and (MatchesTable.round eq currentRound)
        }.toList()

        val roundComplete = roundMatches.all {
            it.status == MatchStatus.COMPLETED.name || it.status == MatchStatus.WALKOVER.name
        }
        if (!roundComplete) return

        val nextRound = currentRound + 1
        val existingNextRound = MatchDAO.find {
            (MatchesTable.phaseId eq phase.id) and (MatchesTable.round eq nextRound)
        }
        if (existingNextRound.any()) {
            maybeMarkTournamentCompleted(phase)
            return
        }

        val config = phase.configuration
        val pointsPerWin = (config as? PhaseConfiguration.SwissConfig)?.pointsPerWin ?: 1

        val matchesSoFar = MatchDAO.find {
            (MatchesTable.phaseId eq phase.id) and (MatchesTable.round lessEq currentRound)
        }.toList()

        val pointsByPlayer = mutableMapOf<Int, Int>().withDefault { 0 }
        matchesSoFar.forEach { roundMatch ->
            val winnerId = roundMatch.winner?.id?.value ?: return@forEach
            pointsByPlayer[winnerId] = (pointsByPlayer[winnerId] ?: 0) + pointsPerWin
        }

        val players = phase.tournament.players.toList()
        val orderedPlayers = players.sortedWith(
            compareByDescending<PlayerDAO> { pointsByPlayer[it.id.value] ?: 0 }
                .thenBy { it.id.value }
        )

        var index = 0
        while (index < orderedPlayers.size) {
            val player1 = orderedPlayers[index]
            val player2 = orderedPlayers.getOrNull(index + 1)
            val isBye = player2 == null

            MatchDAO.new {
                this.phase = phase
                round = nextRound
                this.player1 = player1
                this.player2 = player2
                if (isBye) {
                    winner = player1
                    status = MatchStatus.WALKOVER.name
                } else {
                    status = MatchStatus.SCHEDULED.name
                }
            }

            index += 2
        }

        maybeMarkTournamentCompleted(phase)
    }

    private fun maybeMarkTournamentCompleted(phase: TournamentPhaseDAO) {
        val tournament = phase.tournament
        if (TournamentStatus.valueOf(tournament.status) != TournamentStatus.STARTED) return

        val hasLaterPhases = tournament.phases.any { it.phaseOrder > phase.phaseOrder }
        if (hasLaterPhases) return

        val phaseMatches = phase.matches.toList()
        if (phaseMatches.isEmpty()) return

        val allFinished = phaseMatches.all {
            it.status == MatchStatus.COMPLETED.name || it.status == MatchStatus.WALKOVER.name
        }
        if (!allFinished) return

        tournament.status = TournamentStatus.COMPLETED.name
        tournament.updatedAt = Instant.now()
    }
}
