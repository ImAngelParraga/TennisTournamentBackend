package bros.parraga.services.repositories.player

import bros.parraga.domain.Player
import bros.parraga.services.repositories.player.dto.CreatePlayerRequest
import bros.parraga.services.repositories.player.dto.UpdatePlayerRequest

interface PlayerRepository {
    suspend fun getPlayers(): List<Player>
    suspend fun getPlayer(id: Int): Player
    suspend fun createPlayerForUser(userId: Int, request: CreatePlayerRequest): Player
    suspend fun updatePlayerForUser(userId: Int, request: UpdatePlayerRequest): Player
    suspend fun deletePlayerForUser(userId: Int, playerId: Int)
    suspend fun getPlayerByUserId(userId: Int): Player?
}
