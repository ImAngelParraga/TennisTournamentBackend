package bros.parraga.domain

import bros.parraga.db.schema.ClubDAO
import kotlinx.serialization.Serializable

@Serializable
data class Club(
    val id: Int,
    val name: String,
    val phoneNumber: String?,
    val address: String?,
    val user: User,
    val tournaments: List<Tournament>
)

fun ClubDAO.toDomain(): Club = Club(
    id = id.value,
    name = name,
    phoneNumber = phoneNumber,
    address = address,
    user = user.toDomain(),
    tournaments = tournaments.map { it.toDomain() }
)