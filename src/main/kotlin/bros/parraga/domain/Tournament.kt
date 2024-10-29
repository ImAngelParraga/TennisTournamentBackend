package bros.parraga.domain

import bros.parraga.db.schema.TournamentDAO
import kotlinx.serialization.Serializable

@Serializable
data class Tournament(
    val id: Int,
    val name: String,
    val description: String?,
    val surface: String?,
    val startDate: Long,
    val endDate: Long,
    val created: Long,
    val modified: Long?,
    val players: List<Player>
)

fun TournamentDAO.toDomain() = Tournament(
    id = id.value,
    name = name,
    description = description,
    surface = surface,
    startDate = startDate,
    endDate = endDate,
    created = created,
    modified = modified,
    players = players.map { Player(it.playerName) }
)