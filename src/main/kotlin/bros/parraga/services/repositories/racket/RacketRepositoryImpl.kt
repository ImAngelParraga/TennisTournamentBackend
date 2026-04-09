package bros.parraga.services.repositories.racket

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.RacketDAO
import bros.parraga.db.schema.RacketStringingAuditAction
import bros.parraga.db.schema.RacketStringingAuditsTable
import bros.parraga.db.schema.RacketStringingDAO
import bros.parraga.db.schema.RacketsTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.RacketDetails
import bros.parraga.errors.ConflictException
import bros.parraga.errors.ForbiddenException
import bros.parraga.services.repositories.racket.dto.CreateRacketStringingRequest
import bros.parraga.services.repositories.racket.dto.NewRacketRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketStringingRequest
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RacketRepositoryImpl : RacketRepository {
    override suspend fun getPublicRacket(publicToken: String): RacketDetails = dbQuery {
        val racket = findRacketByPublicToken(publicToken)
        racket.toDetails(activeHistory(racket))
    }

    override suspend fun createStringing(userId: Int, request: CreateRacketStringingRequest): RacketDetails = dbQuery {
        val racket = when {
            request.racketPublicToken != null && request.newRacket != null ->
                throw IllegalArgumentException("Provide either racketPublicToken or newRacket, not both")

            request.racketPublicToken == null && request.newRacket == null ->
                throw IllegalArgumentException("Either racketPublicToken or newRacket is required")

            request.racketPublicToken != null -> {
                val existingRacket = findRacketByPublicToken(request.racketPublicToken)
                requireRacketEditor(existingRacket, userId)
                existingRacket
            }

            else -> createRacket(userId, requireNotNull(request.newRacket))
        }

        val stringing = createStringingRecord(racket, userId, request)
        auditStringing(stringing, userId, RacketStringingAuditAction.CREATED)
        racket.toDetails(activeHistory(racket))
    }

    override suspend fun updateRacket(userId: Int, publicToken: String, request: UpdateRacketRequest): RacketDetails = dbQuery {
        requireRacketUpdatePayload(request)
        val racket = findRacketByPublicToken(publicToken)
        requireRacketEditor(racket, userId)

        request.displayName?.let {
            racket.displayName = requireNonBlank(it, "displayName")
        }
        if (request.brand != null) {
            racket.brand = normalizeOptionalText(request.brand)
        }
        if (request.model != null) {
            racket.model = normalizeOptionalText(request.model)
        }
        if (request.stringPattern != null) {
            racket.stringPattern = normalizeOptionalText(request.stringPattern)
        }
        if (request.ownerName != null) {
            racket.ownerName = normalizeOptionalText(request.ownerName)
        }
        request.ownerUserId?.let { ownerUserId ->
            racket.ownerUser = UserDAO.findById(ownerUserId)
                ?: throw NotFoundException("Owner user not found")
        }
        racket.updatedAt = Instant.now()

        racket.toDetails(activeHistory(racket))
    }

    override suspend fun updateStringing(
        userId: Int,
        stringingId: Int,
        request: UpdateRacketStringingRequest
    ): RacketDetails = dbQuery {
        requireStringingUpdatePayload(request)
        val stringing = findActiveStringing(stringingId)
        requireRacketEditor(stringing.racket, userId)

        request.stringingDate?.let { stringing.stringingDate = parseStringingDate(it) }
        request.mainsKg?.let { stringing.mainsKg = toScaledKilograms(it, "mainsKg") }
        request.crossesKg?.let { stringing.crossesKg = toScaledKilograms(it, "crossesKg") }
        stringing.mainsLb = kilogramsToPounds(stringing.mainsKg)
        stringing.crossesLb = kilogramsToPounds(stringing.crossesKg)

        if (request.mainStringBrand != null) {
            stringing.mainStringBrand = normalizeOptionalText(request.mainStringBrand)
        }
        if (request.mainStringModel != null) {
            stringing.mainStringModel = normalizeOptionalText(request.mainStringModel)
        }
        if (request.mainStringGauge != null) {
            stringing.mainStringGauge = normalizeOptionalText(request.mainStringGauge)
        }
        if (request.crossStringBrand != null) {
            stringing.crossStringBrand = normalizeOptionalText(request.crossStringBrand)
        }
        if (request.crossStringModel != null) {
            stringing.crossStringModel = normalizeOptionalText(request.crossStringModel)
        }
        if (request.crossStringGauge != null) {
            stringing.crossStringGauge = normalizeOptionalText(request.crossStringGauge)
        }
        if (request.notes != null) {
            stringing.notes = normalizeOptionalText(request.notes)
        }
        stringing.updatedByUser = UserDAO[userId]
        stringing.updatedAt = Instant.now()

        auditStringing(stringing, userId, RacketStringingAuditAction.UPDATED)
        stringing.racket.toDetails(activeHistory(stringing.racket))
    }

    override suspend fun deleteStringing(userId: Int, stringingId: Int) = dbQuery {
        val stringing = findActiveStringing(stringingId)
        requireRacketEditor(stringing.racket, userId)

        stringing.deletedByUser = UserDAO[userId]
        stringing.deletedAt = Instant.now()
        stringing.updatedAt = stringing.deletedAt
        stringing.updatedByUser = UserDAO[userId]

        auditStringing(stringing, userId, RacketStringingAuditAction.DELETED)
    }

    private fun createRacket(userId: Int, request: NewRacketRequest): RacketDAO {
        val ownerUser = request.ownerUserId?.let {
            UserDAO.findById(it) ?: throw NotFoundException("Owner user not found")
        }

        return RacketDAO.new {
            publicToken = UUID.randomUUID()
            displayName = requireNonBlank(request.displayName, "displayName")
            brand = normalizeOptionalText(request.brand)
            model = normalizeOptionalText(request.model)
            stringPattern = normalizeOptionalText(request.stringPattern)
            ownerName = normalizeOptionalText(request.ownerName)
            this.ownerUser = ownerUser
            createdByUser = UserDAO[userId]
            updatedAt = null
        }
    }

    private fun createStringingRecord(
        racket: RacketDAO,
        userId: Int,
        request: CreateRacketStringingRequest
    ): RacketStringingDAO =
        RacketStringingDAO.new {
            this.racket = racket
            stringingDate = parseStringingDate(request.stringingDate)
            mainsKg = toScaledKilograms(request.mainsKg, "mainsKg")
            crossesKg = toScaledKilograms(request.crossesKg, "crossesKg")
            mainsLb = kilogramsToPounds(mainsKg)
            crossesLb = kilogramsToPounds(crossesKg)
            mainStringBrand = normalizeOptionalText(request.mainStringBrand)
            mainStringModel = normalizeOptionalText(request.mainStringModel)
            mainStringGauge = normalizeOptionalText(request.mainStringGauge)
            crossStringBrand = normalizeOptionalText(request.crossStringBrand)
            crossStringModel = normalizeOptionalText(request.crossStringModel)
            crossStringGauge = normalizeOptionalText(request.crossStringGauge)
            notes = normalizeOptionalText(request.notes)
            createdByUser = UserDAO[userId]
            updatedByUser = null
            deletedByUser = null
            updatedAt = null
            deletedAt = null
        }

    private fun findRacketByPublicToken(publicToken: String): RacketDAO {
        val token = try {
            UUID.fromString(publicToken)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("publicToken must be a valid UUID")
        }

        return RacketDAO.find { RacketsTable.publicToken eq token }.firstOrNull()
            ?: throw NotFoundException("Racket not found")
    }

    private fun findActiveStringing(stringingId: Int): RacketStringingDAO {
        val stringing = RacketStringingDAO.findById(stringingId)
            ?: throw NotFoundException("Stringing record not found")
        if (stringing.deletedAt != null) {
            throw ConflictException("Stringing record has already been deleted")
        }
        return stringing
    }

    private fun activeHistory(racket: RacketDAO) = racket.stringings
        .filter { it.deletedAt == null }
        .sortedWith(compareByDescending<RacketStringingDAO> { it.stringingDate }.thenByDescending { it.createdAt })
        .map { it.toDomain() }

    private fun requireRacketEditor(racket: RacketDAO, userId: Int) {
        val isCreator = racket.createdByUser.id.value == userId
        val isOwner = racket.ownerUser?.id?.value == userId
        if (!isCreator && !isOwner) {
            throw ForbiddenException("Only the racket creator or owner can modify this racket")
        }
    }

    private fun requireRacketUpdatePayload(request: UpdateRacketRequest) {
        if (
            request.displayName == null &&
            request.brand == null &&
            request.model == null &&
            request.stringPattern == null &&
            request.ownerName == null &&
            request.ownerUserId == null
        ) {
            throw IllegalArgumentException("At least one racket field must be provided")
        }
    }

    private fun requireStringingUpdatePayload(request: UpdateRacketStringingRequest) {
        if (
            request.stringingDate == null &&
            request.mainsKg == null &&
            request.crossesKg == null &&
            request.mainStringBrand == null &&
            request.mainStringModel == null &&
            request.mainStringGauge == null &&
            request.crossStringBrand == null &&
            request.crossStringModel == null &&
            request.crossStringGauge == null &&
            request.notes == null
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

    private fun kilogramsToPounds(value: BigDecimal): BigDecimal =
        value.multiply(KILOGRAM_TO_POUND_FACTOR).setScale(2, RoundingMode.HALF_UP)

    private fun auditStringing(
        stringing: RacketStringingDAO,
        actorUserId: Int,
        action: RacketStringingAuditAction
    ) {
        val snapshot = RacketStringingAuditSnapshot(
            id = stringing.id.value,
            racketId = stringing.racket.id.value,
            stringingDate = stringing.stringingDate.toString(),
            mainsKg = stringing.mainsKg.toDouble(),
            crossesKg = stringing.crossesKg.toDouble(),
            mainsLb = stringing.mainsLb.toDouble(),
            crossesLb = stringing.crossesLb.toDouble(),
            mainStringBrand = stringing.mainStringBrand,
            mainStringModel = stringing.mainStringModel,
            mainStringGauge = stringing.mainStringGauge,
            crossStringBrand = stringing.crossStringBrand,
            crossStringModel = stringing.crossStringModel,
            crossStringGauge = stringing.crossStringGauge,
            notes = stringing.notes,
            createdByUserId = stringing.createdByUser.id.value,
            updatedByUserId = stringing.updatedByUser?.id?.value,
            deletedByUserId = stringing.deletedByUser?.id?.value,
            createdAt = stringing.createdAt.toString(),
            updatedAt = stringing.updatedAt?.toString(),
            deletedAt = stringing.deletedAt?.toString()
        )

        RacketStringingAuditsTable.insert {
            it[RacketStringingAuditsTable.racketId] = stringing.racket.id
            it[RacketStringingAuditsTable.racketStringingId] = stringing.id
            it[RacketStringingAuditsTable.action] = action.name
            it[RacketStringingAuditsTable.actorUserId] = UserDAO[actorUserId].id
            it[RacketStringingAuditsTable.snapshotJson] = Json.encodeToString(snapshot)
        }
    }

    @Serializable
    private data class RacketStringingAuditSnapshot(
        val id: Int,
        val racketId: Int,
        val stringingDate: String,
        val mainsKg: Double,
        val crossesKg: Double,
        val mainsLb: Double,
        val crossesLb: Double,
        val mainStringBrand: String?,
        val mainStringModel: String?,
        val mainStringGauge: String?,
        val crossStringBrand: String?,
        val crossStringModel: String?,
        val crossStringGauge: String?,
        val notes: String?,
        val createdByUserId: Int,
        val updatedByUserId: Int?,
        val deletedByUserId: Int?,
        val createdAt: String,
        val updatedAt: String?,
        val deletedAt: String?
    )

    private companion object {
        val KILOGRAM_TO_POUND_FACTOR = BigDecimal("2.20462")
    }
}
