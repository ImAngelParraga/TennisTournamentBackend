package bros.parraga.domain

import kotlinx.serialization.Serializable

@Serializable
data class MatchDependency(
    val requiredMatchId: Int,
    val requiredOutcome: Outcome
)

enum class Outcome { WINNER, LOSER }
