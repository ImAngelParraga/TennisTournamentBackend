package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class League(
    val id: Int,
    val name: String,
    val description: String?,
    val ownerUserId: Int,
    val inviteCode: String,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

@Serializable
data class LeagueMember(
    val leagueId: Int,
    val playerId: Int,
    val userId: Int?,
    val name: String,
    val username: String?,
    val rating: Int,
    val ratedMatches: Int,
    val wins: Int,
    val losses: Int,
    val joinedAt: Instant?
)

@Serializable
data class LeagueMatch(
    val id: Int,
    val leagueId: Int,
    val player1: Player,
    val player2: Player,
    val winnerId: Int,
    val score: TennisScore?,
    val playedAt: Instant,
    val createdByUserId: Int,
    val createdAt: Instant?
)

