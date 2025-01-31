package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class GroupStanding(
    val groupId: Int,
    val playerId: Int,
    val matchesPlayed: Int,
    val wins: Int,
    val points: Int,
    val createdAt: Instant,
    val updatedAt: Instant?
)