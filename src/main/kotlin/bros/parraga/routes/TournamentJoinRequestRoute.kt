package bros.parraga.routes

import bros.parraga.domain.TournamentJoinRequest
import bros.parraga.domain.TournamentJoinRequestStatus
import bros.parraga.services.auth.AuthorizationService
import bros.parraga.services.repositories.tournament.TournamentJoinRequestRepository
import bros.parraga.services.repositories.tournament.dto.AcceptTournamentJoinRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentJoinRequest
import bros.parraga.services.repositories.tournament.dto.DecideTournamentJoinRequest
import bros.parraga.services.repositories.user.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.tournamentJoinRequestRouting() {
    val joinRequestRepository: TournamentJoinRequestRepository by inject()
    val userRepository: UserRepository by inject()
    val authorizationService: AuthorizationService by inject()

    authenticate("clerk-jwt") {
        route("/tournaments/{id}/join-requests") {
            post {
                handleRequestWithStatus<TournamentJoinRequest>(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val result = joinRequestRepository.createJoinRequest(
                        tournamentId = call.requireIntParameter("id"),
                        userId = localUser.id,
                        request = call.receive<CreateTournamentJoinRequest>()
                    )
                    val status = if (result.created) HttpStatusCode.Created else HttpStatusCode.OK
                    status to result.request
                }
            }

            get {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val tournamentId = call.requireIntParameter("id")
                    authorizationService.requireTournamentManager(localUser.id, tournamentId)
                    joinRequestRepository.getJoinRequestsForTournament(tournamentId, call.optionalJoinRequestStatus())
                }
            }

            post("/{requestId}/withdraw") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    joinRequestRepository.withdrawJoinRequest(
                        tournamentId = call.requireIntParameter("id"),
                        requestId = call.requireIntParameter("requestId"),
                        userId = localUser.id
                    )
                }
            }

            post("/{requestId}/accept") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val tournamentId = call.requireIntParameter("id")
                    authorizationService.requireTournamentManager(localUser.id, tournamentId)
                    joinRequestRepository.acceptJoinRequest(
                        tournamentId = tournamentId,
                        requestId = call.requireIntParameter("requestId"),
                        managerUserId = localUser.id,
                        request = call.receive<AcceptTournamentJoinRequest>()
                    )
                }
            }

            post("/{requestId}/reject") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val tournamentId = call.requireIntParameter("id")
                    authorizationService.requireTournamentManager(localUser.id, tournamentId)
                    joinRequestRepository.rejectJoinRequest(
                        tournamentId = tournamentId,
                        requestId = call.requireIntParameter("requestId"),
                        managerUserId = localUser.id,
                        request = call.receive<DecideTournamentJoinRequest>()
                    )
                }
            }

            post("/{requestId}/allow-resubmit") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val tournamentId = call.requireIntParameter("id")
                    authorizationService.requireTournamentManager(localUser.id, tournamentId)
                    joinRequestRepository.allowResubmit(
                        tournamentId = tournamentId,
                        requestId = call.requireIntParameter("requestId"),
                        managerUserId = localUser.id
                    )
                }
            }
        }

        get("/users/me/tournament-join-requests") {
            handleRequest(call) {
                val localUser = call.requireLocalUser(userRepository)
                joinRequestRepository.getJoinRequestsForUser(localUser.id, call.optionalJoinRequestStatus())
            }
        }
    }
}

private fun ApplicationCall.optionalJoinRequestStatus(): TournamentJoinRequestStatus? {
    val rawStatus = request.queryParameters["status"]?.takeIf { it.isNotBlank() } ?: return null
    return try {
        TournamentJoinRequestStatus.valueOf(rawStatus.uppercase())
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Query parameter status must be one of ${TournamentJoinRequestStatus.entries.joinToString()}")
    }
}
