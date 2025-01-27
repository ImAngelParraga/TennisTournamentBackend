package bros.parraga.db.schema

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object TournamentsTable : IntIdTable("tournaments") {
    val name = varchar("name", 255)
    val description = varchar("description", 255).nullable()
    val surface = varchar("surface", 255).nullable()
    val clubId = reference("club_id", ClubsTable)
    val startDate = timestamp("start_date")
    val endDate = timestamp("end_date")
    val createdAt = timestamp("created_at").databaseGenerated()
    val modifiedAt = timestamp("modified_at").databaseGenerated()
}

class TournamentDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TournamentDAO>(TournamentsTable)

    var name by TournamentsTable.name
    var description by TournamentsTable.description
    var surface by TournamentsTable.surface
    var startDate by TournamentsTable.startDate
    var endDate by TournamentsTable.endDate
    var createdAt by TournamentsTable.createdAt
    var modifiedAt by TournamentsTable.modifiedAt

    var club by ClubDAO referencedOn TournamentsTable.clubId
    val players by PlayerDAO via TournamentPlayersTable
}