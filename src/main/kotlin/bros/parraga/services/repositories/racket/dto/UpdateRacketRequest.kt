package bros.parraga.services.repositories.racket.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateRacketRequest(
    val displayName: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val stringPattern: String? = null,
    val ownerName: String? = null,
    val ownerUserId: Int? = null
)
