package bros.parraga.db.schema

import bros.parraga.domain.RacketDetails
import bros.parraga.domain.RacketStringingHistoryEntry
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.util.UUID

object RacketsTable : IntIdTable("rackets") {
    val publicToken = uuid("public_token").uniqueIndex()
    val displayName = varchar("display_name", 255)
    val brand = varchar("brand", 255).nullable()
    val model = varchar("model", 255).nullable()
    val stringPattern = varchar("string_pattern", 32).nullable()
    val ownerUserId = reference("owner_user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val ownerName = varchar("owner_name", 255).nullable()
    val createdByUserId = reference("created_by_user_id", UsersTable, onDelete = ReferenceOption.RESTRICT)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").nullable()
}

class RacketDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RacketDAO>(RacketsTable)

    var publicToken by RacketsTable.publicToken
    var displayName by RacketsTable.displayName
    var brand by RacketsTable.brand
    var model by RacketsTable.model
    var stringPattern by RacketsTable.stringPattern
    var ownerUser by UserDAO optionalReferencedOn RacketsTable.ownerUserId
    var ownerName by RacketsTable.ownerName
    var createdByUser by UserDAO referencedOn RacketsTable.createdByUserId
    var createdAt by RacketsTable.createdAt
    var updatedAt by RacketsTable.updatedAt
    val stringings by RacketStringingDAO referrersOn RacketStringingsTable.racketId

    fun toDetails(history: List<RacketStringingHistoryEntry>) = RacketDetails(
        publicToken = publicToken.toString(),
        displayName = displayName,
        brand = brand,
        model = model,
        stringPattern = stringPattern,
        ownerName = ownerName,
        latestStringing = history.firstOrNull(),
        history = history
    )
}

object RacketStringingsTable : IntIdTable("racket_stringings") {
    val racketId = reference("racket_id", RacketsTable, onDelete = ReferenceOption.CASCADE)
    val stringingDate = date("stringing_date")
    val mainsKg = decimal("mains_kg", 6, 2)
    val crossesKg = decimal("crosses_kg", 6, 2)
    val mainsLb = decimal("mains_lb", 6, 2)
    val crossesLb = decimal("crosses_lb", 6, 2)
    val mainStringBrand = varchar("main_string_brand", 255).nullable()
    val mainStringModel = varchar("main_string_model", 255).nullable()
    val mainStringGauge = varchar("main_string_gauge", 64).nullable()
    val crossStringBrand = varchar("cross_string_brand", 255).nullable()
    val crossStringModel = varchar("cross_string_model", 255).nullable()
    val crossStringGauge = varchar("cross_string_gauge", 64).nullable()
    val notes = text("notes").nullable()
    val createdByUserId = reference("created_by_user_id", UsersTable, onDelete = ReferenceOption.RESTRICT)
    val updatedByUserId = reference("updated_by_user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val deletedByUserId = reference("deleted_by_user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").nullable()
    val deletedAt = timestamp("deleted_at").nullable()
}

class RacketStringingDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RacketStringingDAO>(RacketStringingsTable)

    var racket by RacketDAO referencedOn RacketStringingsTable.racketId
    var stringingDate by RacketStringingsTable.stringingDate
    var mainsKg by RacketStringingsTable.mainsKg
    var crossesKg by RacketStringingsTable.crossesKg
    var mainsLb by RacketStringingsTable.mainsLb
    var crossesLb by RacketStringingsTable.crossesLb
    var mainStringBrand by RacketStringingsTable.mainStringBrand
    var mainStringModel by RacketStringingsTable.mainStringModel
    var mainStringGauge by RacketStringingsTable.mainStringGauge
    var crossStringBrand by RacketStringingsTable.crossStringBrand
    var crossStringModel by RacketStringingsTable.crossStringModel
    var crossStringGauge by RacketStringingsTable.crossStringGauge
    var notes by RacketStringingsTable.notes
    var createdByUser by UserDAO referencedOn RacketStringingsTable.createdByUserId
    var updatedByUser by UserDAO optionalReferencedOn RacketStringingsTable.updatedByUserId
    var deletedByUser by UserDAO optionalReferencedOn RacketStringingsTable.deletedByUserId
    var createdAt by RacketStringingsTable.createdAt
    var updatedAt by RacketStringingsTable.updatedAt
    var deletedAt by RacketStringingsTable.deletedAt

    fun toDomain() = RacketStringingHistoryEntry(
        id = id.value,
        stringingDate = stringingDate.toString(),
        mainsKg = mainsKg.toDouble(),
        crossesKg = crossesKg.toDouble(),
        mainsLb = mainsLb.toDouble(),
        crossesLb = crossesLb.toDouble(),
        mainStringBrand = mainStringBrand,
        mainStringModel = mainStringModel,
        mainStringGauge = mainStringGauge,
        crossStringBrand = crossStringBrand,
        crossStringModel = crossStringModel,
        crossStringGauge = crossStringGauge,
        notes = notes,
        stringerUsername = createdByUser.username,
        createdAt = createdAt.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )
}

object RacketStringingAuditsTable : IntIdTable("racket_stringing_audits") {
    val racketId = reference("racket_id", RacketsTable, onDelete = ReferenceOption.CASCADE)
    val racketStringingId = reference("racket_stringing_id", RacketStringingsTable, onDelete = ReferenceOption.CASCADE)
    val action = varchar("action", 16)
    val actorUserId = reference("actor_user_id", UsersTable, onDelete = ReferenceOption.RESTRICT)
    val snapshotJson = text("snapshot_json")
    val occurredAt = timestamp("occurred_at").clientDefault { Instant.now() }
}

enum class RacketStringingAuditAction {
    CREATED,
    UPDATED,
    DELETED
}
