package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserTrainingEntry(
    val id: Int,
    val trainingDate: String,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant?
)

@Serializable
data class UserTrainingCalendarDay(
    val date: String,
    val trainingCount: Int
)

@Serializable
data class UserTrainingMonthResponse(
    val userId: Int,
    val month: String,
    val calendarDays: List<UserTrainingCalendarDay>,
    val trainings: List<UserTrainingEntry>
)
