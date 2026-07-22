package bros.parraga.services.repositories.match

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.lockMatchRow
import bros.parraga.db.lockMatchRowInCurrentTransaction
import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.MatchDependencyDAO
import bros.parraga.db.schema.MatchDependenciesTable
import bros.parraga.db.schema.MatchesTable
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.domain.Match
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.Outcome
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.TournamentStatus
import bros.parraga.errors.ConflictException
import bros.parraga.services.TournamentProgressionService
import bros.parraga.services.rating.RatingService
import bros.parraga.services.repositories.match.dto.UpdateMatchScoreRequest
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import parraga.bros.tournament.domain.Match as LibMatch
import parraga.bros.tournament.domain.MatchStatus as LibMatchStatus
import java.time.Instant

class MatchRepositoryImpl : MatchRepository {

    override suspend fun updateMatchScore(matchId: Int, request: UpdateMatchScoreRequest): Match = dbQuery {
        lockMatchRow(matchId)
        val match = MatchDAO.findById(matchId) ?: throw EntityNotFoundException(
            DaoEntityID(matchId, MatchesTable),
            MatchDAO
        )
        request.score.validateForSubmission()

        val matchStatus = MatchStatus.valueOf(match.status)
        val isRescore = matchStatus == MatchStatus.COMPLETED && match.score != request.score
        if (matchStatus == MatchStatus.COMPLETED) {
            if (!isRescore) return@dbQuery match.toDomain()
            assertCompletedMatchCanBeRescored(matchId, match)
            retractCompletedMatchProgression(match)
            RatingService.revertMatchRating(match)
        } else {
            assertMatchIsScoreable(matchId, match)
        }

        val completedAt = Instant.now()

        val libMatch = LibMatch(
            id = match.id.value,
            round = match.round,
            player1Id = match.player1?.id?.value,
            player2Id = match.player2?.id?.value,
            winnerId = null,
            score = null,
            status = LibMatchStatus.SCHEDULED,
            dependencies = emptyList()
        )
        libMatch.applyScore(request.score.toLib())
        if (request.winnerId != null && request.winnerId != libMatch.winnerId) {
            throw IllegalArgumentException("winnerId does not match the submitted score.")
        }

        match.score = request.score
        val winnerId = libMatch.winnerId ?: throw IllegalArgumentException("Score does not produce a winner.")
        match.winner = PlayerDAO[winnerId]
        match.status = MatchStatus.COMPLETED.name
        match.completedAt = completedAt
        match.updatedAt = completedAt

        TournamentProgressionService.onMatchCompleted(match)

        match.toDomain()
    }

    override suspend fun getMatch(matchId: Int): Match = dbQuery {
        MatchDAO.findById(matchId)?.toDomain() ?: throw EntityNotFoundException(
            DaoEntityID(matchId, MatchesTable),
            MatchDAO
        )
    }

    private fun assertMatchIsScoreable(matchId: Int, match: MatchDAO) {
        val matchStatus = MatchStatus.valueOf(match.status)
        if (matchStatus != MatchStatus.SCHEDULED && matchStatus != MatchStatus.LIVE) {
            throw ConflictException("Match $matchId is $matchStatus and cannot be scored.")
        }
        if (match.player1 == null || match.player2 == null) {
            throw ConflictException("Match $matchId cannot be scored until both players are assigned.")
        }
    }

    private fun assertCompletedMatchCanBeRescored(matchId: Int, match: MatchDAO) {
        if (TournamentStatus.valueOf(match.phase.tournament.status) == TournamentStatus.COMPLETED) {
            throw ConflictException("Match $matchId cannot be rescored after the tournament is completed.")
        }
        if (PhaseFormat.valueOf(match.phase.format) != PhaseFormat.KNOCKOUT) {
            throw ConflictException("Completed ${match.phase.format} matches cannot be rescored after standings are applied.")
        }

        dependentMatches(match).forEach { dependentMatch ->
            lockMatchRowInCurrentTransaction(dependentMatch.id.value)
            dependentMatch.refresh(flush = false)
            val dependentStatus = MatchStatus.valueOf(dependentMatch.status)
            if (
                dependentStatus == MatchStatus.LIVE ||
                dependentStatus == MatchStatus.COMPLETED ||
                dependentStatus == MatchStatus.WALKOVER ||
                dependentMatch.winner != null ||
                dependentMatch.score != null
            ) {
                throw ConflictException("Match $matchId cannot be rescored because match ${dependentMatch.id.value} already has a result.")
            }
        }
    }

    private fun retractCompletedMatchProgression(match: MatchDAO) {
        val previousWinner = match.winner
        val previousLoser = when (previousWinner?.id) {
            match.player1?.id -> match.player2
            match.player2?.id -> match.player1
            else -> null
        }
        val now = Instant.now()

        MatchDependencyDAO.find { MatchDependenciesTable.requiredMatchId eq match.id }.forEach { dependency ->
            val dependentMatch = MatchDAO[dependency.matchId]
            val playerToRemove = when (Outcome.valueOf(dependency.requiredOutcome)) {
                Outcome.WINNER -> previousWinner
                Outcome.LOSER -> previousLoser
            } ?: return@forEach

            when (playerToRemove.id) {
                dependentMatch.player1?.id -> dependentMatch.player1 = null
                dependentMatch.player2?.id -> dependentMatch.player2 = null
            }
            dependentMatch.updatedAt = now
        }

        match.winner = null
        match.score = null
        match.status = MatchStatus.SCHEDULED.name
        match.completedAt = null
        match.updatedAt = now
    }

    private fun dependentMatches(match: MatchDAO): List<MatchDAO> =
        MatchDependencyDAO.find { MatchDependenciesTable.requiredMatchId eq match.id }
            .map { MatchDAO[it.matchId] }
            .toList()
}
