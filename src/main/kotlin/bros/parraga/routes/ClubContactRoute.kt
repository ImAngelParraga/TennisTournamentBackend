package bros.parraga.routes

import bros.parraga.services.auth.AuthorizationService
import bros.parraga.services.repositories.club.ClubContactRequestRepository
import bros.parraga.services.repositories.user.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.clubContactRouting() {
    val clubContactRequestRepository: ClubContactRequestRepository by inject()
    val userRepository: UserRepository by inject()
    val authorizationService: AuthorizationService by inject()

    route("/club-contact-requests") {
        // Public: the contact form for clubs that want to be onboarded. Clubs are
        // provisioned manually, so this only records the inquiry for the operator.
        post {
            handleRequest(call, HttpStatusCode.Created) {
                clubContactRequestRepository.createContactRequest(call.receive())
            }
        }

        authenticate("clerk-jwt") {
            get {
                handleRequest(call) {
                    val localUser = call.requireLocalUser(userRepository)
                    authorizationService.requirePlatformAdmin(localUser.id)
                    clubContactRequestRepository.getContactRequests()
                }
            }

            // Clears a handled inquiry (the "accept" flow is: read → create club → delete).
            delete("/{id}") {
                handleRequest(call, HttpStatusCode.NoContent) {
                    val localUser = call.requireLocalUser(userRepository)
                    authorizationService.requirePlatformAdmin(localUser.id)
                    clubContactRequestRepository.deleteContactRequest(call.requireIntParameter("id"))
                }
            }
        }
    }
}
