package bros.parraga.db.schema

import bros.parraga.domain.Club
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object ClubsTable : IntIdTable("clubs") {
    val userId = reference("user_id", UsersTable)
    val name = varchar("name", 255)
    val phoneNumber = varchar("phone_number", 20).nullable()
    val address = text("address").nullable()
}

class ClubDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ClubDAO>(ClubsTable)

    var name by ClubsTable.name
    var phoneNumber by ClubsTable.phoneNumber
    var address by ClubsTable.address

    var user by UserDAO referencedOn ClubsTable.userId
    val tournaments by TournamentDAO referrersOn TournamentsTable.clubId

    fun toDomain(): Club = Club(
        id = id.value,
        name = name,
        phoneNumber = phoneNumber,
        address = address,
        user = user.toDomain(),
        tournaments = tournaments.map { it.toDomain() }
    )
}
