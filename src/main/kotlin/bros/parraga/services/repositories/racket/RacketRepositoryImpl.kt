package bros.parraga.services.repositories.racket

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.RacketDAO
import bros.parraga.db.schema.RacketStringingDAO
import bros.parraga.db.schema.RacketStringingsTable
import bros.parraga.db.schema.RacketsTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.RacketDetails
import bros.parraga.domain.RacketStringingHistoryEntry
import bros.parraga.domain.RacketSummary
import bros.parraga.errors.ForbiddenException
import bros.parraga.services.repositories.racket.dto.CreateRacketRequest
import bros.parraga.services.repositories.racket.dto.CreateRacketStringingRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketStringingRequest
import io.ktor.server.plugins.NotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

class RacketRepositoryImpl : RacketRepository {
    override suspend fun getOwnRackets(ownerUserId: Int): List<RacketSummary> = dbQuery {
        RacketDAO.find {
            (RacketsTable.ownerUserId eq ownerUserId) and RacketsTable.deletedAt.isNull()
        }
            .sortedByDescending { it.createdAt }
            .map { racket ->
                val history = activeHistory(racket)
                racket.toSummary(history.firstOrNull())
            }
    }

    override suspend fun getOwnRacket(ownerUserId: Int, racketId: Int): RacketDetails = dbQuery {
        val racket = findOwnedRacket(ownerUserId, racketId)
        racket.toDetails(activeHistory(racket))
    }

    override suspend fun createRacket(ownerUserId: Int, request: CreateRacketRequest): RacketDetails = dbQuery {
        val racket = RacketDAO.new {
            ownerUser = UserDAO[ownerUserId]
            displayName = requireNonBlank(request.displayName, "displayName")
            brand = normalizeOptionalText(request.brand)
            model = normalizeOptionalText(request.model)
            stringPattern = normalizeOptionalText(request.stringPattern)
            visibility = request.visibility.name
            updatedAt = null
            deletedAt = null
        }

        racket.toDetails(emptyList())
    }

    override suspend fun updateRacket(ownerUserId: Int, racketId: Int, request: UpdateRacketRequest): RacketDetails = dbQuery {
        requireRacketUpdatePayload(request)
        val racket = findOwnedRacket(ownerUserId, racketId)

        request.displayName?.let { racket.displayName = requireNonBlank(it, "displayName") }
        if (request.brand != null) {
            racket.brand = normalizeOptionalText(request.brand)
        }
        if (request.model != null) {
            racket.model = normalizeOptionalText(request.model)
        }
        if (request.stringPattern != null) {
            racket.stringPattern = normalizeOptionalText(request.stringPattern)
        }
        request.visibility?.let { racket.visibility = it.name }
        racket.updatedAt = Instant.now()

        racket.toDetails(activeHistory(racket))
    }

    override suspend fun deleteRacket(ownerUserId: Int, racketId: Int) = dbQuery {
        val racket = findOwnedRacket(ownerUserId, racketId)
        val deletedAt = Instant.now()

        racket.deletedAt = deletedAt
        racket.updatedAt = deletedAt
        racket.stringings
            .filter { it.deletedAt == null }
            .forEach { stringing ->
                stringing.deletedAt = deletedAt
                stringing.updatedAt = deletedAt
            }
    }

    override suspend fun createStringing(
        ownerUserId: Int,
        racketId: Int,
        request: CreateRacketStringingRequest
    ): RacketDetails = dbQuery {
        val racket = findOwnedRacket(ownerUserId, racketId)

        RacketStringingDAO.new {
            this.racket = racket
            stringingDate = parseStringingDate(request.stringingDate)
            mainsTensionKg = toScaledKilograms(request.mainsTensionKg, "mainsTensionKg")
            crossesTensionKg = toScaledKilograms(request.crossesTensionKg, "crossesTensionKg")
            mainStringType = normalizeOptionalText(request.mainStringType)
            crossStringType = normalizeOptionalText(request.crossStringType)
            performanceNotes = normalizeOptionalText(request.performanceNotes)
            updatedAt = null
            deletedAt = null
        }

        racket.toDetails(activeHistory(racket))
    }

    override suspend fun updateStringing(
        ownerUserId: Int,
        racketId: Int,
        stringingId: Int,
        request: UpdateRacketStringingRequest
    ): RacketDetails = dbQuery {
        requireStringingUpdatePayload(request)
        val stringing = findOwnedStringing(ownerUserId, racketId, stringingId)

        request.stringingDate?.let { stringing.stringingDate = parseStringingDate(it) }
        request.mainsTensionKg?.let { stringing.mainsTensionKg = toScaledKilograms(it, "mainsTensionKg") }
        request.crossesTensionKg?.let { stringing.crossesTensionKg = toScaledKilograms(it, "crossesTensionKg") }
        if (request.mainStringType != null) {
            stringing.mainStringType = normalizeOptionalText(request.mainStringType)
        }
        if (request.crossStringType != null) {
            stringing.crossStringType = normalizeOptionalText(request.crossStringType)
        }
        if (request.performanceNotes != null) {
            stringing.performanceNotes = normalizeOptionalText(request.performanceNotes)
        }
        stringing.updatedAt = Instant.now()

        stringing.racket.toDetails(activeHistory(stringing.racket))
    }

    override suspend fun deleteStringing(ownerUserId: Int, racketId: Int, stringingId: Int) = dbQuery {
        val stringing = findOwnedStringing(ownerUserId, racketId, stringingId)
        val deletedAt = Instant.now()
        stringing.deletedAt = deletedAt
        stringing.updatedAt = deletedAt
    }

    override suspend fun getPublicRackets(userId: Int): List<RacketSummary> = dbQuery {
        RacketDAO.find {
            (RacketsTable.ownerUserId eq userId) and
                (RacketsTable.visibility eq "PUBLIC") and
                RacketsTable.deletedAt.isNull()
        }
            .sortedByDescending { it.createdAt }
            .map { racket ->
                val history = activeHistory(racket)
                racket.toSummary(history.firstOrNull())
            }
    }

    override suspend fun getPublicRacket(userId: Int, racketId: Int): RacketDetails = dbQuery {
        val racket = findVisibleRacket(userId, racketId)
        racket.toDetails(activeHistory(racket))
    }

    private fun findOwnedRacket(ownerUserId: Int, racketId: Int): RacketDAO {
        val racket = RacketDAO.findById(racketId) ?: throw NotFoundException("Racket not found")
        if (racket.deletedAt != null) {
            throw NotFoundException("Racket not found")
        }
        if (racket.ownerUser.id.value != ownerUserId) {
            throw ForbiddenException("You can only manage your own rackets")
        }
        return racket
    }

    private fun findVisibleRacket(userId: Int, racketId: Int): RacketDAO {
        val racket = RacketDAO.findById(racketId) ?: throw NotFoundException("Racket not found")
        if (racket.deletedAt != null || racket.ownerUser.id.value != userId || racket.visibility != "PUBLIC") {
            throw NotFoundException("Racket not found")
        }
        return racket
    }

    private fun findOwnedStringing(ownerUserId: Int, racketId: Int, stringingId: Int): RacketStringingDAO {
        val racket = findOwnedRacket(ownerUserId, racketId)
        val stringing = RacketStringingDAO.findById(stringingId) ?: throw NotFoundException("Stringing record not found")
        if (stringing.deletedAt != null || stringing.racket.id.value != racket.id.value) {
            throw NotFoundException("Stringing record not found")
        }
        return stringing
    }

    private fun activeHistory(racket: RacketDAO): List<RacketStringingHistoryEntry> = racket.stringings
        .filter { it.deletedAt == null }
        .sortedWith(compareByDescending<RacketStringingDAO> { it.stringingDate }.thenByDescending { it.createdAt })
        .map { it.toDomain() }

    private fun requireRacketUpdatePayload(request: UpdateRacketRequest) {
        if (
            request.displayName == null &&
            request.brand == null &&
            request.model == null &&
            request.stringPattern == null &&
            request.visibility == null
        ) {
            throw IllegalArgumentException("At least one racket field must be provided")
        }
    }

    private fun requireStringingUpdatePayload(request: UpdateRacketStringingRequest) {
        if (
            request.stringingDate == null &&
            request.mainsTensionKg == null &&
            request.crossesTensionKg == null &&
            request.mainStringType == null &&
            request.crossStringType == null &&
            request.performanceNotes == null
        ) {
            throw IllegalArgumentException("At least one stringing field must be provided")
        }
    }

    private fun requireNonBlank(value: String, fieldName: String): String =
        value.trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("$fieldName must not be blank")

    private fun normalizeOptionalText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseStringingDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        throw IllegalArgumentException("stringingDate must use ISO format YYYY-MM-DD")
    }

    private fun toScaledKilograms(value: Double, fieldName: String): BigDecimal {
        if (value <= 0.0) {
            throw IllegalArgumentException("$fieldName must be greater than 0")
        }

        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
    }
}
