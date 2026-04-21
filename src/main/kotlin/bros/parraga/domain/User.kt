package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val username: String,
    val email: String? = null,
    val authProvider: String? = null,
    val authSubject: String? = null,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val achievements: List<Achievement> = emptyList()
)

@Serializable
data class Achievement(
    val id: Int,
    val key: String,
    val name: String,
    val description: String? = null
)

@Serializable
data class PublicUser(
    val id: Int,
    val username: String
)
