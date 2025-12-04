package bros.parraga.services.repositories.match.dto

import bros.parraga.domain.TennisScore
import kotlinx.serialization.Serializable

@Serializable
data class UpdateMatchScoreRequest(
    val score: TennisScore
)