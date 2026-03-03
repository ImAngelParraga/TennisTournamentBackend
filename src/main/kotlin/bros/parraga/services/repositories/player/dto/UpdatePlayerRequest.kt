package bros.parraga.services.repositories.player.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePlayerRequest(
    val id: Int,
    val name: String? = null
)
