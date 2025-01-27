package bros.parraga.domain

import bros.parraga.db.schema.UserDAO
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id : Int,
    val username: String,
    val password: String,
    val email: String?,
    val role: Role,
    val createdAt: Instant,
    val updatedAt: Instant
)

fun UserDAO.toDomain() = User(
    id.value,
    username,
    password,
    email,
    Role.valueOf(role),
    createdAt.toKotlinInstant(),
    updatedAt.toKotlinInstant()
)

enum class Role { PLAYER, CLUB }
