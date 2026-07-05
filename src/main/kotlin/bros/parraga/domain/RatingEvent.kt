package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A single, append-only change to a player's rating. Powers the public
 * rating-history endpoint (audit trail / future sparkline). `matchId` and
 * `tournamentId` are populated depending on the [reason]:
 *  - MATCH / GUEST_WIN / GUEST_LOSS -> match + tournament
 *  - TOURNAMENT_BONUS -> tournament only
 *  - DECAY -> neither
 */
@Serializable
data class RatingEvent(
    val id: Int,
    val matchId: Int? = null,
    val tournamentId: Int? = null,
    val reason: String,
    val delta: Int,
    val ratingAfter: Int,
    val createdAt: Instant
)
