package bros.parraga.routes

import bros.parraga.services.repositories.player.PlayerRepository
import bros.parraga.services.repositories.player.dto.CreatePlayerRequest
import bros.parraga.services.repositories.player.dto.UpdatePlayerRequest
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

fun Route.playerRouting() {
    val playerRepository: PlayerRepository by inject()
    val userRepository: UserRepository by inject()

    route("/players") {
        get {
            handleRequest(call) { playerRepository.getPlayers() }
        }

        get("/{id}") {
            handleRequest(call) { playerRepository.getPlayer(call.requireIntParameter("id")) }
        }

        authenticate("clerk-jwt") {
            post {
                handleRequest(call, HttpStatusCode.Created) {
                    val localUser = call.requireLocalUser(userRepository)
                    val request = call.receive<CreatePlayerRequest>()
                    playerRepository.createPlayerForUser(localUser.id, request)
                }
            }

            put {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val request = call.receive<UpdatePlayerRequest>()
                    playerRepository.updatePlayerForUser(localUser.id, request)
                }
            }

            delete("/{id}") {
                handleRequest(call, HttpStatusCode.NoContent) {
                    val localUser = call.requireLocalUser(userRepository)
                    val id = call.requireIntParameter("id")
                    playerRepository.deletePlayerForUser(localUser.id, id)
                }
            }
        }
    }
}
