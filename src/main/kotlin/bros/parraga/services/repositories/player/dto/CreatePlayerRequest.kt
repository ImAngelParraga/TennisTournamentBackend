package bros.parraga.services.repositories.player.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreatePlayerRequest(
    val name: String,
    val external: Boolean,
    val userId: Int? = null
)
