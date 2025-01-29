package bros.parraga.domain

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