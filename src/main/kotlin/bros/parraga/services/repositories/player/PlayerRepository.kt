package bros.parraga.services.repositories.player

import bros.parraga.domain.Player
import bros.parraga.services.repositories.player.dto.CreatePlayerRequest
import bros.parraga.services.repositories.player.dto.UpdatePlayerRequest

interface PlayerRepository {
    suspend fun getPlayers(): List<Player>
    suspend fun getPlayer(id: Int): Player
    suspend fun createPlayer(request: CreatePlayerRequest): Player
    suspend fun updatePlayer(request: UpdatePlayerRequest): Player
    suspend fun deletePlayer(id: Int)
}
