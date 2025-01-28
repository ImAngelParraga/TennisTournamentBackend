package bros.parraga.services.repositories.tournament.dto

data class TournamentPlayerRequest(
    val playerId: Int? = null,
    val name: String? = null
) {
    init {
        require(playerId != null || name != null) {
            "Either playerId or name must be provided"
        }
        require(!(playerId != null && name != null)) {
            "Cannot provide both playerId and name"
        }
    }
}

data class AddPlayersRequest(val players: List<TournamentPlayerRequest>)