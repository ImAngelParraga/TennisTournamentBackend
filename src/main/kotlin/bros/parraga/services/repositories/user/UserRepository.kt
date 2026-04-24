package bros.parraga.services.repositories.user

import bros.parraga.domain.User
import bros.parraga.services.repositories.user.dto.CreateUserRequest
import bros.parraga.services.repositories.user.dto.UserMatchActivityResponse
import bros.parraga.services.repositories.user.dto.UpdateUserRequest
import kotlinx.datetime.Instant

interface UserRepository {
    suspend fun getUsers(): List<User>
    suspend fun getUser(id: Int): User
    suspend fun getUserMatchActivity(userId: Int, from: Instant, to: Instant): UserMatchActivityResponse
    suspend fun createUser(request: CreateUserRequest): User
    suspend fun updateUser(request: UpdateUserRequest): User
    suspend fun deleteUser(id: Int)
    suspend fun findByAuthSubject(authSubject: String): User?
    suspend fun findOrCreateByAuthSubject(authSubject: String, email: String?, preferredName: String?): User
}
