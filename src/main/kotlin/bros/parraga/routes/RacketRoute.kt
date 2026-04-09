package bros.parraga.routes

import bros.parraga.services.repositories.racket.RacketRepository
import bros.parraga.services.repositories.racket.dto.CreateRacketStringingRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketStringingRequest
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

fun Route.racketRouting() {
    val racketRepository: RacketRepository by inject()
    val userRepository: UserRepository by inject()

    route("/public/rackets") {
        get("/{publicToken}") {
            handleRequest(call) {
                val publicToken = call.parameters["publicToken"]
                    ?: throw IllegalArgumentException("Parameter publicToken is required")
                racketRepository.getPublicRacket(publicToken)
            }
        }
    }

    route("/rackets") {
        authenticate("clerk-jwt") {
            post("/stringings") {
                handleRequest(call, HttpStatusCode.Created) {
                    val localUser = call.requireLocalUser(userRepository)
                    val request = call.receive<CreateRacketStringingRequest>()
                    racketRepository.createStringing(localUser.id, request)
                }
            }

            put("/{publicToken}") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val publicToken = call.parameters["publicToken"]
                        ?: throw IllegalArgumentException("Parameter publicToken is required")
                    val request = call.receive<UpdateRacketRequest>()
                    racketRepository.updateRacket(localUser.id, publicToken, request)
                }
            }

            put("/stringings/{stringingId}") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val stringingId = call.requireIntParameter("stringingId")
                    val request = call.receive<UpdateRacketStringingRequest>()
                    racketRepository.updateStringing(localUser.id, stringingId, request)
                }
            }

            delete("/stringings/{stringingId}") {
                handleRequest(call, HttpStatusCode.NoContent) {
                    val localUser = call.requireLocalUser(userRepository)
                    val stringingId = call.requireIntParameter("stringingId")
                    racketRepository.deleteStringing(localUser.id, stringingId)
                    ""
                }
            }
        }
    }
}
