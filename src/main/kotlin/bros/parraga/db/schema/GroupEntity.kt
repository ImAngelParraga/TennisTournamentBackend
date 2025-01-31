package bros.parraga.db.schema

import bros.parraga.domain.Group
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant


object GroupsTable : IntIdTable("groups") {
    val phaseId = reference("phase_id", TournamentPhasesTable)
    val name = varchar("name", 10)
    val createdAt = timestamp("created_at").databaseGenerated().default(Instant.now()).nullable()
    val updatedAt = timestamp("updated_at").databaseGenerated().nullable()
}

class GroupDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GroupDAO>(GroupsTable)

    var phase by TournamentPhaseDAO referencedOn GroupsTable.phaseId
    var name by GroupsTable.name
    var createdAt by GroupsTable.createdAt
    var updatedAt by GroupsTable.updatedAt

    fun toDomain() = Group(
        id = id.value,
        phase = phase.toDomain(),
        name = name,
        createdAt = createdAt?.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )
}