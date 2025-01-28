package bros.parraga.services.repositories.user.dto

import bros.parraga.domain.Role

data class CreateUserRequest(
    val username: String,
    val password: String,
    val email: String?,
    val role: Role
)
