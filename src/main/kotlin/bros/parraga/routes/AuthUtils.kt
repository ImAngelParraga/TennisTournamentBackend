package bros.parraga.routes

import bros.parraga.domain.User
import bros.parraga.services.repositories.user.UserRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun ApplicationCall.requireJwtSubject(): String {
    val principal = principal<JWTPrincipal>() ?: throw IllegalStateException("Missing JWT principal")
    return principal.payload.subject ?: throw IllegalStateException("Missing JWT subject")
}

fun ApplicationCall.jwtEmailOrNull(): String? {
    val principal = principal<JWTPrincipal>() ?: return null
    return principal.payload.getClaim("email").asString()
}

fun ApplicationCall.jwtNameOrNull(): String? {
    val principal = principal<JWTPrincipal>() ?: return null
    return principal.payload.getClaim("name").asString()
        ?: principal.payload.getClaim("preferred_username").asString()
        ?: principal.payload.getClaim("username").asString()
}

suspend fun ApplicationCall.requireLocalUser(userRepository: UserRepository): User {
    val subject = requireJwtSubject()
    return userRepository.findOrCreateByAuthSubject(
        authSubject = subject,
        email = jwtEmailOrNull(),
        preferredName = jwtNameOrNull()
    )
}
