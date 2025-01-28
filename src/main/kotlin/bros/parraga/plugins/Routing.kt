package bros.parraga.plugins

import bros.parraga.routes.clubRouting
import bros.parraga.routes.playerRouting
import bros.parraga.routes.tournamentRouting
import bros.parraga.routes.userRouting
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        tournamentRouting()
        playerRouting()
        clubRouting()
        userRouting()
    }
}
