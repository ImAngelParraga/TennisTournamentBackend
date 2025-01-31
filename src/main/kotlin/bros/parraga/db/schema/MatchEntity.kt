package bros.parraga.db.schema

import bros.parraga.domain.Match
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.TennisScore
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import java.time.Instant as JavaInstant

object MatchesTable : IntIdTable("matches") {
    val phase = reference("phase_id", TournamentPhasesTable, onDelete = ReferenceOption.CASCADE)
    val round = integer("round").check { it greater 0 }
    val groupId = reference("group_id", GroupsTable, onDelete = ReferenceOption.CASCADE).nullable()
    val swissRound = integer("swiss_round").nullable()
    val player1Id = reference("player1_id", PlayersTable).nullable()
    val player2Id = reference("player2_id", PlayersTable).nullable()
    val winner = reference("winner_id", PlayersTable).nullable()
    val scores = jsonb<TennisScore>(
        "scores",
        { Json.encodeToString(it) },
        { Json.decodeFromString(it) }
    ).nullable()
    // TODO(Is it possible to use valueOf here?)
    val status = varchar("status", 20).check { it.inList(MatchStatus.entries.map { it.name }) }
        .default("SCHEDULED")
    val scheduledTime = timestamp("scheduled_time").nullable()
    val court = varchar("court", 100).nullable()
    val createdAt = timestamp("created_at").clientDefault { JavaInstant.now() }
    val updatedAt = timestamp("updated_at").nullable()
}

class MatchDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MatchDAO>(MatchesTable)

    var phase by TournamentPhaseDAO referencedOn MatchesTable.phase
    var round by MatchesTable.round
    var group by GroupDAO optionalReferencedOn MatchesTable.groupId
    var swissRound by MatchesTable.swissRound
    var player1 by PlayerDAO optionalReferencedOn MatchesTable.player1Id
    var player2 by PlayerDAO optionalReferencedOn MatchesTable.player2Id
    var winner by PlayerDAO optionalReferencedOn MatchesTable.winner
    var scores by MatchesTable.scores
    var status by MatchesTable.status
    var scheduledTime by MatchesTable.scheduledTime
    var court by MatchesTable.court
    var createdAt by MatchesTable.createdAt
    var updatedAt by MatchesTable.updatedAt

    fun toDomain() = Match(
        id = id.value,
        phaseId = phase.id.value,
        round = round,
        groupId = group?.id?.value,
        swissRound = swissRound,
        player1 = player1?.toDomain(),
        player2 = player2?.toDomain(),
        winnerId = winner?.id?.value,
        scores = scores,
        status = MatchStatus.valueOf(status),
        scheduledTime = scheduledTime?.toKotlinInstant(),
        court = court,
        createdAt = createdAt.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )
}