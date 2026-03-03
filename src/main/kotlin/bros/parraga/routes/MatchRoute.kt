package bros.parraga.routes

import bros.parraga.services.auth.AuthorizationService
import bros.parraga.services.repositories.match.MatchRepository
import bros.parraga.services.repositories.match.dto.UpdateMatchScoreRequest
import bros.parraga.services.repositories.user.UserRepository
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.matchRouting() {
    val matchRepository: MatchRepository by inject()
    val userRepository: UserRepository by inject()
    val authorizationService: AuthorizationService by inject()

    route("/matches") {
        get("/{id}") {
            handleRequest(call) {
                matchRepository.getMatch(call.requireIntParameter("id"))
            }
        }

        authenticate("clerk-jwt") {
            put("/{id}/score") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val id = call.requireIntParameter("id")
                    val request = call.receive<UpdateMatchScoreRequest>()
                    authorizationService.requireMatchManager(localUser.id, id)
                    matchRepository.updateMatchScore(id, request)
                }
            }
        }
    }
}
