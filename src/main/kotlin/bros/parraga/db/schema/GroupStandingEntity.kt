package bros.parraga.db.schema

import bros.parraga.domain.GroupStanding
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object GroupStandingsTable : CompositeIdTable("group_standings") {
    val groupId = reference("group_id", GroupsTable, onDelete = ReferenceOption.CASCADE)
    val playerId = reference("player_id", PlayersTable, onDelete = ReferenceOption.CASCADE)
    val matchesPlayed = integer("matches_played").default(0)
    val wins = integer("wins").default(0)
    val points = integer("points").default(0)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").nullable()

    init {
        addIdColumn(groupId)
        addIdColumn(playerId)
    }

    override val primaryKey = PrimaryKey(groupId, playerId)
}

class GroupStandingDAO(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<GroupStandingDAO>(GroupStandingsTable)

    var group by GroupDAO referencedOn GroupStandingsTable.groupId
    var player by PlayerDAO referencedOn GroupStandingsTable.playerId
    var matchesPlayed by GroupStandingsTable.matchesPlayed
    var wins by GroupStandingsTable.wins
    var points by GroupStandingsTable.points
    var createdAt by GroupStandingsTable.createdAt
    var updatedAt by GroupStandingsTable.updatedAt

    fun toDomain() = GroupStanding(
        groupId = group.id.value,
        playerId = player.id.value,
        matchesPlayed = matchesPlayed,
        wins = wins,
        points = points,
        createdAt = createdAt.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )
}