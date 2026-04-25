package bros.parraga.db.schema

import bros.parraga.domain.UserTrainingEntry
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object UserTrainingsTable : IntIdTable("user_trainings") {
    val ownerUserId = reference("owner_user_id", UsersTable, onDelete = ReferenceOption.RESTRICT)
    val trainingDate = date("training_date")
    val durationMinutes = integer("duration_minutes").nullable()
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").nullable()
}

class UserTrainingDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserTrainingDAO>(UserTrainingsTable)

    var ownerUser by UserDAO referencedOn UserTrainingsTable.ownerUserId
    var trainingDate by UserTrainingsTable.trainingDate
    var durationMinutes by UserTrainingsTable.durationMinutes
    var notes by UserTrainingsTable.notes
    var createdAt by UserTrainingsTable.createdAt
    var updatedAt by UserTrainingsTable.updatedAt

    fun toDomain() = UserTrainingEntry(
        id = id.value,
        trainingDate = trainingDate.toString(),
        durationMinutes = durationMinutes,
        notes = notes,
        createdAt = createdAt.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )
}
