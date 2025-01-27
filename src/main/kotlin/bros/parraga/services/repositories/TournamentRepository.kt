package bros.parraga.services.repositories

import bros.parraga.domain.Tournament
import bros.parraga.routes.dto.CreateTournamentRequest
import bros.parraga.routes.dto.UpdateTournamentRequest

interface TournamentRepository {
    suspend fun getTournaments(): List<Tournament>
    suspend fun getTournament(id: Int): Tournament
    suspend fun createTournament(request: CreateTournamentRequest): Tournament
    suspend fun updateTournament(request: UpdateTournamentRequest): Tournament
    suspend fun deleteTournament(id: Int)
}