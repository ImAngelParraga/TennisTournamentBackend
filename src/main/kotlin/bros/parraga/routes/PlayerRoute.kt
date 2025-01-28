package bros.parraga.routes

import bros.parraga.domain.Player
import bros.parraga.services.repositories.player.PlayerRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.playerRouting() {
    val playerRepository: PlayerRepository by inject()

    route("/players") {
        get {
            handleRequest(call) { playerRepository.getPlayers() }
        }

        get("/{id}") {
            try {
                val id = call.requireIntParameter("id")
                handleRequest(call) { playerRepository.getPlayer(id) }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Player>(FAILURE, message = e.message))
            }
        }

        post {
            handleRequest(call, HttpStatusCode.Created) {
                playerRepository.createPlayer(call.receive())
            }
        }

        put {
            handleRequest(call) {
                playerRepository.updatePlayer(call.receive())
            }
        }

        delete("/{id}") {
            try {
                val id = call.requireIntParameter("id")
                handleRequest(call, HttpStatusCode.NoContent) { playerRepository.deletePlayer(id) }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(FAILURE, message = e.message))
            }
        }
    }
}