package bros.parraga.db.schema

import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID

object PlayersToTournamentsTable : CompositeIdTable("players_to_tournaments") {
    val playerName = varchar("player_name", 255)
    val tournamentId = reference("tournament_id", TournamentsTable)

    init {
        addIdColumn(tournamentId)
    }

    override val primaryKey = PrimaryKey(playerName, tournamentId)
}

class PlayersToTournamentsDAO(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<PlayersToTournamentsDAO>(PlayersToTournamentsTable)

    val playerName by PlayersToTournamentsTable.playerName
    val tournamentId by PlayersToTournamentsTable.tournamentId
}