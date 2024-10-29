package bros.parraga.db.schema

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

// TODO("Add fk to brackets table")
object TournamentsTable : IntIdTable("tournaments") {
    val name = varchar("name", 255).uniqueIndex()
    val description = varchar("description", 255).nullable()
    val surface = varchar("surface", 255).nullable()
    val startDate = long("start_date")
    val endDate = long("end_date")
    val created = long("created").databaseGenerated()
    val modified = long("modified").nullable()
}

class TournamentDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TournamentDAO>(TournamentsTable)

    var name by TournamentsTable.name
    var description by TournamentsTable.description
    var surface by TournamentsTable.surface
    var startDate by TournamentsTable.startDate
    var endDate by TournamentsTable.endDate
    var created by TournamentsTable.created
    var modified by TournamentsTable.modified

    val players by PlayersToTournamentsDAO referrersOn PlayersToTournamentsTable.tournamentId
}