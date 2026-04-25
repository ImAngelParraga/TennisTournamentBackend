package bros.parraga.routes

import bros.parraga.errors.ConflictException
import bros.parraga.errors.ForbiddenException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import java.time.YearMonth

const val SUCCESS = "SUCCESS"
const val FAILURE = "FAILURE"

suspend inline fun <reified T : Any> handleRequest(
    call: ApplicationCall,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    action: () -> T
) {
    try {
        val result = action()
        call.respond(statusCode, ApiResponse(SUCCESS, result))
    } catch (e: Exception) {
        when (e) {
            is EntityNotFoundException, is NotFoundException -> call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<T>(FAILURE, message = e.message ?: "Entity not found")
            )

            is CannotTransformContentToTypeException -> call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<T>(FAILURE, message = e.message ?: "Bad request")
            )

            is IllegalArgumentException -> call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<T>(FAILURE, message = e.message ?: "Bad request")
            )

            is ForbiddenException -> call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<T>(FAILURE, message = e.message ?: "Forbidden")
            )

            is ConflictException -> call.respond(
                HttpStatusCode.Conflict,
                ApiResponse<T>(FAILURE, message = e.message ?: "Conflict")
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

fun ApplicationCall.requireInstantQueryParameter(name: String): Instant {
    val value = request.queryParameters[name]
        ?: throw IllegalArgumentException("Query parameter $name is required")
    return try {
        Instant.parse(value)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Query parameter $name must be a valid ISO-8601 instant")
    }
}

fun ApplicationCall.requireYearMonthQueryParameter(name: String): YearMonth {
    val value = request.queryParameters[name]
        ?: throw IllegalArgumentException("Query parameter $name is required")
    return try {
        YearMonth.parse(value)
    } catch (_: Exception) {
        throw IllegalArgumentException("Query parameter $name must use ISO format YYYY-MM")
    }
}

fun validateInstantRange(from: Instant, to: Instant, maxDays: Int) {
    require(from <= to) { "Query parameter from must be on or before to" }

    val maxRangeMillis = maxDays * 24L * 60L * 60L * 1000L
    require(to.toEpochMilliseconds() - from.toEpochMilliseconds() <= maxRangeMillis) {
        "Query range must not exceed $maxDays days"
    }
}
