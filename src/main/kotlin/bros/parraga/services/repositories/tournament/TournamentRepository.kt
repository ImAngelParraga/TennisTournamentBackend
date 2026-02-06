package bros.parraga.services.repositories.tournament

import bros.parraga.domain.Match
import bros.parraga.domain.Player
import bros.parraga.domain.TournamentBasic
import bros.parraga.domain.TournamentBracket
import bros.parraga.domain.TournamentPhase
import bros.parraga.domain.TournamentPhaseSummary
import bros.parraga.services.repositories.tournament.dto.AddPlayersRequest
import bros.parraga.services.repositories.tournament.dto.CreatePhaseRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentRequest
import bros.parraga.services.repositories.tournament.dto.UpdateTournamentRequest

interface TournamentRepository {
    suspend fun getTournaments(): List<TournamentBasic>
    suspend fun getTournament(id: Int): TournamentBasic
    suspend fun createTournament(request: CreateTournamentRequest): TournamentBasic
    suspend fun updateTournament(request: UpdateTournamentRequest): TournamentBasic
    suspend fun deleteTournament(id: Int)
    suspend fun createPhase(tournamentId: Int, request: CreatePhaseRequest): TournamentPhase
    suspend fun getTournamentPlayers(tournamentId: Int): List<Player>
    suspend fun getTournamentPhases(tournamentId: Int): List<TournamentPhaseSummary>
    suspend fun getTournamentMatches(tournamentId: Int): List<Match>
    suspend fun getTournamentBracket(tournamentId: Int): TournamentBracket
    suspend fun addPlayersToTournament(tournamentId: Int, request: AddPlayersRequest)
    suspend fun removePlayerFromTournament(tournamentId: Int, playerId: Int)
    suspend fun startTournament(id: Int): TournamentPhase
}
