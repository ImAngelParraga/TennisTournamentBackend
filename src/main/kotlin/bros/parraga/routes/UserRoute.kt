package bros.parraga.routes

import bros.parraga.errors.ForbiddenException
import bros.parraga.services.repositories.user.UserRepository
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.userRouting() {
    val userRepository: UserRepository by inject()

    route("/users") {
        get {
            handleRequest(call) { userRepository.getUsers() }
        }

        get("/{id}") {
            handleRequest(call) { userRepository.getUser(call.requireIntParameter("id")) }
        }

        authenticate("clerk-jwt") {
            post {
                handleRequest<Unit>(call) {
                    call.requireLocalUser(userRepository)
                    throw ForbiddenException("User write endpoints are disabled")
                }
            }

            put {
                handleRequest<Unit>(call) {
                    call.requireLocalUser(userRepository)
                    throw ForbiddenException("User write endpoints are disabled")
                }
            }

            delete("/{id}") {
                handleRequest<Unit>(call) {
                    call.requireLocalUser(userRepository)
                    throw ForbiddenException("User write endpoints are disabled")
                }
            }
        }
    }
}
