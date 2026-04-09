package bros.parraga.services.repositories.racket.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateRacketStringingRequest(
    val racketPublicToken: String? = null,
    val newRacket: NewRacketRequest? = null,
    val stringingDate: String,
    val mainsKg: Double,
    val crossesKg: Double,
    val mainStringBrand: String? = null,
    val mainStringModel: String? = null,
    val mainStringGauge: String? = null,
    val crossStringBrand: String? = null,
    val crossStringModel: String? = null,
    val crossStringGauge: String? = null,
    val notes: String? = null
)

@Serializable
data class NewRacketRequest(
    val displayName: String,
    val brand: String? = null,
    val model: String? = null,
    val stringPattern: String? = null,
    val ownerName: String? = null,
    val ownerUserId: Int? = null
)
