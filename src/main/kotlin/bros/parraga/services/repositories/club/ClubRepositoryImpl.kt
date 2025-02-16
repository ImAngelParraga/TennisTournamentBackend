package bros.parraga.services.repositories.club

import bros.parraga.db.DatabaseFactory
import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.ClubsTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.Club
import bros.parraga.services.repositories.club.dto.CreateClubRequest
import bros.parraga.services.repositories.club.dto.UpdateClubRequest
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException

class ClubRepositoryImpl : ClubRepository {
    override suspend fun getClubs(): List<Club> = DatabaseFactory.dbQuery {
        ClubDAO.all().map { it.toDomain() }
    }

    override suspend fun getClub(id: Int): Club = DatabaseFactory.dbQuery {
        ClubDAO[id].toDomain()
    }

    override suspend fun createClub(request: CreateClubRequest): Club = DatabaseFactory.dbQuery {
        ClubDAO.new {
            name = request.name
            phoneNumber = request.phoneNumber
            address = request.address
            user = UserDAO[request.userId]
        }.toDomain()
    }

    override suspend fun updateClub(request: UpdateClubRequest): Club = DatabaseFactory.dbQuery {
        ClubDAO.findByIdAndUpdate(request.id) {
            it.apply {
                request.name?.let { name = it }
                request.phoneNumber?.let { phoneNumber = it }
                request.address?.let { address = it }
                request.userId?.let { user = UserDAO[it] }
            }
        }?.toDomain() ?: throw EntityNotFoundException(DaoEntityID(request.id, ClubsTable), ClubDAO)
    }

    override suspend fun deleteClub(id: Int) = DatabaseFactory.dbQuery {
        ClubDAO[id].delete()
    }
}