package bros.parraga.services.repositories.user.dto

import kotlinx.serialization.Serializable

// Self-service profile edit (PATCH /users/me). Identity comes from the JWT, not the body.
@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val imageUrl: String? = null,
)
