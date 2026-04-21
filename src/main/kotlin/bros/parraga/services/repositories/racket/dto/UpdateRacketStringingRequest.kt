package bros.parraga.services.repositories.racket.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateRacketStringingRequest(
    val stringingDate: String? = null,
    val mainsTensionKg: Double? = null,
    val crossesTensionKg: Double? = null,
    val mainStringType: String? = null,
    val crossStringType: String? = null,
    val performanceNotes: String? = null
)
