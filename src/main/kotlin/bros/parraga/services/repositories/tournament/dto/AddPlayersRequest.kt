package bros.parraga.services.repositories.tournament.dto

import kotlinx.serialization.Serializable

@Serializable
data class TournamentPlayerRequest(
    val playerId: Int? = null,
    val name: String? = null,
    val seed: Int? = null
) {
    init {
        require(playerId != null || name != null) {
            "Either playerId or name must be provided"
        }
        require(!(playerId != null && name != null)) {
            "Cannot provide both playerId and name"
        }
        require(seed == null || seed > 0) {
            "Seed must be greater than 0 when provided"
        }
    }
}

@Serializable
data class AddPlayersRequest(val players: List<TournamentPlayerRequest>)
