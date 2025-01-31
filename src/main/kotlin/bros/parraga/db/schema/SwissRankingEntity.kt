package bros.parraga.db.schema

import bros.parraga.domain.SwissRanking
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object SwissRankingsTable : Table("swiss_rankings") {
    val phaseId = reference("phase_id", TournamentPhasesTable, onDelete = ReferenceOption.CASCADE)
    val playerId = reference("player_id", PlayersTable, onDelete = ReferenceOption.CASCADE)
    val round = integer("round")
    val points = integer("points").default(0)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").nullable()

    override val primaryKey = PrimaryKey(phaseId, playerId, round, name = "PK_Swiss_Rankings")
}

class SwissRankingDAO(id: EntityID<CompositeID>) : CompositeEntity(id) {
    var phase by TournamentPhaseDAO referencedOn SwissRankingsTable.phaseId
    var player by PlayerDAO referencedOn SwissRankingsTable.playerId
    var round by SwissRankingsTable.round
    var points by SwissRankingsTable.points
    var createdAt by SwissRankingsTable.createdAt
    var updatedAt by SwissRankingsTable.updatedAt

    fun toDomain() = SwissRanking(
        phase = phase.toDomain(),
        player = player.toDomain(),
        round = round,
        points = points,
        createdAt = createdAt.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )
}
