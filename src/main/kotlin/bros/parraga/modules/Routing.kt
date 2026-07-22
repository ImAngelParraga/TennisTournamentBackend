package bros.parraga.modules

import bros.parraga.routes.clubContactRouting
import bros.parraga.routes.clubRouting
import bros.parraga.routes.adminRouting
import bros.parraga.routes.leagueRouting
import bros.parraga.routes.matchRouting
import bros.parraga.routes.playerRouting
import bros.parraga.routes.racketRouting
import bros.parraga.routes.trainingRouting
import bros.parraga.routes.tournamentJoinRequestRouting
import bros.parraga.routes.tournamentRouting
import bros.parraga.routes.userRouting
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        adminRouting()
        tournamentRouting()
        tournamentJoinRequestRouting()
        leagueRouting()
        playerRouting()
        clubRouting()
        clubContactRouting()
        racketRouting()
        trainingRouting()
        userRouting()
        matchRouting()
    }
}
