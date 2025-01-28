package bros.parraga.services.repositories.user.dto

import bros.parraga.domain.Role

data class UpdateUserRequest(
    val id: Int,
    val username: String? = null,
    val password: String? = null,
    val email: String? = null,
    val role: Role? = null
)
