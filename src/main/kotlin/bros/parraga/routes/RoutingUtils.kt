package bros.parraga.routes

import bros.parraga.errors.ConflictException
import bros.parraga.errors.ForbiddenException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

            is CannotTransformContentToTypeException,
            is ContentTransformationException,
            is BadRequestException,
            is SerializationException -> call.respond(
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

fun ApplicationCall.requireLocalDateQueryParameter(name: String): LocalDate {
    val value = request.queryParameters[name]
        ?: throw IllegalArgumentException("Query parameter $name is required")
    return try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        throw IllegalArgumentException("Query parameter $name must use ISO format YYYY-MM-DD")
    }
}

fun ApplicationCall.getZoneIdQueryParameter(name: String, default: ZoneId = ZoneId.of("UTC")): ZoneId {
    val value = request.queryParameters[name] ?: return default
    return try {
        ZoneId.of(value)
    } catch (_: DateTimeException) {
        throw IllegalArgumentException("Query parameter $name must be a valid IANA time zone")
    }
}

fun validateLocalDateRange(from: LocalDate, to: LocalDate) {
    require(!from.isAfter(to)) { "Query parameter from must be on or before to" }
}

fun validateLocalDateRange(from: LocalDate, to: LocalDate, maxDaysInclusive: Int) {
    validateLocalDateRange(from, to)
    val totalDays = ChronoUnit.DAYS.between(from, to) + 1
    require(totalDays <= maxDaysInclusive) {
        "Query range must not exceed $maxDaysInclusive days"
    }
}

fun validateInstantRange(from: Instant, to: Instant, maxDays: Int) {
    require(from <= to) { "Query parameter from must be on or before to" }

    val maxRangeMillis = maxDays * 24L * 60L * 60L * 1000L
    require(to.toEpochMilliseconds() - from.toEpochMilliseconds() <= maxRangeMillis) {
        "Query range must not exceed $maxDays days"
    }
}
