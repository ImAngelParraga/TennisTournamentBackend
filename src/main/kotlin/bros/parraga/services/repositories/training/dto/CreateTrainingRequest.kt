package bros.parraga.services.repositories.training.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateTrainingRequest(
    val trainingDate: String,
    val notes: String? = null
)
