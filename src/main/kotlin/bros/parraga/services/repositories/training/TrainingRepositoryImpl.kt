package bros.parraga.services.repositories.training

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UserTrainingDAO
import bros.parraga.db.schema.UserTrainingsTable
import bros.parraga.domain.UserTrainingCalendarDay
import bros.parraga.domain.UserTrainingEntry
import bros.parraga.domain.UserTrainingMonthResponse
import bros.parraga.errors.ForbiddenException
import bros.parraga.services.repositories.training.dto.CreateTrainingRequest
import bros.parraga.services.repositories.training.dto.UpdateTrainingRequest
import io.ktor.server.plugins.NotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class TrainingRepositoryImpl : TrainingRepository {
    override suspend fun getOwnTrainingMonth(ownerUserId: Int, month: YearMonth): UserTrainingMonthResponse = dbQuery {
        UserDAO[ownerUserId]

        val trainings = UserTrainingDAO.find {
            (UserTrainingsTable.ownerUserId eq ownerUserId) and
                (UserTrainingsTable.trainingDate greaterEq month.atDay(1)) and
                (UserTrainingsTable.trainingDate lessEq month.atEndOfMonth())
        }
            .sortedWith(compareByDescending<UserTrainingDAO> { it.trainingDate }.thenByDescending { it.createdAt })
            .map { it.toDomain() }

        UserTrainingMonthResponse(
            userId = ownerUserId,
            month = month.toString(),
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

    override suspend fun createTraining(ownerUserId: Int, request: CreateTrainingRequest): UserTrainingEntry = dbQuery {
        UserTrainingDAO.new {
            ownerUser = UserDAO[ownerUserId]
            trainingDate = parseTrainingDate(request.trainingDate)
            notes = normalizeOptionalText(request.notes)
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
        if (request.notes != null) {
            training.notes = normalizeOptionalText(request.notes)
        }
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
        if (request.trainingDate == null && request.notes == null) {
            throw IllegalArgumentException("At least one training field must be provided")
        }
    }

    private fun parseTrainingDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        throw IllegalArgumentException("trainingDate must use ISO format YYYY-MM-DD")
    }

    private fun normalizeOptionalText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
