package bros.parraga.services.repositories.tournament.dto

import kotlinx.serialization.Serializable

@Serializable
data class JoinTournamentByCodeRequest(
    val inviteCode: String
)
