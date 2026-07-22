package bros.parraga.services.repositories.league

import bros.parraga.domain.League
import bros.parraga.domain.LeagueMatch
import bros.parraga.domain.LeagueMember
import bros.parraga.services.repositories.league.dto.AddLeagueMemberRequest
import bros.parraga.services.repositories.league.dto.CreateLeagueRequest
import bros.parraga.services.repositories.league.dto.JoinLeagueRequest
import bros.parraga.services.repositories.league.dto.RecordLeagueMatchRequest
import bros.parraga.services.repositories.league.dto.UpdateLeagueRequest

interface LeagueRepository {
    suspend fun createLeague(ownerUserId: Int, request: CreateLeagueRequest): League
    suspend fun getMyLeagues(userId: Int): List<League>
    suspend fun getLeague(id: Int): League
    suspend fun updateLeague(id: Int, request: UpdateLeagueRequest): League
    suspend fun deleteLeague(id: Int)
    suspend fun joinLeague(userId: Int, request: JoinLeagueRequest): League
    suspend fun addMember(id: Int, request: AddLeagueMemberRequest): LeagueMember
    suspend fun removeMember(id: Int, playerId: Int)
    suspend fun getMembers(id: Int): List<LeagueMember>
    suspend fun getMatches(id: Int): List<LeagueMatch>
    suspend fun recordMatch(id: Int, createdByUserId: Int, request: RecordLeagueMatchRequest): LeagueMatch
    suspend fun deleteMatch(id: Int, matchId: Int)
    suspend fun regenerateInviteCode(id: Int): League
}

