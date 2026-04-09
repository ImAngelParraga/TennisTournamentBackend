package bros.parraga.services.repositories.racket.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateRacketStringingRequest(
    val stringingDate: String? = null,
    val mainsKg: Double? = null,
    val crossesKg: Double? = null,
    val mainStringBrand: String? = null,
    val mainStringModel: String? = null,
    val mainStringGauge: String? = null,
    val crossStringBrand: String? = null,
    val crossStringModel: String? = null,
    val crossStringGauge: String? = null,
    val notes: String? = null
)
