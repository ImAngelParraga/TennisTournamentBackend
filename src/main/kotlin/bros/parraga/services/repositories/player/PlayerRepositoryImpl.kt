package bros.parraga.services.repositories.player

import bros.parraga.errors.ConflictException
import bros.parraga.errors.ForbiddenException
import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.PlayersTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.Player
import bros.parraga.services.repositories.player.dto.CreatePlayerRequest
import bros.parraga.services.repositories.player.dto.UpdatePlayerRequest
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class PlayerRepositoryImpl : PlayerRepository {
    override suspend fun getPlayers(): List<Player> = dbQuery {
        PlayerDAO.all().map { it.toDomain() }
    }

    override suspend fun getPlayer(id: Int): Player = dbQuery {
        PlayerDAO[id].toDomain()
    }

    override suspend fun createPlayerForUser(userId: Int, request: CreatePlayerRequest): Player = dbQuery {
        if (PlayerDAO.find { PlayersTable.userId eq userId }.firstOrNull() != null) {
            throw ConflictException("Authenticated user already has a player profile")
        }

        PlayerDAO.new {
            name = request.name
            external = false
            user = UserDAO[userId]
        }.toDomain()
    }

    override suspend fun updatePlayerForUser(userId: Int, request: UpdatePlayerRequest): Player = dbQuery {
        val player = PlayerDAO.findById(request.id)
            ?: throw EntityNotFoundException(DaoEntityID(request.id, PlayersTable), PlayerDAO)
        if (player.user?.id?.value != userId) {
            throw ForbiddenException("You can only update your own player profile")
        }

        request.name?.let { player.name = it }
        player.toDomain()
    }

    override suspend fun deletePlayerForUser(userId: Int, playerId: Int) = dbQuery {
        val player = PlayerDAO.findById(playerId)
            ?: throw EntityNotFoundException(DaoEntityID(playerId, PlayersTable), PlayerDAO)
        if (player.user?.id?.value != userId) {
            throw ForbiddenException("You can only delete your own player profile")
        }

        player.delete()
    }

    override suspend fun getPlayerByUserId(userId: Int): Player? = dbQuery {
        PlayerDAO.find { PlayersTable.userId eq userId }.firstOrNull()?.toDomain()
    }
}
