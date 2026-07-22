package bros.parraga.db.schema

import bros.parraga.domain.SurfaceType
import bros.parraga.domain.Tournament
import bros.parraga.domain.TournamentBasic
import bros.parraga.domain.TournamentStatus
import bros.parraga.domain.TournamentVisibility
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
    val status = varchar("status", 20).check { it.inList(TournamentStatus.entries.map { it.name }) }.default("DRAFT")
    val clubId = reference("club_id", ClubsTable).nullable()
    val ownerUserId = reference("owner_user_id", UsersTable).nullable()
    val visibility = varchar("visibility", 10).check { it.inList(TournamentVisibility.entries.map { it.name }) }
        .default(TournamentVisibility.PUBLIC.name)
    val inviteCode = varchar("invite_code", 8).nullable().uniqueIndex()
    val championPlayerId = reference("champion_player_id", PlayersTable).nullable()
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
    var status by TournamentsTable.status
    var startDate by TournamentsTable.startDate
    var endDate by TournamentsTable.endDate
    var visibility by TournamentsTable.visibility
    var inviteCode by TournamentsTable.inviteCode
    var createdAt by TournamentsTable.createdAt
    var updatedAt by TournamentsTable.updatedAt

    var club by ClubDAO optionalReferencedOn TournamentsTable.clubId
    var owner by UserDAO optionalReferencedOn TournamentsTable.ownerUserId
    var champion by PlayerDAO optionalReferencedOn TournamentsTable.championPlayerId
    var players by PlayerDAO via TournamentPlayersTable
    val phases by TournamentPhaseDAO referrersOn TournamentPhasesTable.tournamentId

    fun toBasic() = TournamentBasic(
        id = id.value,
        name = name,
        description = description,
        surface = surface?.let { SurfaceType.valueOf(it) },
        status = TournamentStatus.valueOf(status),
        clubId = club?.id?.value,
        ownerUserId = owner?.id?.value,
        visibility = TournamentVisibility.valueOf(visibility),
        inviteCode = inviteCode,
        startDate = startDate.toKotlinInstant(),
        endDate = endDate.toKotlinInstant(),
        createdAt = createdAt?.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )

    fun toDomain() = Tournament(
        id = id.value,
        name = name,
        description = description,
        surface = surface?.let { SurfaceType.valueOf(it) },
        status = TournamentStatus.valueOf(status),
        clubId = club?.id?.value,
        ownerUserId = owner?.id?.value,
        visibility = TournamentVisibility.valueOf(visibility),
        inviteCode = inviteCode,
        startDate = startDate.toKotlinInstant(),
        endDate = endDate.toKotlinInstant(),
        createdAt = createdAt?.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant(),
        players = players.map { it.toDomain() },
        phases = phases.map { it.toDomain() }
    )
}

