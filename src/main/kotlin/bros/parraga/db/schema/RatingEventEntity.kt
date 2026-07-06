package bros.parraga.db.schema

import bros.parraga.domain.RatingEvent
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant as JavaInstant

object RatingEventsTable : IntIdTable("rating_events") {
    val playerId = reference("player_id", PlayersTable)
    val matchId = reference("match_id", MatchesTable).nullable()
    val tournamentId = reference("tournament_id", TournamentsTable).nullable()

    // Stored as a plain varchar (like MatchesTable.status) so the value survives
    // even if the reason set changes; the check mirrors the V17 CHECK constraint.
    val reason = varchar("reason", 20)
        .check { it.inList(listOf("MATCH", "GUEST_WIN", "GUEST_LOSS", "TOURNAMENT_BONUS", "DECAY")) }
    val delta = integer("delta")
    val ratingAfter = integer("rating_after")
    val createdAt = timestamp("created_at").clientDefault { JavaInstant.now() }

    init {
        index(false, playerId, createdAt)
    }
}

class RatingEventDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RatingEventDAO>(RatingEventsTable)

    var player by PlayerDAO referencedOn RatingEventsTable.playerId
    var match by MatchDAO optionalReferencedOn RatingEventsTable.matchId
    var tournament by TournamentDAO optionalReferencedOn RatingEventsTable.tournamentId
    var reason by RatingEventsTable.reason
    var delta by RatingEventsTable.delta
    var ratingAfter by RatingEventsTable.ratingAfter
    var createdAt by RatingEventsTable.createdAt

    fun toDomain() = RatingEvent(
        id = id.value,
        matchId = match?.id?.value,
        tournamentId = tournament?.id?.value,
        reason = reason,
        delta = delta,
        ratingAfter = ratingAfter,
        createdAt = createdAt.toKotlinInstant()
    )
}
