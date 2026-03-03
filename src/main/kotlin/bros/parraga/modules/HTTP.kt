package bros.parraga.modules

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import java.net.URI

fun Application.configureHTTP(isTest: Boolean = false) {
    val authConfig = loadAuthConfig(isTest)

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("MyCustomHeader")
        authConfig.allowedOrigins.forEach { origin ->
            val parsed = URI(origin)
            val scheme = parsed.scheme ?: throw IllegalArgumentException("Invalid ALLOWED_ORIGINS entry: $origin")
            val host = parsed.host ?: throw IllegalArgumentException("Invalid ALLOWED_ORIGINS entry: $origin")
            val port = parsed.port
            val hostWithPort = if (port == -1) host else "$host:$port"
            allowHost(hostWithPort, schemes = listOf(scheme))
        }
    }
}
