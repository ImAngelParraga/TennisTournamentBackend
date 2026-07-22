package bros.parraga.db.schema

import bros.parraga.domain.League
import bros.parraga.domain.LeagueMatch
import bros.parraga.domain.LeagueMember
import bros.parraga.domain.TennisScore
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import java.time.Instant as JavaInstant

object LeaguesTable : IntIdTable("leagues") {
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val ownerUserId = reference("owner_user_id", UsersTable)
    val inviteCode = varchar("invite_code", 8).uniqueIndex()
    val createdAt = timestamp("created_at").databaseGenerated().nullable().default(JavaInstant.now())
    val updatedAt = timestamp("updated_at").databaseGenerated().nullable()
}

class LeagueDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LeagueDAO>(LeaguesTable)

    var name by LeaguesTable.name
    var description by LeaguesTable.description
    var owner by UserDAO referencedOn LeaguesTable.ownerUserId
    var inviteCode by LeaguesTable.inviteCode
    var createdAt by LeaguesTable.createdAt
    var updatedAt by LeaguesTable.updatedAt
    val members by LeagueMemberDAO referrersOn LeagueMembersTable.leagueId
    val matches by LeagueMatchDAO referrersOn LeagueMatchesTable.leagueId

    fun toDomain() = League(
        id = id.value,
        name = name,
        description = description,
        ownerUserId = owner.id.value,
        inviteCode = inviteCode,
        createdAt = createdAt?.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )
}

object LeagueMembersTable : CompositeIdTable("league_members") {
    val leagueId = reference("league_id", LeaguesTable, onDelete = ReferenceOption.CASCADE)
    val playerId = reference("player_id", PlayersTable)
    val rating = integer("rating").default(1000)
    val ratedMatches = integer("rated_matches").default(0)
    val wins = integer("wins").default(0)
    val losses = integer("losses").default(0)
    val joinedAt = timestamp("joined_at").databaseGenerated().nullable().default(JavaInstant.now())

    override val primaryKey = PrimaryKey(leagueId, playerId)

    init {
        addIdColumn(leagueId)
        addIdColumn(playerId)
    }
}

class LeagueMemberDAO(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<LeagueMemberDAO>(LeagueMembersTable)

    var league by LeagueDAO referencedOn LeagueMembersTable.leagueId
    var player by PlayerDAO referencedOn LeagueMembersTable.playerId
    var rating by LeagueMembersTable.rating
    var ratedMatches by LeagueMembersTable.ratedMatches
    var wins by LeagueMembersTable.wins
    var losses by LeagueMembersTable.losses
    var joinedAt by LeagueMembersTable.joinedAt

    fun toDomain() = LeagueMember(
        leagueId = league.id.value,
        playerId = player.id.value,
        userId = player.user?.id?.value,
        name = player.name,
        username = player.user?.username,
        rating = rating,
        ratedMatches = ratedMatches,
        wins = wins,
        losses = losses,
        joinedAt = joinedAt?.toKotlinInstant()
    )
}

object LeagueMatchesTable : IntIdTable("league_matches") {
    val leagueId = reference("league_id", LeaguesTable, onDelete = ReferenceOption.CASCADE)
    val player1Id = reference("player1_id", PlayersTable)
    val player2Id = reference("player2_id", PlayersTable)
    val winnerId = reference("winner_id", PlayersTable)
    val score = jsonb<TennisScore>(
        "score",
        { Json.encodeToString(it) },
        { Json.decodeFromString(it) }
    ).nullable()
    val playedAt = timestamp("played_at").clientDefault { JavaInstant.now() }
    val createdByUserId = reference("created_by_user_id", UsersTable)
    val createdAt = timestamp("created_at").databaseGenerated().nullable().default(JavaInstant.now())

    init {
        index(false, leagueId, playedAt, id)
    }
}

class LeagueMatchDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LeagueMatchDAO>(LeagueMatchesTable)

    var league by LeagueDAO referencedOn LeagueMatchesTable.leagueId
    var player1 by PlayerDAO referencedOn LeagueMatchesTable.player1Id
    var player2 by PlayerDAO referencedOn LeagueMatchesTable.player2Id
    var winner by PlayerDAO referencedOn LeagueMatchesTable.winnerId
    var score by LeagueMatchesTable.score
    var playedAt by LeagueMatchesTable.playedAt
    var createdBy by UserDAO referencedOn LeagueMatchesTable.createdByUserId
    var createdAt by LeagueMatchesTable.createdAt

    fun toDomain() = LeagueMatch(
        id = id.value,
        leagueId = league.id.value,
        player1 = player1.toDomain(),
        player2 = player2.toDomain(),
        winnerId = winner.id.value,
        score = score,
        playedAt = playedAt.toKotlinInstant(),
        createdByUserId = createdBy.id.value,
        createdAt = createdAt?.toKotlinInstant()
    )
}

object LeagueRatingEventsTable : IntIdTable("league_rating_events") {
    val leagueId = reference("league_id", LeaguesTable, onDelete = ReferenceOption.CASCADE)
    val leagueMatchId = reference("league_match_id", LeagueMatchesTable, onDelete = ReferenceOption.CASCADE).nullable()
    val playerId = reference("player_id", PlayersTable)
    val reason = varchar("reason", 20).check { it eq "MATCH" }
    val delta = integer("delta")
    val ratingAfter = integer("rating_after")
    val createdAt = timestamp("created_at").clientDefault { JavaInstant.now() }
}

class LeagueRatingEventDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LeagueRatingEventDAO>(LeagueRatingEventsTable)

    var league by LeagueDAO referencedOn LeagueRatingEventsTable.leagueId
    var leagueMatch by LeagueMatchDAO optionalReferencedOn LeagueRatingEventsTable.leagueMatchId
    var player by PlayerDAO referencedOn LeagueRatingEventsTable.playerId
    var reason by LeagueRatingEventsTable.reason
    var delta by LeagueRatingEventsTable.delta
    var ratingAfter by LeagueRatingEventsTable.ratingAfter
    var createdAt by LeagueRatingEventsTable.createdAt
}
