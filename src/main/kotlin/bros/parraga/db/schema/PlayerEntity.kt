package bros.parraga.db.schema

import bros.parraga.domain.Player
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
    val tournaments by TournamentDAO via TournamentPlayersTable

    fun toDomain(includeTournaments: Boolean = true): Player =
        Player(
            id.value,
            name,
            external,
            user?.toDomain(),
            if (includeTournaments) tournaments.map { it.toDomain(false) } else emptyList())

}
