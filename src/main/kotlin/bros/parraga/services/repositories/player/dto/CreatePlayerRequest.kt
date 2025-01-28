package bros.parraga.services.repositories.player.dto

data class CreatePlayerRequest(
    val name: String,
    val external: Boolean,
    val userId: Int?
)
