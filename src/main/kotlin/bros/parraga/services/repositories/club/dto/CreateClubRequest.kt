package bros.parraga.services.repositories.club.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateClubRequest(
    val name: String,
    val phoneNumber: String?,
    val address: String?,
    // Clubs are provisioned by a platform admin on behalf of the club's user.
    val ownerUserId: Int
)
