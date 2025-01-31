package bros.parraga.domain

import kotlinx.serialization.Serializable

@Serializable
data class TennisScore(
    val sets: List<SetScore>
)

@Serializable
data class SetScore(
    val player1Games: Int,
    val player2Games: Int,
    val tiebreak: TiebreakScore?
)

@Serializable
data class GameScore(
    val player1Points: Int,
    val player2Points: Int
)

@Serializable
data class TiebreakScore(
    val player1Points: Int,
    val player2Points: Int
)