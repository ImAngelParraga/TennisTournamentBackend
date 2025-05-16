package bros.parraga.db.schema

import bros.parraga.domain.User
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object UsersTable : IntIdTable("users") {
    val username = varchar("username", 255).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex().nullable()
    val createdAt = timestamp("created_at").databaseGenerated().nullable().default(Instant.now())
    val updatedAt = timestamp("updated_at").databaseGenerated().nullable()
}

class UserDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserDAO>(UsersTable)

    var username by UsersTable.username
    var email by UsersTable.email
    val createdAt by UsersTable.createdAt
    var updatedAt by UsersTable.updatedAt

    fun toDomain() = User(
        id.value,
        username,
        email,
        createdAt?.toKotlinInstant(),
        updatedAt?.toKotlinInstant()
    )
}