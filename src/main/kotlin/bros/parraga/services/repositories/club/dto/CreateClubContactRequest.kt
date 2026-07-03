package bros.parraga.services.repositories.club.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateClubContactRequest(
    val clubName: String,
    val contactName: String,
    val email: String,
    val phone: String? = null,
    val message: String? = null
)
