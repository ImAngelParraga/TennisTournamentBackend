package bros.parraga.routes

import bros.parraga.domain.Tournament
import bros.parraga.routes.dto.CreateTournamentRequest
import bros.parraga.routes.dto.UpdateTournamentRequest
import bros.parraga.services.repositories.TournamentRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.tournamentRouting() {
    val tournamentRepository: TournamentRepository by inject()

    route("/tournaments") {
        get {
            handleRequest(call) { tournamentRepository.getTournaments() }
        }

        get("/{id}") {
            try {
                val id = call.requireIntParameter("id")
                handleRequest(call) { tournamentRepository.getTournament(id) }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Tournament>(FAILURE, message = e.message))
            }
        }

        post {
            handleRequest(call) {
                val request = call.receive<CreateTournamentRequest>()
                tournamentRepository.createTournament(request)
            }
        }

        put() {
            handleRequest(call) {
                val request = call.receive<UpdateTournamentRequest>()
                tournamentRepository.updateTournament(request)
            }
        }
    }
}
