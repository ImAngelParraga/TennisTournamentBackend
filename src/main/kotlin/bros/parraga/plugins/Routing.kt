package bros.parraga.plugins

import bros.parraga.routes.tournamentRouting
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        tournamentRouting()
    }
}
