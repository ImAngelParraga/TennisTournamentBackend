package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Match(
    val id: Int,
    val phaseId: Int,
    val round: Int,
    val groupId: Int? = null,
    val player1: Player? = null,
    val player2: Player? = null,
    val winnerId: Int? = null,
    val score: TennisScore? = null,
    val status: MatchStatus,
    val scheduledTime: Instant? = null,
    val court: String? = null,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val matchDependencies: List<MatchDependency> = emptyList()
) {
    companion object {
        /*fun fromLib(matchLib: parraga.bros.tournament.domain.Match): Match = Match(
            matchLib.id,
            1,
            matchLib.round,
            status = MatchStatus.valueOf(matchLib.status.name),

        )*/
    }
}

enum class MatchStatus { SCHEDULED, LIVE, COMPLETED, WALKOVER }