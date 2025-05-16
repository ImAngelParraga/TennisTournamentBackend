package bros.parraga.routes

import bros.parraga.services.repositories.user.UserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.userRouting() {
    val userRepository: UserRepository by inject()

    route("/users") {
        get {
            handleRequest(call) { userRepository.getUsers() }
        }

        get("/{id}") {
            handleRequest(call) { userRepository.getUser(call.requireIntParameter("id")) }
        }

        post {
            handleRequest(call, HttpStatusCode.Created) {
                userRepository.createUser(call.receive())
            }
        }

        put {
            handleRequest(call) {
                userRepository.updateUser(call.receive())
            }
        }

        delete("/{id}") {
            handleRequest(call, HttpStatusCode.NoContent) { userRepository.deleteUser(call.requireIntParameter("id")) }
        }
    }
}