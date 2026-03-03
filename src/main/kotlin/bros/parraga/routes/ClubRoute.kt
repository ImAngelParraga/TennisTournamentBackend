package bros.parraga.routes

import bros.parraga.errors.ForbiddenException
import bros.parraga.services.auth.AuthorizationService
import bros.parraga.services.repositories.club.ClubRepository
import bros.parraga.services.repositories.club.dto.UpdateClubRequest
import bros.parraga.services.repositories.user.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.clubRouting() {
    val clubRepository: ClubRepository by inject()
    val userRepository: UserRepository by inject()
    val authorizationService: AuthorizationService by inject()

    route("/clubs") {
        get {
            handleRequest(call) { clubRepository.getClubs() }
        }

        get("/{id}") {
            handleRequest(call) {
                clubRepository.getClub(call.requireIntParameter("id"))
            }
        }

        get("/{id}/admins") {
            handleRequest(call) {
                clubRepository.getClubAdmins(call.requireIntParameter("id"))
            }
        }

        authenticate("clerk-jwt") {
            post {
                handleRequest(call, HttpStatusCode.Created) {
                    val localUser = call.requireLocalUser(userRepository)
                    clubRepository.createClub(call.receive(), localUser.id)
                }
            }

            put {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val request = call.receive<UpdateClubRequest>()
                    authorizationService.requireClubManager(localUser.id, request.id)
                    clubRepository.updateClub(request)
                }
            }

            delete("/{id}") {
                handleRequest(call, HttpStatusCode.NoContent) {
                    val localUser = call.requireLocalUser(userRepository)
                    val clubId = call.requireIntParameter("id")
                    authorizationService.requireClubManager(localUser.id, clubId)
                    clubRepository.deleteClub(clubId)
                }
            }

            post("/{id}/admins/{userId}") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val clubId = call.requireIntParameter("id")
                    val userId = call.requireIntParameter("userId")
                    authorizationService.requireClubManager(localUser.id, clubId)
                    if (authorizationService.isClubOwner(userId, clubId)) {
                        throw ForbiddenException("Owner cannot be managed via admins endpoint")
                    }
                    clubRepository.addClubAdmin(clubId, userId)
                }
            }

            delete("/{id}/admins/{userId}") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val clubId = call.requireIntParameter("id")
                    val userId = call.requireIntParameter("userId")
                    authorizationService.requireClubManager(localUser.id, clubId)
                    if (authorizationService.isClubOwner(userId, clubId)) {
                        throw ForbiddenException("Owner cannot be managed via admins endpoint")
                    }
                    clubRepository.removeClubAdmin(clubId, userId)
                }
            }
        }
    }
}
