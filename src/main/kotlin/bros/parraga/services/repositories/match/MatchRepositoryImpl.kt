package bros.parraga.services.repositories.match

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.MatchesTable
import bros.parraga.services.repositories.match.dto.UpdateMatchScoreRequest
import bros.parraga.domain.Match
import bros.parraga.domain.MatchStatus
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import java.time.Instant

class MatchRepositoryImpl : MatchRepository {

    override suspend fun updateMatchScore(matchId: Int, request: UpdateMatchScoreRequest): Match = dbQuery {
        val match = MatchDAO.findById(matchId) ?: throw EntityNotFoundException(
            DaoEntityID(matchId, MatchesTable),
            MatchDAO
        )

        match.score = request.score
        match.status = MatchStatus.COMPLETED.name
        match.updatedAt = Instant.now()

        match.toDomain()
    }

    override suspend fun getMatch(matchId: Int): Match = dbQuery {
        MatchDAO.findById(matchId)?.toDomain() ?: throw EntityNotFoundException(
            DaoEntityID(matchId, MatchesTable),
            MatchDAO
        )
    }
}