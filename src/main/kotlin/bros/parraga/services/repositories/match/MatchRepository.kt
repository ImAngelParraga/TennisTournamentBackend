package bros.parraga.services.repositories.match

import bros.parraga.domain.Match
import bros.parraga.services.repositories.match.dto.UpdateMatchScoreRequest

interface MatchRepository {
    suspend fun updateMatchScore(matchId: Int, request: UpdateMatchScoreRequest): Match
    suspend fun getMatch(matchId: Int): Match
}