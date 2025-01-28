package bros.parraga.routes

import bros.parraga.services.repositories.tournament.TournamentRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.tournamentRouting() {
    val tournamentRepository: TournamentRepository by inject()

    route("/tournaments") {
        get {
            handleRequest(call) { tournamentRepository.getTournaments() }
        }

        post {
            handleRequest(call) {
                tournamentRepository.createTournament(call.receive())
            }
        }

        put() {
            handleRequest(call) {
                tournamentRepository.updateTournament(call.receive())
            }
        }

        route("/{id}") {
            get {
                handleRequest(call) { tournamentRepository.getTournament(call.requireIntParameter("id")) }
            }

            delete {
                handleRequest(
                    call,
                    HttpStatusCode.NoContent
                ) { tournamentRepository.deleteTournament(call.requireIntParameter("id")) }
            }

            route("/players") {
                post {
                    handleRequest(call) {
                        tournamentRepository.addPlayersToTournament(
                            call.requireIntParameter("id"),
                            call.receive()
                        )
                    }
                }

                delete("/{playerId}") {
                    handleRequest(call) {
                        tournamentRepository.removePlayerFromTournament(
                            tournamentId = call.requireIntParameter("id"),
                            playerId = call.requireIntParameter("playerId")
                        )
                    }
                }
            }
        }

    }
}
