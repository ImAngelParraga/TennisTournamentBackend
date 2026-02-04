package bros.parraga.routes

import bros.parraga.domain.Match
import bros.parraga.services.repositories.match.MatchRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.matchRouting() {
    val matchRepository: MatchRepository by inject()

    route("/matches") {
        get("/{id}") {
            try {
                val id = call.requireIntParameter("id")
                handleRequest(call) { matchRepository.getMatch(id) }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Match>(FAILURE, message = e.message))
            }
        }

        put("/{id}/score") {
            try {
                val id = call.requireIntParameter("id")
                handleRequest(call) {
                    matchRepository.updateMatchScore(id, call.receive())
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Match>(FAILURE, message = e.message))
            }
        }
    }
}
