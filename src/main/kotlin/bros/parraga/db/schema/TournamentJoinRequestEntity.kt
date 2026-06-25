package bros.parraga.db.schema

import bros.parraga.domain.TournamentJoinRequest
import bros.parraga.domain.TournamentJoinRequestStatus
import bros.parraga.domain.TournamentStatus
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object TournamentJoinRequestsTable : IntIdTable("tournament_join_requests") {
    val tournamentId = reference("tournament_id", TournamentsTable, onDelete = ReferenceOption.CASCADE)
    val playerId = reference("player_id", PlayersTable, onDelete = ReferenceOption.CASCADE)
    val requesterUserId = reference("requester_user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 20)
        .check { it.inList(TournamentJoinRequestStatus.entries.map { status -> status.name }) }
    val playerNote = varchar("player_note", 500).nullable()
    val managerNote = varchar("manager_note", 500).nullable()
    val decidedByUserId = reference("decided_by_user_id", UsersTable).nullable()
    val requestedAt = timestamp("requested_at")
    val decidedAt = timestamp("decided_at").nullable()
    val withdrawnAt = timestamp("withdrawn_at").nullable()
    val resubmitAfter = timestamp("resubmit_after").nullable()
    val resubmitUnlockedByUserId = reference("resubmit_unlocked_by_user_id", UsersTable).nullable()
    val resubmitUnlockedAt = timestamp("resubmit_unlocked_at").nullable()
    val createdAt = timestamp("created_at").databaseGenerated().default(Instant.now()).nullable()
    val updatedAt = timestamp("updated_at").databaseGenerated().nullable()

    init {
        uniqueIndex(tournamentId, playerId)
    }
}

class TournamentJoinRequestDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TournamentJoinRequestDAO>(TournamentJoinRequestsTable)

    var tournament by TournamentDAO referencedOn TournamentJoinRequestsTable.tournamentId
    var player by PlayerDAO referencedOn TournamentJoinRequestsTable.playerId
    var requester by UserDAO referencedOn TournamentJoinRequestsTable.requesterUserId
    var status by TournamentJoinRequestsTable.status
    var playerNote by TournamentJoinRequestsTable.playerNote
    var managerNote by TournamentJoinRequestsTable.managerNote
    var decidedBy by UserDAO optionalReferencedOn TournamentJoinRequestsTable.decidedByUserId
    var requestedAt by TournamentJoinRequestsTable.requestedAt
    var decidedAt by TournamentJoinRequestsTable.decidedAt
    var withdrawnAt by TournamentJoinRequestsTable.withdrawnAt
    var resubmitAfter by TournamentJoinRequestsTable.resubmitAfter
    var resubmitUnlockedBy by UserDAO optionalReferencedOn TournamentJoinRequestsTable.resubmitUnlockedByUserId
    var resubmitUnlockedAt by TournamentJoinRequestsTable.resubmitUnlockedAt
    var createdAt by TournamentJoinRequestsTable.createdAt
    var updatedAt by TournamentJoinRequestsTable.updatedAt

    fun toDomain() = TournamentJoinRequest(
        id = id.value,
        tournamentId = tournament.id.value,
        player = player.toDomain(),
        requester = requester.toPublicUser(),
        status = TournamentJoinRequestStatus.valueOf(status),
        playerNote = playerNote,
        managerNote = managerNote,
        decidedBy = decidedBy?.toPublicUser(),
        requestedAt = requestedAt.toKotlinInstant(),
        decidedAt = decidedAt?.toKotlinInstant(),
        withdrawnAt = withdrawnAt?.toKotlinInstant(),
        resubmitAfter = resubmitAfter?.toKotlinInstant(),
        resubmitUnlockedBy = resubmitUnlockedBy?.toPublicUser(),
        resubmitUnlockedAt = resubmitUnlockedAt?.toKotlinInstant(),
        createdAt = createdAt?.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )
}

fun UserDAO.toPublicUser() = bros.parraga.domain.PublicUser(id.value, username)
