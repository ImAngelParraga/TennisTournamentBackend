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
    val email: String? = null,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

fun UserDAO.toDomain() = User(
    id.value,
    username,
    password,
    email,
    createdAt?.toKotlinInstant(),
    updatedAt?.toKotlinInstant()
)