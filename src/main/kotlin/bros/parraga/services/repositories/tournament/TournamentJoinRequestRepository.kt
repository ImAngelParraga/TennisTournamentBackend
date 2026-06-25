package bros.parraga.services.repositories.tournament

import bros.parraga.domain.TournamentJoinRequest
import bros.parraga.domain.TournamentJoinRequestStatus
import bros.parraga.services.repositories.tournament.dto.AcceptTournamentJoinRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentJoinRequest
import bros.parraga.services.repositories.tournament.dto.DecideTournamentJoinRequest

data class CreateJoinRequestResult(
    val request: TournamentJoinRequest,
    val created: Boolean
)

interface TournamentJoinRequestRepository {
    suspend fun createJoinRequest(
        tournamentId: Int,
        userId: Int,
        request: CreateTournamentJoinRequest
    ): CreateJoinRequestResult

    suspend fun withdrawJoinRequest(tournamentId: Int, requestId: Int, userId: Int): TournamentJoinRequest

    suspend fun getJoinRequestsForTournament(
        tournamentId: Int,
        status: TournamentJoinRequestStatus?
    ): List<TournamentJoinRequest>

    suspend fun getJoinRequestsForUser(userId: Int, status: TournamentJoinRequestStatus?): List<TournamentJoinRequest>

    suspend fun acceptJoinRequest(
        tournamentId: Int,
        requestId: Int,
        managerUserId: Int,
        request: AcceptTournamentJoinRequest
    ): TournamentJoinRequest

    suspend fun rejectJoinRequest(
        tournamentId: Int,
        requestId: Int,
        managerUserId: Int,
        request: DecideTournamentJoinRequest
    ): TournamentJoinRequest

    suspend fun allowResubmit(tournamentId: Int, requestId: Int, managerUserId: Int): TournamentJoinRequest
}
