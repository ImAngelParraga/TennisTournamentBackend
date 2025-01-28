package bros.parraga.services.repositories.user.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserRequest(
    val id: Int,
    val username: String? = null,
    val password: String? = null,
    val email: String? = null,
)
