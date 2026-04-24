package bros.parraga.services.repositories.match

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.lockMatchRow
import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.MatchesTable
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.domain.Match
import bros.parraga.domain.MatchStatus
import bros.parraga.errors.ConflictException
import bros.parraga.services.TournamentProgressionService
import bros.parraga.services.repositories.match.dto.UpdateMatchScoreRequest
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
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
        if (matchStatus == MatchStatus.COMPLETED) {
            return@dbQuery when {
                match.score == request.score -> match.toDomain()
                else -> throw ConflictException("Match $matchId is COMPLETED and cannot be rescored with a different payload.")
            }
        }

        assertMatchIsScoreable(matchId, match)
        val completedAt = Instant.now()

        val libMatch = LibMatch(
            id = match.id.value,
            round = match.round,
            player1Id = match.player1?.id?.value,
            player2Id = match.player2?.id?.value,
            winnerId = match.winner?.id?.value,
            score = null,
            status = LibMatchStatus.valueOf(match.status),
            dependencies = emptyList()
        )
        libMatch.applyScore(request.score.toLib())

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
}
