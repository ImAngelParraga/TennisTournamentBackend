package bros.parraga.db.schema

import bros.parraga.domain.RacketDetails
import bros.parraga.domain.RacketStringingHistoryEntry
import bros.parraga.domain.RacketSummary
import bros.parraga.domain.RacketVisibility
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object RacketsTable : IntIdTable("rackets") {
    val ownerUserId = reference("owner_user_id", UsersTable, onDelete = ReferenceOption.RESTRICT)
    val displayName = varchar("display_name", 255)
    val brand = varchar("brand", 255).nullable()
    val model = varchar("model", 255).nullable()
    val stringPattern = varchar("string_pattern", 64).nullable()
    val visibility = varchar("visibility", 16).default(RacketVisibility.PRIVATE.name)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").nullable()
    val deletedAt = timestamp("deleted_at").nullable()
}

class RacketDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RacketDAO>(RacketsTable)

    var ownerUser by UserDAO referencedOn RacketsTable.ownerUserId
    var displayName by RacketsTable.displayName
    var brand by RacketsTable.brand
    var model by RacketsTable.model
    var stringPattern by RacketsTable.stringPattern
    var visibility by RacketsTable.visibility
    var createdAt by RacketsTable.createdAt
    var updatedAt by RacketsTable.updatedAt
    var deletedAt by RacketsTable.deletedAt
    val stringings by RacketStringingDAO referrersOn RacketStringingsTable.racketId

    fun toSummary(latestStringing: RacketStringingHistoryEntry?) = RacketSummary(
        id = id.value,
        displayName = displayName,
        brand = brand,
        model = model,
        stringPattern = stringPattern,
        visibility = RacketVisibility.valueOf(visibility),
        latestStringing = latestStringing,
        createdAt = createdAt.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )

    fun toDetails(history: List<RacketStringingHistoryEntry>) = RacketDetails(
        id = id.value,
        displayName = displayName,
        brand = brand,
        model = model,
        stringPattern = stringPattern,
        visibility = RacketVisibility.valueOf(visibility),
        latestStringing = history.firstOrNull(),
        history = history,
        createdAt = createdAt.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )
}

object RacketStringingsTable : IntIdTable("racket_stringings") {
    val racketId = reference("racket_id", RacketsTable, onDelete = ReferenceOption.RESTRICT)
    val stringingDate = date("stringing_date")
    val mainsTensionKg = decimal("mains_tension_kg", 6, 2)
    val crossesTensionKg = decimal("crosses_tension_kg", 6, 2)
    val mainStringType = varchar("main_string_type", 255).nullable()
    val crossStringType = varchar("cross_string_type", 255).nullable()
    val performanceNotes = text("performance_notes").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").nullable()
    val deletedAt = timestamp("deleted_at").nullable()
}

class RacketStringingDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RacketStringingDAO>(RacketStringingsTable)

    var racket by RacketDAO referencedOn RacketStringingsTable.racketId
    var stringingDate by RacketStringingsTable.stringingDate
    var mainsTensionKg by RacketStringingsTable.mainsTensionKg
    var crossesTensionKg by RacketStringingsTable.crossesTensionKg
    var mainStringType by RacketStringingsTable.mainStringType
    var crossStringType by RacketStringingsTable.crossStringType
    var performanceNotes by RacketStringingsTable.performanceNotes
    var createdAt by RacketStringingsTable.createdAt
    var updatedAt by RacketStringingsTable.updatedAt
    var deletedAt by RacketStringingsTable.deletedAt

    fun toDomain() = RacketStringingHistoryEntry(
        id = id.value,
        stringingDate = stringingDate.toString(),
        mainsTensionKg = mainsTensionKg.toDouble(),
        crossesTensionKg = crossesTensionKg.toDouble(),
        mainStringType = mainStringType,
        crossStringType = crossStringType,
        performanceNotes = performanceNotes,
        createdAt = createdAt.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )
}
