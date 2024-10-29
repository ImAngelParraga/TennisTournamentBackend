package bros.parraga.services.repositories

import bros.parraga.domain.Tournament

interface TournamentRepository {
    suspend fun getTournaments(): List<Tournament>
    suspend fun getTournament(id: Int): Tournament
    suspend fun createTournament(tournament: Tournament): Tournament
    suspend fun updateTournament(tournament: Tournament): Tournament
    suspend fun deleteTournament(id: Int)
}