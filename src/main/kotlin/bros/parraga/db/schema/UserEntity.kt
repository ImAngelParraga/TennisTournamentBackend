package bros.parraga.db.schema

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object UsersTable : IntIdTable("users") {
    val username = varchar("username", 255).uniqueIndex()
    val password = varchar("password", 255)
    val email = varchar("email", 255).uniqueIndex().nullable()
    val createdAt = timestamp("created_at").databaseGenerated()
    val updatedAt = timestamp("updated_at").databaseGenerated().nullable()
}

class UserDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserDAO>(UsersTable)

    var username by UsersTable.username
    var password by UsersTable.password
    var email by UsersTable.email
    var createdAt by UsersTable.createdAt
    var updatedAt by UsersTable.updatedAt
}