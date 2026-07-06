package bros.parraga.services.repositories.user.dto

import kotlinx.serialization.Serializable

// Self-service profile edit (PATCH /users/me). Identity comes from the JWT, not the body.
@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    // User-chosen handle / profile-URL slug. When provided it is slugified and must be
    // unique; when omitted the username stays in sync with the name (legacy behavior).
    val username: String? = null,
    val imageUrl: String? = null,
)
