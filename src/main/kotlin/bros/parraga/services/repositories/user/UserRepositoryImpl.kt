package bros.parraga.services.repositories.user

import bros.parraga.db.DatabaseFactory
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import bros.parraga.domain.User
import bros.parraga.domain.toDomain
import bros.parraga.services.repositories.user.dto.CreateUserRequest
import bros.parraga.services.repositories.user.dto.UpdateUserRequest
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import java.time.Instant

class UserRepositoryImpl : UserRepository {
    override suspend fun getUsers(): List<User> = DatabaseFactory.dbQuery {
        UserDAO.Companion.all().map { it.toDomain() }
    }

    override suspend fun getUser(id: Int): User = DatabaseFactory.dbQuery {
        UserDAO.Companion[id].toDomain()
    }

    override suspend fun createUser(request: CreateUserRequest): User = DatabaseFactory.dbQuery {
        UserDAO.Companion.new {
            username = request.username
            password = request.password
            email = request.email
        }.toDomain()
    }

    override suspend fun updateUser(request: UpdateUserRequest): User = DatabaseFactory.dbQuery {
        UserDAO.Companion.findByIdAndUpdate(request.id) {
            it.apply {
                request.username?.let { username = it }
                request.password?.let { password = it }
                request.email?.let { email = it }
                updatedAt = Instant.now()
            }
        }?.toDomain() ?: throw EntityNotFoundException(
            DaoEntityID(request.id, UsersTable),
            UserDAO.Companion
        )
    }

    override suspend fun deleteUser(id: Int) = DatabaseFactory.dbQuery {
        UserDAO.Companion[id].delete()
    }
}