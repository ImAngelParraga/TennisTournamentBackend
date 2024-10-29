package bros.parraga.routes

import bros.parraga.services.repositories.TournamentRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.koin.ktor.ext.inject

fun Route.tournamentRouting() {
    val tournamentRepository: TournamentRepository by inject()

    route("/tournaments") {
        get {
            try {
                val tournaments = tournamentRepository.getTournaments()
                call.respond(HttpStatusCode.OK, tournaments)
            } catch (e: Exception) {
                when (e) {
                    is EntityNotFoundException -> call.respond(HttpStatusCode.NotFound, e.message ?: "Entity not found")
                    else -> call.respond(HttpStatusCode.InternalServerError, e.message ?: "Unknown error")
                }
            }
        }
    }
}