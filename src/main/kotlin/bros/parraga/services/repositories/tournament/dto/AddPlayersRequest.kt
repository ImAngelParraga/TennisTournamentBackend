package bros.parraga.services.repositories.tournament.dto

import kotlinx.serialization.Serializable

@Serializable
data class TournamentPlayerRequest(
    val playerId: Int? = null,
    val name: String? = null,
    val email: String? = null,
    val seed: Int? = null
) {
    init {
        val identifiers = listOfNotNull(playerId, name, email)
        require(identifiers.isNotEmpty()) {
            "Either playerId, name, or email must be provided"
        }
        require(identifiers.size == 1) {
            "Cannot provide more than one of playerId, name, or email"
        }
        require(seed == null || seed > 0) {
            "Seed must be greater than 0 when provided"
        }
    }
}

@Serializable
data class AddPlayersRequest(val players: List<TournamentPlayerRequest>)
