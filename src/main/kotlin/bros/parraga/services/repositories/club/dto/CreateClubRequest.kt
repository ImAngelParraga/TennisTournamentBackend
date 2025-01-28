package bros.parraga.services.repositories.club.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateClubRequest(
    val name: String,
    val phoneNumber: String?,
    val address: String?,
    val userId: Int
)
