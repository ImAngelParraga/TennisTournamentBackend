package bros.parraga.routes

import bros.parraga.errors.ForbiddenException
import bros.parraga.services.repositories.user.UserRepository
import bros.parraga.services.repositories.user.dto.UpdateProfileRequest
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.userRouting() {
    val userRepository: UserRepository by inject()
    val maxMatchActivityRangeDays = 93
    val maxProfileCalendarRangeDays = 93

    route("/users") {
        get {
            handleRequest(call) { userRepository.getUsers() }
        }

        get("/by-username/{username}") {
            handleRequest(call) {
                val username = call.parameters["username"]?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Parameter username is required")
                userRepository.getUserByUsername(username)
            }
        }

        get("/{id}/matches") {
            handleRequest(call) {
                val from = call.requireInstantQueryParameter("from")
                val to = call.requireInstantQueryParameter("to")
                validateInstantRange(from, to, maxMatchActivityRangeDays)
                userRepository.getUserMatchActivity(call.requireIntParameter("id"), from, to)
            }
        }

        get("/{id}/tournaments") {
            handleRequest(call) {
                userRepository.getUserTournaments(call.requireIntParameter("id"))
            }
        }

        authenticate("clerk-jwt") {
            get("/me") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    userRepository.getMe(localUser.id)
                }
            }

            patch("/me") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    userRepository.updateOwnProfile(localUser.id, call.receive<UpdateProfileRequest>())
                }
            }

            get("/me/profile-calendar") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val from = call.requireLocalDateQueryParameter("from")
                    val to = call.requireLocalDateQueryParameter("to")
                    validateLocalDateRange(from, to, maxProfileCalendarRangeDays)
                    userRepository.getOwnProfileCalendar(
                        localUser.id,
                        from,
                        to,
                        call.getZoneIdQueryParameter("timezone")
                    )
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

        get("/{id}/profile-calendar") {
            handleRequest(call) {
                val from = call.requireLocalDateQueryParameter("from")
                val to = call.requireLocalDateQueryParameter("to")
                validateLocalDateRange(from, to, maxProfileCalendarRangeDays)
                userRepository.getPublicProfileCalendar(
                    call.requireIntParameter("id"),
                    from,
                    to,
                    call.getZoneIdQueryParameter("timezone")
                )
            }
        }

        get("/{id}") {
            handleRequest(call) { userRepository.getUser(call.requireIntParameter("id")) }
        }
    }
}
