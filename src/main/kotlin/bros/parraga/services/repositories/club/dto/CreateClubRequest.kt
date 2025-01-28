package bros.parraga.services.repositories.club.dto

data class CreateClubRequest(
    val name: String,
    val phoneNumber: String?,
    val address: String?,
    val userId: Int
)
