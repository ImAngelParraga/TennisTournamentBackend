package bros.parraga.services.repositories.club

import bros.parraga.domain.Club
import bros.parraga.services.repositories.club.dto.CreateClubRequest
import bros.parraga.services.repositories.club.dto.UpdateClubRequest

interface ClubRepository {
    suspend fun getClubs(): List<Club>
    suspend fun getClub(id: Int): Club
    suspend fun createClub(request: CreateClubRequest): Club
    suspend fun updateClub(request: UpdateClubRequest): Club
    suspend fun deleteClub(id: Int)
}