package bros.parraga.services.repositories.racket.dto

import bros.parraga.domain.RacketVisibility
import kotlinx.serialization.Serializable

@Serializable
data class CreateRacketRequest(
    val displayName: String,
    val brand: String? = null,
    val model: String? = null,
    val stringPattern: String? = null,
    val visibility: RacketVisibility = RacketVisibility.PRIVATE
)
