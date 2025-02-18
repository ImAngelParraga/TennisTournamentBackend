package bros.parraga.db.schema

import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID

object TournamentPlayersTable : CompositeIdTable("tournament_players") {
    val tournamentId = reference("tournament_id", TournamentsTable)
    val playerId = reference("player_id", PlayersTable.id)

    init {
        addIdColumn(tournamentId)
        addIdColumn(playerId)
    }

    override val primaryKey = PrimaryKey(playerId, tournamentId)
}

class TournamentPlayerDAO(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<TournamentPlayerDAO>(TournamentPlayersTable)

    var player by PlayerDAO referencedOn TournamentPlayersTable.playerId
    var tournament by TournamentDAO referencedOn TournamentPlayersTable.tournamentId
}