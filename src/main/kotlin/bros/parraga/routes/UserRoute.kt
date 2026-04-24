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
    val maxMatchActivityRangeDays = 93

    route("/users") {
        get {
            handleRequest(call) { userRepository.getUsers() }
        }

        get("/{id}/matches") {
            handleRequest(call) {
                val from = call.requireInstantQueryParameter("from")
                val to = call.requireInstantQueryParameter("to")
                validateInstantRange(from, to, maxMatchActivityRangeDays)
                userRepository.getUserMatchActivity(call.requireIntParameter("id"), from, to)
            }
        }

        authenticate("clerk-jwt") {
            get("/me") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    userRepository.getUser(localUser.id)
                }
            }

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

        get("/{id}") {
            handleRequest(call) { userRepository.getUser(call.requireIntParameter("id")) }
        }
    }
}
