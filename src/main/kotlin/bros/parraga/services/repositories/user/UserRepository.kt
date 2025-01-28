package bros.parraga.services.repositories.user

import bros.parraga.domain.User
import bros.parraga.services.repositories.user.dto.CreateUserRequest
import bros.parraga.services.repositories.user.dto.UpdateUserRequest

interface UserRepository {
    suspend fun getUsers(): List<User>
    suspend fun getUser(id: Int): User
    suspend fun createUser(request: CreateUserRequest): User
    suspend fun updateUser(request: UpdateUserRequest): User
    suspend fun deleteUser(id: Int)
}