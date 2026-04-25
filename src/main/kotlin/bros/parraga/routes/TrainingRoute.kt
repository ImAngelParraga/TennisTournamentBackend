package bros.parraga.routes

import bros.parraga.services.repositories.training.TrainingRepository
import bros.parraga.services.repositories.training.dto.CreateTrainingRequest
import bros.parraga.services.repositories.training.dto.UpdateTrainingRequest
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

fun Route.trainingRouting() {
    val trainingRepository: TrainingRepository by inject()
    val userRepository: UserRepository by inject()

    route("/users") {
        authenticate("clerk-jwt") {
            route("/me/trainings") {
                get {
                    handleRequest(call) {
                        val localUser = call.requireLocalUser(userRepository)
                        val from = call.requireLocalDateQueryParameter("from")
                        val to = call.requireLocalDateQueryParameter("to")
                        validateLocalDateRange(from, to)
                        trainingRepository.getOwnTrainingRange(localUser.id, from, to)
                    }
                }

                post {
                    handleRequest(call, HttpStatusCode.Created) {
                        val localUser = call.requireLocalUser(userRepository)
                        trainingRepository.createTraining(localUser.id, call.receive<CreateTrainingRequest>())
                    }
                }

                put("/{trainingId}") {
                    handleRequest(call) {
                        val localUser = call.requireLocalUser(userRepository)
                        trainingRepository.updateTraining(
                            localUser.id,
                            call.requireIntParameter("trainingId"),
                            call.receive<UpdateTrainingRequest>()
                        )
                    }
                }

                delete("/{trainingId}") {
                    handleRequest(call, HttpStatusCode.NoContent) {
                        val localUser = call.requireLocalUser(userRepository)
                        trainingRepository.deleteTraining(localUser.id, call.requireIntParameter("trainingId"))
                    }
                }
            }
        }
    }
}
