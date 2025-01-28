package bros.parraga.routes

import bros.parraga.domain.Club
import bros.parraga.services.repositories.club.ClubRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.clubRouting() {
    val clubRepository: ClubRepository by inject()

    route("/clubs") {
        get {
            handleRequest(call) { clubRepository.getClubs() }
        }

        get("/{id}") {
            try {
                val id = call.requireIntParameter("id")
                handleRequest(call) { clubRepository.getClub(id) }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Club>(FAILURE, message = e.message))
            }
        }

        post {
            handleRequest(call, HttpStatusCode.Created) {
                clubRepository.createClub(call.receive())
            }
        }

        put {
            handleRequest(call) {
                clubRepository.updateClub(call.receive())
            }
        }

        delete("/{id}") {
            try {
                val id = call.requireIntParameter("id")
                handleRequest(call, HttpStatusCode.NoContent) { clubRepository.deleteClub(id) }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(FAILURE, message = e.message))
            }
        }
    }
}