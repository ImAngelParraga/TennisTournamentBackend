package bros.parraga.domain

import bros.parraga.db.schema.PlayerDAO
import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val name: String,
    val external: Boolean,
    val user: User? = null,
    val tournaments: List<Tournament> = emptyList<Tournament>()
)

fun PlayerDAO.toDomain(includeTournaments: Boolean = true): Player =
    Player(
        name,
        external,
        user?.toDomain(),
        if (includeTournaments) tournaments.map { it.toDomain(false) } else emptyList())