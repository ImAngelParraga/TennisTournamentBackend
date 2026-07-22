package bros.parraga.services.repositories.league.dto

import bros.parraga.domain.TennisScore
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CreateLeagueRequest(
    val name: String,
    val description: String? = null
)

@Serializable
data class UpdateLeagueRequest(
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class JoinLeagueRequest(
    val inviteCode: String
)

@Serializable
data class AddLeagueMemberRequest(
    val email: String
)

@Serializable
data class RecordLeagueMatchRequest(
    val player1Id: Int,
    val player2Id: Int,
    val winnerId: Int,
    val score: TennisScore? = null,
    val playedAt: Instant? = null
)

