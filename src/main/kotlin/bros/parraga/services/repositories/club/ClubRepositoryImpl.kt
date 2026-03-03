package bros.parraga.services.repositories.club

import bros.parraga.db.DatabaseFactory
import bros.parraga.db.schema.ClubAdminsTable
import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.ClubsTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import bros.parraga.domain.Club
import bros.parraga.domain.PublicUser
import bros.parraga.services.repositories.club.dto.CreateClubRequest
import bros.parraga.services.repositories.club.dto.UpdateClubRequest
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert

class ClubRepositoryImpl : ClubRepository {
    override suspend fun getClubs(): List<Club> = DatabaseFactory.dbQuery {
        ClubDAO.all().map { it.toDomain() }
    }

    override suspend fun getClub(id: Int): Club = DatabaseFactory.dbQuery {
        ClubDAO[id].toDomain()
    }

    override suspend fun createClub(request: CreateClubRequest, ownerUserId: Int): Club = DatabaseFactory.dbQuery {
        ClubDAO.new {
            name = request.name
            phoneNumber = request.phoneNumber
            address = request.address
            user = UserDAO[ownerUserId]
        }.toDomain()
    }

    override suspend fun updateClub(request: UpdateClubRequest): Club = DatabaseFactory.dbQuery {
        ClubDAO.findByIdAndUpdate(request.id) {
            it.apply {
                request.name?.let { name = it }
                request.phoneNumber?.let { phoneNumber = it }
                request.address?.let { address = it }
            }
        }?.toDomain() ?: throw EntityNotFoundException(DaoEntityID(request.id, ClubsTable), ClubDAO)
    }

    override suspend fun deleteClub(id: Int) = DatabaseFactory.dbQuery {
        ClubDAO[id].delete()
    }

    override suspend fun getClubAdmins(clubId: Int): List<PublicUser> = DatabaseFactory.dbQuery {
        getClubAdminsInternal(clubId)
    }

    override suspend fun addClubAdmin(clubId: Int, userId: Int): List<PublicUser> = DatabaseFactory.dbQuery {
        val club = ClubDAO[clubId]
        UserDAO[userId]
        if (club.admins.none { it.id.value == userId }) {
            ClubAdminsTable.insert {
                it[this.clubId] = DaoEntityID(clubId, ClubsTable)
                it[this.userId] = DaoEntityID(userId, UsersTable)
            }
        }
        getClubAdminsInternal(clubId)
    }

    override suspend fun removeClubAdmin(clubId: Int, userId: Int): List<PublicUser> = DatabaseFactory.dbQuery {
        ClubDAO[clubId]
        UserDAO[userId]
        ClubAdminsTable.deleteWhere {
            (ClubAdminsTable.clubId eq clubId) and (ClubAdminsTable.userId eq userId)
        }
        getClubAdminsInternal(clubId)
    }

    private fun getClubAdminsInternal(clubId: Int): List<PublicUser> {
        val club = ClubDAO[clubId]
        return club.admins
            .map { PublicUser(it.id.value, it.username) }
            .sortedBy { it.id }
    }
}
