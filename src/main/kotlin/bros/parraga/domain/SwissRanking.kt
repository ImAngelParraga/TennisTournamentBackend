package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SwissRanking(
    val phase: TournamentPhase,
    val player: Player,
    val round: Int,
    val points: Int,
    val createdAt: Instant,
    val updatedAt: Instant?
)