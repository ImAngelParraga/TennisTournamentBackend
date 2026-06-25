package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

enum class TournamentJoinRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    WITHDRAWN,
    EXPIRED
}

@Serializable
data class TournamentJoinRequest(
    val id: Int,
    val tournamentId: Int,
    val player: Player,
    val requester: PublicUser,
    val status: TournamentJoinRequestStatus,
    val playerNote: String? = null,
    val managerNote: String? = null,
    val decidedBy: PublicUser? = null,
    val requestedAt: Instant,
    val decidedAt: Instant? = null,
    val withdrawnAt: Instant? = null,
    val resubmitAfter: Instant? = null,
    val resubmitUnlockedBy: PublicUser? = null,
    val resubmitUnlockedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)
