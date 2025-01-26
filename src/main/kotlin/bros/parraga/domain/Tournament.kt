package bros.parraga.domain

import bros.parraga.db.schema.TournamentDAO
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable

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
    startDate = startDate.toKotlinInstant(),
    endDate = endDate.toKotlinInstant(),
    created = created.toKotlinInstant(),
    modified = modified?.toKotlinInstant(),
    players = players.map { Player(name = it.playerName) }
)

fun TournamentDAO.fromDomain(tournament: Tournament) {
    name = tournament.name
    description = tournament.description
    surface = tournament.surface
    startDate = tournament.startDate.toJavaInstant()
    endDate = tournament.endDate.toJavaInstant()
    created = tournament.created.toJavaInstant()
    modified = tournament.modified?.toJavaInstant()
}