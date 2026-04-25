package bros.parraga.services.repositories.training

import bros.parraga.domain.UserTrainingEntry
import bros.parraga.domain.UserTrainingMonthResponse
import bros.parraga.services.repositories.training.dto.CreateTrainingRequest
import bros.parraga.services.repositories.training.dto.UpdateTrainingRequest
import java.time.YearMonth

interface TrainingRepository {
    suspend fun getOwnTrainingMonth(ownerUserId: Int, month: YearMonth): UserTrainingMonthResponse
    suspend fun createTraining(ownerUserId: Int, request: CreateTrainingRequest): UserTrainingEntry
    suspend fun updateTraining(ownerUserId: Int, trainingId: Int, request: UpdateTrainingRequest): UserTrainingEntry
    suspend fun deleteTraining(ownerUserId: Int, trainingId: Int)
}
