package bros.parraga.services.repositories.club

import bros.parraga.db.DatabaseFactory
import bros.parraga.db.schema.ClubContactRequestDAO
import bros.parraga.domain.ClubContactRequest
import bros.parraga.services.repositories.club.dto.CreateClubContactRequest

class ClubContactRequestRepositoryImpl : ClubContactRequestRepository {
    override suspend fun createContactRequest(request: CreateClubContactRequest): ClubContactRequest {
        val clubName = request.clubName.trim()
        val contactName = request.contactName.trim()
        val email = request.email.trim()
        val phone = request.phone?.trim()?.takeIf { it.isNotEmpty() }
        val message = request.message?.trim()?.takeIf { it.isNotEmpty() }

        require(clubName.isNotEmpty()) { "clubName is required" }
        require(contactName.isNotEmpty()) { "contactName is required" }
        require(email.isNotEmpty()) { "email is required" }
        require(email.matches(EMAIL_REGEX)) { "email is not valid" }
        require(clubName.length <= 255) { "clubName is too long (max 255)" }
        require(contactName.length <= 255) { "contactName is too long (max 255)" }
        require(email.length <= 255) { "email is too long (max 255)" }
        require((phone?.length ?: 0) <= 50) { "phone is too long (max 50)" }
        require((message?.length ?: 0) <= MAX_MESSAGE_LENGTH) { "message is too long (max $MAX_MESSAGE_LENGTH)" }

        return DatabaseFactory.dbQuery {
            ClubContactRequestDAO.new {
                this.clubName = clubName
                this.contactName = contactName
                this.email = email
                this.phone = phone
                this.message = message
            }.toDomain()
        }
    }

    override suspend fun getContactRequests(): List<ClubContactRequest> = DatabaseFactory.dbQuery {
        ClubContactRequestDAO.all()
            .sortedByDescending { it.createdAt }
            .map { it.toDomain() }
    }

    override suspend fun deleteContactRequest(id: Int) = DatabaseFactory.dbQuery {
        ClubContactRequestDAO[id].delete()
    }

    private companion object {
        // Loose shape check — real validation is the operator replying to the address.
        val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        const val MAX_MESSAGE_LENGTH = 4000
    }
}
