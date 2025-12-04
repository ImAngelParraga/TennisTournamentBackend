package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val username: String,
    val email: String? = null,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

@Serializable
data class PublicUser(
    val id: Int,
    val username: String
)