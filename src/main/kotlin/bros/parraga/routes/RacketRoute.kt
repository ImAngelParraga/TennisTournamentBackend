package bros.parraga.routes

import bros.parraga.services.repositories.racket.RacketRepository
import bros.parraga.services.repositories.racket.dto.CreateRacketRequest
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

    route("/users") {
        authenticate("clerk-jwt") {
            route("/me/rackets") {
                get {
                    handleRequest(call) {
                        val localUser = call.requireLocalUser(userRepository)
                        racketRepository.getOwnRackets(localUser.id)
                    }
                }

                get("/{racketId}") {
                    handleRequest(call) {
                        val localUser = call.requireLocalUser(userRepository)
                        racketRepository.getOwnRacket(localUser.id, call.requireIntParameter("racketId"))
                    }
                }

                post {
                    handleRequest(call, HttpStatusCode.Created) {
                        val localUser = call.requireLocalUser(userRepository)
                        racketRepository.createRacket(localUser.id, call.receive<CreateRacketRequest>())
                    }
                }

                put("/{racketId}") {
                    handleRequest(call) {
                        val localUser = call.requireLocalUser(userRepository)
                        racketRepository.updateRacket(
                            localUser.id,
                            call.requireIntParameter("racketId"),
                            call.receive<UpdateRacketRequest>()
                        )
                    }
                }

                delete("/{racketId}") {
                    handleRequest(call, HttpStatusCode.NoContent) {
                        val localUser = call.requireLocalUser(userRepository)
                        racketRepository.deleteRacket(localUser.id, call.requireIntParameter("racketId"))
                    }
                }

                post("/{racketId}/stringings") {
                    handleRequest(call, HttpStatusCode.Created) {
                        val localUser = call.requireLocalUser(userRepository)
                        racketRepository.createStringing(
                            localUser.id,
                            call.requireIntParameter("racketId"),
                            call.receive<CreateRacketStringingRequest>()
                        )
                    }
                }

                put("/{racketId}/stringings/{stringingId}") {
                    handleRequest(call) {
                        val localUser = call.requireLocalUser(userRepository)
                        racketRepository.updateStringing(
                            localUser.id,
                            call.requireIntParameter("racketId"),
                            call.requireIntParameter("stringingId"),
                            call.receive<UpdateRacketStringingRequest>()
                        )
                    }
                }

                delete("/{racketId}/stringings/{stringingId}") {
                    handleRequest(call, HttpStatusCode.NoContent) {
                        val localUser = call.requireLocalUser(userRepository)
                        racketRepository.deleteStringing(
                            localUser.id,
                            call.requireIntParameter("racketId"),
                            call.requireIntParameter("stringingId")
                        )
                    }
                }
            }
        }

        route("/{id}/rackets") {
            get {
                handleRequest(call) {
                    racketRepository.getPublicRackets(call.requireIntParameter("id"))
                }
            }

            get("/{racketId}") {
                handleRequest(call) {
                    racketRepository.getPublicRacket(
                        call.requireIntParameter("id"),
                        call.requireIntParameter("racketId")
                    )
                }
            }
        }
    }
}
