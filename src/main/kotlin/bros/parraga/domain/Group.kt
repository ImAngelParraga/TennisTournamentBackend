package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val id: Int,
    val phase: TournamentPhase,
    val name: String,
    val createdAt: Instant?,
    val updatedAt: Instant?
)