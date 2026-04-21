package bros.parraga.services.repositories.racket.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateRacketStringingRequest(
    val stringingDate: String,
    val mainsTensionKg: Double,
    val crossesTensionKg: Double,
    val mainStringType: String? = null,
    val crossStringType: String? = null,
    val performanceNotes: String? = null
)
