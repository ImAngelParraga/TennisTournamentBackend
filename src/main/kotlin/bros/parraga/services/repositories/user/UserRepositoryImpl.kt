package bros.parraga.services.repositories.user

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import bros.parraga.domain.User
import bros.parraga.services.repositories.user.dto.CreateUserRequest
import bros.parraga.services.repositories.user.dto.UpdateUserRequest
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import java.time.Instant

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
}