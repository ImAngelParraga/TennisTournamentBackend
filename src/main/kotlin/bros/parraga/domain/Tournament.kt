package bros.parraga.domain

import bros.parraga.db.schema.TournamentDAO
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable

@Serializable
data class Tournament(
    val id: Int,
    val name: String,
    val description: String?,
    val surface: String?,
    val club: Club,
    val startDate: Instant,
    val endDate: Instant,
    val createdAt: Instant,
    val modified: Instant,
    val players: List<Player>
)

fun TournamentDAO.toDomain(includePlayers: Boolean = true) = Tournament(
    id = id.value,
    name = name,
    description = description,
    surface = surface,
    club = club.toDomain(),
    startDate = startDate.toKotlinInstant(),
    endDate = endDate.toKotlinInstant(),
    createdAt = createdAt.toKotlinInstant(),
    modified = modifiedAt.toKotlinInstant(),
    players = if (includePlayers) players.map { it.toDomain(false) } else emptyList()
)