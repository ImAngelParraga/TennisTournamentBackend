package bros.parraga.services.repositories.user.dto

import bros.parraga.domain.MatchStatus
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.TennisScore
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserMatchActivityResponse(
    val userId: Int,
    val playerId: Int? = null,
    val playerName: String? = null,
    val from: Instant,
    val to: Instant,
    val matches: List<UserMatchActivityItem>
)

@Serializable
data class UserMatchActivityItem(
    val matchId: Int,
    val completedAt: Instant,
    val status: MatchStatus,
    val result: UserMatchResult,
    val score: TennisScore? = null,
    val court: String? = null,
    val tournament: UserMatchTournamentSummary,
    val phase: UserMatchPhaseSummary,
    val opponent: UserMatchOpponentSummary? = null
)

@Serializable
data class UserMatchTournamentSummary(
    val id: Int,
    val name: String
)

@Serializable
data class UserMatchPhaseSummary(
    val id: Int,
    val phaseOrder: Int,
    val format: PhaseFormat,
    val round: Int
)

@Serializable
data class UserMatchOpponentSummary(
    val id: Int,
    val name: String,
    val userId: Int? = null
)

@Serializable
enum class UserMatchResult {
    WIN,
    LOSS
}
