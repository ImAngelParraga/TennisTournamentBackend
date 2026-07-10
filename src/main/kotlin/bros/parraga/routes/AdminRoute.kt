package bros.parraga.routes

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.ClubContactRequestDAO
import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentsTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.PublicUser
import bros.parraga.domain.TournamentStatus
import bros.parraga.services.auth.AuthorizationService
import bros.parraga.services.repositories.club.ClubContactRequestRepository
import bros.parraga.services.repositories.club.ClubRepository
import bros.parraga.services.repositories.user.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.koin.ktor.ext.inject

fun Route.adminRouting() {
    val authorizationService: AuthorizationService by inject()
    val userRepository: UserRepository by inject()
    val clubRepository: ClubRepository by inject()
    val clubContactRequestRepository: ClubContactRequestRepository by inject()

    route("/admin") {
        authenticate("clerk-jwt") {
            get("/overview") {
                handleAdminRequest(call, userRepository, authorizationService) {
                    getAdminOverview()
                }
            }

            get("/club-contact-requests") {
                handleAdminRequest(call, userRepository, authorizationService) {
                    clubContactRequestRepository.getContactRequests()
                }
            }

            delete("/club-contact-requests/{id}") {
                handleAdminRequest(call, userRepository, authorizationService, HttpStatusCode.NoContent) {
                    clubContactRequestRepository.deleteContactRequest(call.requireIntParameter("id"))
                }
            }

            get("/clubs") {
                handleAdminRequest(call, userRepository, authorizationService) {
                    getAdminClubs()
                }
            }

            post("/clubs") {
                handleAdminRequest(call, userRepository, authorizationService, HttpStatusCode.Created) {
                    clubRepository.createClub(call.receive())
                }
            }
        }
    }
}

private suspend inline fun <reified T : Any> handleAdminRequest(
    call: io.ktor.server.application.ApplicationCall,
    userRepository: UserRepository,
    authorizationService: AuthorizationService,
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    crossinline action: suspend () -> T
) {
    handleRequest(call, statusCode) {
        val localUser = call.requireLocalUser(userRepository)
        authorizationService.requirePlatformAdmin(localUser.id)
        action()
    }
}

private suspend fun getAdminOverview(): AdminOverview = dbQuery {
    AdminOverview(
        totalClubs = ClubDAO.all().count().toInt(),
        pendingClubContactRequests = ClubContactRequestDAO.all().count().toInt(),
        totalTournaments = TournamentDAO.all().count().toInt(),
        activeTournaments = TournamentDAO.find { TournamentsTable.status eq TournamentStatus.STARTED.name }.count().toInt(),
        completedTournaments = TournamentDAO.find { TournamentsTable.status eq TournamentStatus.COMPLETED.name }.count()
            .toInt(),
        totalUsers = UserDAO.all().count().toInt(),
        totalPlayers = PlayerDAO.all().count().toInt()
    )
}

private suspend fun getAdminClubs(): List<AdminClubSummary> = dbQuery {
    ClubDAO.all()
        .sortedBy { it.name.lowercase() }
        .map { club ->
            AdminClubSummary(
                id = club.id.value,
                name = club.name,
                phoneNumber = club.phoneNumber,
                address = club.address,
                owner = PublicUser(club.user.id.value, club.user.username),
                tournamentCount = TournamentDAO.find { TournamentsTable.clubId eq club.id }.count().toInt()
            )
        }
}

@Serializable
private data class AdminOverview(
    val totalClubs: Int,
    val pendingClubContactRequests: Int,
    val totalTournaments: Int,
    val activeTournaments: Int,
    val completedTournaments: Int,
    val totalUsers: Int,
    val totalPlayers: Int
)

@Serializable
private data class AdminClubSummary(
    val id: Int,
    val name: String,
    val phoneNumber: String?,
    val address: String?,
    val owner: PublicUser,
    val tournamentCount: Int
)
