package bros.parraga.routes

import bros.parraga.services.auth.AuthorizationService
import bros.parraga.services.repositories.league.LeagueRepository
import bros.parraga.services.repositories.league.dto.AddLeagueMemberRequest
import bros.parraga.services.repositories.league.dto.CreateLeagueRequest
import bros.parraga.services.repositories.league.dto.JoinLeagueRequest
import bros.parraga.services.repositories.league.dto.RecordLeagueMatchRequest
import bros.parraga.services.repositories.league.dto.UpdateLeagueRequest
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

fun Route.leagueRouting() {
    val leagueRepository: LeagueRepository by inject()
    val userRepository: UserRepository by inject()
    val authorizationService: AuthorizationService by inject()

    authenticate("clerk-jwt") {
        post("/leagues") {
            handleRequest(call, HttpStatusCode.Created) {
                val localUser = call.requireLocalUser(userRepository)
                leagueRepository.createLeague(localUser.id, call.receive<CreateLeagueRequest>())
            }
        }

        post("/leagues/join") {
            handleRequest(call) {
                val localUser = call.requireLocalUser(userRepository)
                leagueRepository.joinLeague(localUser.id, call.receive<JoinLeagueRequest>())
            }
        }

        get("/users/me/leagues") {
            handleRequest(call) {
                val localUser = call.requireLocalUser(userRepository)
                leagueRepository.getMyLeagues(localUser.id)
            }
        }

        route("/leagues/{id}") {
            get {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val leagueId = call.requireIntParameter("id")
                    authorizationService.requireLeagueMemberOrOwner(localUser.id, leagueId)
                    leagueRepository.getLeague(leagueId)
                }
            }

            put {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val leagueId = call.requireIntParameter("id")
                    authorizationService.requireLeagueOwner(localUser.id, leagueId)
                    leagueRepository.updateLeague(leagueId, call.receive<UpdateLeagueRequest>())
                }
            }

            delete {
                handleRequest(call, HttpStatusCode.NoContent) {
                    val localUser = call.requireLocalUser(userRepository)
                    val leagueId = call.requireIntParameter("id")
                    authorizationService.requireLeagueOwner(localUser.id, leagueId)
                    leagueRepository.deleteLeague(leagueId)
                }
            }

            post("/invite-code") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val leagueId = call.requireIntParameter("id")
                    authorizationService.requireLeagueOwner(localUser.id, leagueId)
                    leagueRepository.regenerateInviteCode(leagueId)
                }
            }

            get("/members") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val leagueId = call.requireIntParameter("id")
                    authorizationService.requireLeagueMemberOrOwner(localUser.id, leagueId)
                    leagueRepository.getMembers(leagueId)
                }
            }

            post("/members") {
                handleRequest(call, HttpStatusCode.Created) {
                    val localUser = call.requireLocalUser(userRepository)
                    val leagueId = call.requireIntParameter("id")
                    authorizationService.requireLeagueOwner(localUser.id, leagueId)
                    leagueRepository.addMember(leagueId, call.receive<AddLeagueMemberRequest>())
                }
            }

            delete("/members/{playerId}") {
                handleRequest(call, HttpStatusCode.NoContent) {
                    val localUser = call.requireLocalUser(userRepository)
                    val leagueId = call.requireIntParameter("id")
                    authorizationService.requireLeagueOwner(localUser.id, leagueId)
                    leagueRepository.removeMember(leagueId, call.requireIntParameter("playerId"))
                }
            }

            get("/matches") {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    val leagueId = call.requireIntParameter("id")
                    authorizationService.requireLeagueMemberOrOwner(localUser.id, leagueId)
                    leagueRepository.getMatches(leagueId)
                }
            }

            post("/matches") {
                handleRequest(call, HttpStatusCode.Created) {
                    val localUser = call.requireLocalUser(userRepository)
                    val leagueId = call.requireIntParameter("id")
                    val request = call.receive<RecordLeagueMatchRequest>()
                    authorizationService.requireLeagueResultRecorder(
                        localUser.id,
                        leagueId,
                        request.player1Id,
                        request.player2Id
                    )
                    leagueRepository.recordMatch(leagueId, localUser.id, request)
                }
            }

            delete("/matches/{matchId}") {
                handleRequest(call, HttpStatusCode.NoContent) {
                    val localUser = call.requireLocalUser(userRepository)
                    val leagueId = call.requireIntParameter("id")
                    authorizationService.requireLeagueOwner(localUser.id, leagueId)
                    leagueRepository.deleteMatch(leagueId, call.requireIntParameter("matchId"))
                }
            }
        }
    }
}
