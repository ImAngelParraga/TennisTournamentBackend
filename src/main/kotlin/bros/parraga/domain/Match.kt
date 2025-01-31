package bros.parraga.domain

import kotlinx.datetime.Instant

data class Match(
    val id: Int,
    val phaseId: Int,
    val round: Int,
    val groupId: Int?,
    val swissRound: Int?,
    val player1: Player?,
    val player2: Player?,
    val winnerId: Int?,
    val scores: TennisScore?,
    val status: MatchStatus,
    val scheduledTime: Instant?,
    val court: String?,
    val createdAt: Instant,
    val updatedAt: Instant?
)

enum class MatchStatus { SCHEDULED, LIVE, COMPLETED, WALKOVER }