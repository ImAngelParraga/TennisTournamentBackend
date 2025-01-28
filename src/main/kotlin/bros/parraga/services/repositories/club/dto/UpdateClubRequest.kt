package bros.parraga.services.repositories.club.dto

data class UpdateClubRequest(
    val id: Int,
    val name: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val userId: Int? = null
)
