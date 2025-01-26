package bros.parraga.domain

import bros.parraga.db.schema.TournamentDAO
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Tournament(
    val id: Int?,
    val name: String,
    val description: String?,
    val surface: String?,
    val startDate: Instant,
    val endDate: Instant,
    val created: Instant,
    val modified: Instant?,
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

fun TournamentDAO.fromDomain(tournament: Tournament) {
    name = tournament.name
    description = tournament.description
    surface = tournament.surface
    startDate = tournament.startDate
    endDate = tournament.endDate
    created = tournament.created
    modified = tournament.modified
}