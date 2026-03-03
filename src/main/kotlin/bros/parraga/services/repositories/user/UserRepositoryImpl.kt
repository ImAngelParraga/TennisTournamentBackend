package bros.parraga.services.repositories.user

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import bros.parraga.domain.User
import bros.parraga.services.repositories.user.dto.CreateUserRequest
import bros.parraga.services.repositories.user.dto.UpdateUserRequest
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

class UserRepositoryImpl : UserRepository {
    override suspend fun getUsers(): List<User> = dbQuery {
        UserDAO.all().map { it.toDomain() }
    }

    override suspend fun getUser(id: Int): User = dbQuery {
        UserDAO[id].toDomain()
    }

    override suspend fun createUser(request: CreateUserRequest): User = dbQuery {
        UserDAO.new {
            username = request.username
            email = request.email
            authProvider = "local"
            authSubject = null
        }.toDomain()
    }

    override suspend fun updateUser(request: UpdateUserRequest): User = dbQuery {
        UserDAO.findByIdAndUpdate(request.id) {
            it.apply {
                request.username?.let { username = it }
                request.email?.let { email = it }
                updatedAt = Instant.now()
            }
        }?.toDomain() ?: throw EntityNotFoundException(
            DaoEntityID(request.id, UsersTable),
            UserDAO
        )
    }

    override suspend fun deleteUser(id: Int) = dbQuery {
        UserDAO[id].delete()
    }

    override suspend fun findByAuthSubject(authSubject: String): User? = dbQuery {
        UserDAO.find { UsersTable.authSubject eq authSubject }
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun findOrCreateByAuthSubject(authSubject: String, email: String?, preferredName: String?): User = dbQuery {
        UserDAO.find { UsersTable.authSubject eq authSubject }
            .firstOrNull()
            ?.toDomain()
            ?: UserDAO.new {
                username = generateUniqueUsername(preferredName, email)
                this.email = email
                authProvider = "clerk"
                this.authSubject = authSubject
            }.toDomain()
    }

    private fun generateUniqueUsername(preferredName: String?, email: String?): String {
        val base = preferredName
            ?.trim()
            ?.replace("\\s+".toRegex(), "_")
            ?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "user"

        var candidate = base.take(220)
        var attempt = 0

        while (UserDAO.find { UsersTable.username eq candidate }.firstOrNull() != null) {
            attempt += 1
            candidate = "${base.take(200)}_${attempt}_${UUID.randomUUID().toString().take(8)}"
        }

        return candidate
    }
}
