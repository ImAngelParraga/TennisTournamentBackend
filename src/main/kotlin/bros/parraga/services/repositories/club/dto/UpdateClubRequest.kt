package bros.parraga.services.repositories.club.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateClubRequest(
    val id: Int,
    val name: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val userId: Int? = null
)
