package bros.parraga.services.repositories.tournament.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateTournamentJoinRequest(
    val playerName: String? = null,
    val note: String? = null
)

@Serializable
data class DecideTournamentJoinRequest(
    val note: String? = null
)

@Serializable
data class AcceptTournamentJoinRequest(
    val seed: Int? = null,
    val note: String? = null
) {
    init {
        require(seed == null || seed > 0) {
            "Seed must be greater than 0 when provided"
        }
    }
}
