package bros.parraga.services.repositories.training.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateTrainingRequest(
    val trainingDate: String,
    val durationMinutes: Int? = null,
    val notes: String? = null
)
