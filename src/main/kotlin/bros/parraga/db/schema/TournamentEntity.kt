package bros.parraga.db.schema

import bros.parraga.domain.SurfaceType
import bros.parraga.domain.Tournament
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object TournamentsTable : IntIdTable("tournaments") {
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val surface = varchar("surface", 50).nullable().check { it.inList(SurfaceType.entries.map { it.name }) }
    val clubId = reference("club_id", ClubsTable)
    val startDate = timestamp("start_date")
    val endDate = timestamp("end_date")
    val createdAt = timestamp("created_at").databaseGenerated().default(Instant.now()).nullable()
    val updatedAt = timestamp("updated_at").databaseGenerated().nullable()
}

class TournamentDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TournamentDAO>(TournamentsTable)

    var name by TournamentsTable.name
    var description by TournamentsTable.description
    var surface by TournamentsTable.surface
    var startDate by TournamentsTable.startDate
    var endDate by TournamentsTable.endDate
    var createdAt by TournamentsTable.createdAt
    var updatedAt by TournamentsTable.updatedAt

    var club by ClubDAO referencedOn TournamentsTable.clubId
    val players by PlayerDAO via TournamentPlayersTable

    fun toDomain(includePlayers: Boolean = true) = Tournament(
        id = id.value,
        name = name,
        description = description,
        surface = surface?.let { SurfaceType.valueOf(it) },
        club = club.toDomain(),
        startDate = startDate.toKotlinInstant(),
        endDate = endDate.toKotlinInstant(),
        createdAt = createdAt?.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant(),
        players = if (includePlayers) players.map { it.toDomain(false) } else emptyList()
    )
}

