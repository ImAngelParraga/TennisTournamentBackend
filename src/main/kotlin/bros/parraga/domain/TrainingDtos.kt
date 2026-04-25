package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserTrainingEntry(
    val id: Int,
    val trainingDate: String,
    val durationMinutes: Int?,
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
data class UserTrainingRangeResponse(
    val userId: Int,
    val from: String,
    val to: String,
    val calendarDays: List<UserTrainingCalendarDay>,
    val trainings: List<UserTrainingEntry>
)
