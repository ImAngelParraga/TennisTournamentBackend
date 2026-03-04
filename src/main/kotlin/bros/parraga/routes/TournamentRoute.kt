package bros.parraga.routes

import bros.parraga.services.auth.AuthorizationService
import bros.parraga.services.repositories.tournament.TournamentRepository
import bros.parraga.services.repositories.tournament.dto.CreateTournamentRequest
import bros.parraga.services.repositories.tournament.dto.UpdateTournamentRequest
import bros.parraga.services.repositories.user.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.tournamentRouting() {
    val tournamentRepository: TournamentRepository by inject()
    val userRepository: UserRepository by inject()
    val authorizationService: AuthorizationService by inject()

    route("/tournaments") {
        get {
            handleRequest(call) { tournamentRepository.getTournaments() }
        }

        get("/{id}") {
            handleRequest(call) { tournamentRepository.getTournament(call.requireIntParameter("id")) }
        }

        route("/{id}/phases") {
            get {
                handleRequest(call) {
                    tournamentRepository.getTournamentPhases(call.requireIntParameter("id"))
                }
            }
        }

        route("/{id}/players") {
            get {
                handleRequest(call) {
                    tournamentRepository.getTournamentPlayers(call.requireIntParameter("id"))
                }
            }
        }

        get("/{id}/matches") {
            handleRequest(call) {
                tournamentRepository.getTournamentMatches(call.requireIntParameter("id"))
            }
        }

        get("/{id}/bracket") {
            handleRequest(call) {
                tournamentRepository.getTournamentBracket(call.requireIntParameter("id"))
            }
        }

        authenticate("clerk-jwt") {
            post {
                handleRequest(call, HttpStatusCode.Created) {
                    val localUser = call.requireLocalUser(userRepository)
                    val request = call.receive<CreateTournamentRequest>()
                    authorizationService.requireClubManager(localUser.id, request.clubId)
                    tournamentRepository.createTournament(request)
                }
            }

            put {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val request = call.receive<UpdateTournamentRequest>()
                    authorizationService.requireTournamentManager(localUser.id, request.id)
                    request.clubId?.let { authorizationService.requireClubManager(localUser.id, it) }
                    tournamentRepository.updateTournament(request)
                }
            }

            delete("/{id}") {
                handleRequest(call, HttpStatusCode.NoContent) {
                    val localUser = call.requireLocalUser(userRepository)
                    val tournamentId = call.requireIntParameter("id")
                    authorizationService.requireTournamentManager(localUser.id, tournamentId)
                    tournamentRepository.deleteTournament(tournamentId)
                }
            }

            post("/{id}/start") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val tournamentId = call.requireIntParameter("id")
                    authorizationService.requireTournamentManager(localUser.id, tournamentId)
                    tournamentRepository.startTournament(tournamentId)
                }
            }

            post("/{id}/reset") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val tournamentId = call.requireIntParameter("id")
                    authorizationService.requireTournamentManager(localUser.id, tournamentId)
                    tournamentRepository.resetTournament(tournamentId)
                }
            }

            post("/{id}/phases") {
                handleRequest(call, HttpStatusCode.Created) {
                    val localUser = call.requireLocalUser(userRepository)
                    val tournamentId = call.requireIntParameter("id")
                    authorizationService.requireTournamentManager(localUser.id, tournamentId)
                    tournamentRepository.createPhase(tournamentId, call.receive())
                }
            }

            post("/{id}/players") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val tournamentId = call.requireIntParameter("id")
                    authorizationService.requireTournamentManager(localUser.id, tournamentId)
                    tournamentRepository.addPlayersToTournament(tournamentId, call.receive())
                }
            }

            delete("/{id}/players/{playerId}") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val tournamentId = call.requireIntParameter("id")
                    val playerId = call.requireIntParameter("playerId")
                    authorizationService.requireTournamentManager(localUser.id, tournamentId)
                    tournamentRepository.removePlayerFromTournament(tournamentId, playerId)
                }
            }
        }
    }
}
