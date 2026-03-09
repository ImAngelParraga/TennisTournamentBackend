package bros.parraga

import bros.parraga.modules.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val authTestMode = System.getenv("AUTH_TEST_MODE")?.toBooleanStrictOrNull() ?: false

    configureKoin()
    configureSecurity(isTest = authTestMode)
    configureHTTP(isTest = authTestMode)
    configureSerialization()
    configureRouting()
    configureDatabase()
}

fun Application.testModule() {
    configureSecurity(isTest = true)
    configureHTTP(isTest = true)
    configureSerialization()
    configureKoin()
    configureRouting()
}
