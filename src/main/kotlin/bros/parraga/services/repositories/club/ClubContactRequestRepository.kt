package bros.parraga.services.repositories.club

import bros.parraga.domain.ClubContactRequest
import bros.parraga.services.repositories.club.dto.CreateClubContactRequest

interface ClubContactRequestRepository {
    suspend fun createContactRequest(request: CreateClubContactRequest): ClubContactRequest
    suspend fun getContactRequests(): List<ClubContactRequest>
    suspend fun deleteContactRequest(id: Int)
}
