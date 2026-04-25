package bros.parraga.services.repositories.training.dto

import bros.parraga.domain.TrainingVisibility
import kotlinx.serialization.Serializable

@Serializable
data class UpdateTrainingRequest(
    val trainingDate: String? = null,
    val durationMinutes: Int? = null,
    val notes: String? = null,
    val visibility: TrainingVisibility? = null
)
