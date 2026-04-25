package bros.parraga.services.repositories.training

import bros.parraga.domain.UserTrainingEntry
import bros.parraga.domain.UserTrainingRangeResponse
import bros.parraga.services.repositories.training.dto.CreateTrainingRequest
import bros.parraga.services.repositories.training.dto.UpdateTrainingRequest
import java.time.LocalDate

interface TrainingRepository {
    suspend fun getOwnTrainingRange(ownerUserId: Int, from: LocalDate, to: LocalDate): UserTrainingRangeResponse
    suspend fun createTraining(ownerUserId: Int, request: CreateTrainingRequest): UserTrainingEntry
    suspend fun updateTraining(ownerUserId: Int, trainingId: Int, request: UpdateTrainingRequest): UserTrainingEntry
    suspend fun deleteTraining(ownerUserId: Int, trainingId: Int)
}
