package bros.parraga.services.repositories.training

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UserTrainingDAO
import bros.parraga.db.schema.UserTrainingsTable
import bros.parraga.domain.TrainingVisibility
import bros.parraga.domain.UserTrainingCalendarDay
import bros.parraga.domain.UserTrainingEntry
import bros.parraga.domain.UserTrainingRangeResponse
import bros.parraga.errors.ForbiddenException
import bros.parraga.services.repositories.training.dto.CreateTrainingRequest
import bros.parraga.services.repositories.training.dto.UpdateTrainingRequest
import io.ktor.server.plugins.NotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import java.time.Instant
import java.time.LocalDate

class TrainingRepositoryImpl : TrainingRepository {
    override suspend fun getOwnTrainingRange(ownerUserId: Int, from: LocalDate, to: LocalDate): UserTrainingRangeResponse = dbQuery {
        getTrainingRange(ownerUserId, from, to, includePrivateTrainings = true)
    }

    override suspend fun getPublicTrainingRange(userId: Int, from: LocalDate, to: LocalDate): UserTrainingRangeResponse = dbQuery {
        getTrainingRange(userId, from, to, includePrivateTrainings = false)
    }

    override suspend fun createTraining(ownerUserId: Int, request: CreateTrainingRequest): UserTrainingEntry = dbQuery {
        UserTrainingDAO.new {
            ownerUser = UserDAO[ownerUserId]
            trainingDate = parseTrainingDate(request.trainingDate)
            durationMinutes = validateDurationMinutes(request.durationMinutes)
            notes = normalizeOptionalText(request.notes)
            visibility = request.visibility.name
            updatedAt = null
        }.toDomain()
    }

    override suspend fun updateTraining(
        ownerUserId: Int,
        trainingId: Int,
        request: UpdateTrainingRequest
    ): UserTrainingEntry = dbQuery {
        requireUpdatePayload(request)
        val training = findOwnedTraining(ownerUserId, trainingId)

        request.trainingDate?.let { training.trainingDate = parseTrainingDate(it) }
        request.durationMinutes?.let { training.durationMinutes = validateDurationMinutes(it) }
        if (request.notes != null) {
            training.notes = normalizeOptionalText(request.notes)
        }
        request.visibility?.let { training.visibility = it.name }
        training.updatedAt = Instant.now()

        training.toDomain()
    }

    override suspend fun deleteTraining(ownerUserId: Int, trainingId: Int) = dbQuery {
        findOwnedTraining(ownerUserId, trainingId).delete()
    }

    private fun findOwnedTraining(ownerUserId: Int, trainingId: Int): UserTrainingDAO {
        val training = UserTrainingDAO.findById(trainingId) ?: throw NotFoundException("Training not found")
        if (training.ownerUser.id.value != ownerUserId) {
            throw ForbiddenException("You can only manage your own trainings")
        }
        return training
    }

    private fun requireUpdatePayload(request: UpdateTrainingRequest) {
        if (
            request.trainingDate == null &&
            request.durationMinutes == null &&
            request.notes == null &&
            request.visibility == null
        ) {
            throw IllegalArgumentException("At least one training field must be provided")
        }
    }

    private fun getTrainingRange(
        userId: Int,
        from: LocalDate,
        to: LocalDate,
        includePrivateTrainings: Boolean
    ): UserTrainingRangeResponse {
        UserDAO[userId]

        val baseCondition =
            (UserTrainingsTable.ownerUserId eq userId) and
                (UserTrainingsTable.trainingDate greaterEq from) and
                (UserTrainingsTable.trainingDate lessEq to)

        val trainings = if (includePrivateTrainings) {
            UserTrainingDAO.find { baseCondition }
        } else {
            UserTrainingDAO.find { baseCondition and (UserTrainingsTable.visibility eq TrainingVisibility.PUBLIC.name) }
        }
            .sortedWith(compareByDescending<UserTrainingDAO> { it.trainingDate }.thenByDescending { it.createdAt })
            .map { it.toDomain() }

        return UserTrainingRangeResponse(
            userId = userId,
            from = from.toString(),
            to = to.toString(),
            calendarDays = trainings
                .groupingBy { it.trainingDate }
                .eachCount()
                .entries
                .sortedBy { it.key }
                .map { (date, count) ->
                    UserTrainingCalendarDay(
                        date = date,
                        trainingCount = count
                    )
                },
            trainings = trainings
        )
    }

    private fun parseTrainingDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        throw IllegalArgumentException("trainingDate must use ISO format YYYY-MM-DD")
    }

    private fun validateDurationMinutes(value: Int?): Int? {
        if (value == null) return null
        require(value > 0) { "durationMinutes must be greater than 0" }
        return value
    }

    private fun normalizeOptionalText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
