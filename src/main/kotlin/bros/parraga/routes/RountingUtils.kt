package bros.parraga.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import io.ktor.server.plugins.CannotTransformContentToTypeException

const val SUCCESS = "SUCCESS"
const val FAILURE = "FAILURE"

suspend inline fun <reified T : Any> handleRequest(call: ApplicationCall, action: () -> T) {
    try {
        val result = action()
        call.respond(HttpStatusCode.OK, ApiResponse(SUCCESS, result))
    } catch (e: Exception) {
        when (e) {
            is EntityNotFoundException -> call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<T>(FAILURE, message = e.message ?: "Entity not found")
            )

            is CannotTransformContentToTypeException -> call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<T>(FAILURE, message = e.message ?: "Bad request")
            )

            else -> call.respond(
                HttpStatusCode.InternalServerError,
                ApiResponse<T>(FAILURE, message = e.message ?: "Unknown error")
            )
        }
    }
}

fun ApplicationCall.requireIntParameter(name: String): Int {
    return parameters[name]?.toIntOrNull()
        ?: throw IllegalArgumentException("Parameter $name must be a valid number")
}