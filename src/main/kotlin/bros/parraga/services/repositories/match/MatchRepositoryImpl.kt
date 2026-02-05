package bros.parraga.services.repositories.match

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.MatchesTable
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.domain.Match
import bros.parraga.domain.MatchStatus
import bros.parraga.services.TournamentProgressionService
import bros.parraga.services.repositories.match.dto.UpdateMatchScoreRequest
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import parraga.bros.tournament.domain.Match as LibMatch
import parraga.bros.tournament.domain.MatchStatus as LibMatchStatus
import java.time.Instant

class MatchRepositoryImpl : MatchRepository {

    override suspend fun updateMatchScore(matchId: Int, request: UpdateMatchScoreRequest): Match = dbQuery {
        val match = MatchDAO.findById(matchId) ?: throw EntityNotFoundException(
            DaoEntityID(matchId, MatchesTable),
            MatchDAO
        )

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
        match.updatedAt = Instant.now()

        TournamentProgressionService.onMatchCompleted(match)

        match.toDomain()
    }

    override suspend fun getMatch(matchId: Int): Match = dbQuery {
        MatchDAO.findById(matchId)?.toDomain() ?: throw EntityNotFoundException(
            DaoEntityID(matchId, MatchesTable),
            MatchDAO
        )
    }
}
