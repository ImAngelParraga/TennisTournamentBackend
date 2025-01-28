package bros.parraga.routes

import bros.parraga.domain.User
import bros.parraga.services.repositories.user.UserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.userRouting() {
    val userRepository: UserRepository by inject()

    route("/users") {
        get {
            handleRequest(call) { userRepository.getUsers() }
        }

        get("/{id}") {
            try {
                val id = call.requireIntParameter("id")
                handleRequest(call) { userRepository.getUser(id) }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<User>(FAILURE, message = e.message))
            }
        }

        post {
            handleRequest(call) {
                userRepository.createUser(call.receive())
            }
        }

        put {
            handleRequest(call) {
                userRepository.updateUser(call.receive())
            }
        }

        delete("/{id}") {
            try {
                val id = call.requireIntParameter("id")
                handleRequest(call, HttpStatusCode.NoContent) { userRepository.deleteUser(id) }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(FAILURE, message = e.message))
            }
        }
    }
}