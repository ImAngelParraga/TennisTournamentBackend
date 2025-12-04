package bros.parraga.db.schema

import bros.parraga.domain.Player
import bros.parraga.domain.PublicUser
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object PlayersTable : IntIdTable("players") {
    val userId = reference("user_id", UsersTable).uniqueIndex().nullable()
    val name = varchar("name", 255)
    val external = bool("external").default(false)
}

class PlayerDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PlayerDAO>(PlayersTable)

    var name by PlayersTable.name
    var external by PlayersTable.external

    var user by UserDAO optionalReferencedOn PlayersTable.userId

    fun toDomain(): Player =
        Player(
            id.value,
            name,
            external,
            user?.let { PublicUser(it.id.value, it.username) }
        )
}
