package bros.parraga.services.repositories.club

import bros.parraga.domain.Club
import bros.parraga.domain.PublicUser
import bros.parraga.services.repositories.club.dto.CreateClubRequest
import bros.parraga.services.repositories.club.dto.UpdateClubRequest

interface ClubRepository {
    suspend fun getClubs(): List<Club>
    suspend fun getClub(id: Int): Club
    suspend fun createClub(request: CreateClubRequest, ownerUserId: Int): Club
    suspend fun updateClub(request: UpdateClubRequest): Club
    suspend fun deleteClub(id: Int)
    suspend fun getClubAdmins(clubId: Int): List<PublicUser>
    suspend fun addClubAdmin(clubId: Int, userId: Int): List<PublicUser>
    suspend fun removeClubAdmin(clubId: Int, userId: Int): List<PublicUser>
}
