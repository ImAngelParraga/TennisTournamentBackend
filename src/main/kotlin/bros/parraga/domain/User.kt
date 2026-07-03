package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    USER,
    PLATFORM_ADMIN
}

@Serializable
data class User(
    val id: Int,
    val username: String,
    val name: String? = null,
    val imageUrl: String? = null,
    val email: String? = null,
    val authProvider: String? = null,
    val authSubject: String? = null,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val achievements: List<Achievement> = emptyList(),
    // Completed/walkover matches won by the user's linked player. Populated on
    // user reads for the public ranking.
    val matchWins: Int = 0,
    val role: UserRole = UserRole.USER,
    // Clubs the user owns or administers. Populated only by GET /users/me.
    val managedClubIds: List<Int> = emptyList()
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
