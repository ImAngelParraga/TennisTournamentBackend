package bros.parraga.domain

data class MatchDependency(
    val match: Match,
    val requiredMatch: Match,
    val requiredOutcome: Outcome
)

enum class Outcome { WINNER, LOSER }
