package bros.parraga.services

import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.PlayersTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import io.ktor.server.plugins.NotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class PlayerResolutionService {
    fun findOrCreateForUser(user: UserDAO, requestedName: String? = null): PlayerDAO {
        PlayerDAO.find { PlayersTable.userId eq user.id }.firstOrNull()?.let { return it }
        val playerName = normalizePlayerName(requestedName) ?: user.name ?: user.username
        return PlayerDAO.new {
            name = playerName.take(255)
            external = false
            this.user = user
        }
    }

    fun findRegisteredByEmail(email: String): PlayerDAO {
        val normalized = email.trim().lowercase()
        val user = UserDAO.find { UsersTable.email eq normalized }.firstOrNull()
            ?: throw NotFoundException("User with email $normalized not found")
        return findOrCreateForUser(user)
    }

    private fun normalizePlayerName(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }
}
