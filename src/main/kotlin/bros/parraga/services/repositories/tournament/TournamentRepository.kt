package bros.parraga.services.repositories.tournament

import bros.parraga.domain.Tournament
import bros.parraga.services.repositories.tournament.dto.AddPlayersRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentRequest
import bros.parraga.services.repositories.tournament.dto.UpdateTournamentRequest

interface TournamentRepository {
    suspend fun getTournaments(): List<Tournament>
    suspend fun getTournament(id: Int): Tournament
    suspend fun createTournament(request: CreateTournamentRequest): Tournament
    suspend fun updateTournament(request: UpdateTournamentRequest): Tournament
    suspend fun deleteTournament(id: Int)
    suspend fun addPlayersToTournament(tournamentId: Int, request: AddPlayersRequest)
    suspend fun removePlayerFromTournament(tournamentId: Int, playerId: Int)
}