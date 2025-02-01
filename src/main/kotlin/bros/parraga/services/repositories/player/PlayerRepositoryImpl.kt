package bros.parraga.services.repositories.player

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.PlayersTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.Player
import bros.parraga.services.repositories.player.dto.CreatePlayerRequest
import bros.parraga.services.repositories.player.dto.UpdatePlayerRequest
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException

class PlayerRepositoryImpl : PlayerRepository {
    override suspend fun getPlayers(): List<Player> = dbQuery {
        PlayerDAO.all().map { it.toDomain() }
    }

    override suspend fun getPlayer(id: Int): Player = dbQuery {
        PlayerDAO[id].toDomain()
    }

    override suspend fun createPlayer(request: CreatePlayerRequest): Player = dbQuery {
        PlayerDAO.new {
            name = request.name
            external = request.external
            user = request.userId?.let { UserDAO[it] }
        }.toDomain()
    }

    override suspend fun updatePlayer(request: UpdatePlayerRequest): Player = dbQuery {
        PlayerDAO.findByIdAndUpdate(request.id) {
            it.apply {
                request.name?.let { name = it }
                request.external?.let { external = it }
                request.userId?.let { user = UserDAO[it] }
            }
        }?.toDomain() ?: throw EntityNotFoundException(DaoEntityID(request.id, PlayersTable), PlayerDAO)
    }

    override suspend fun deletePlayer(id: Int) = dbQuery {
        PlayerDAO[id].delete()
    }
}
