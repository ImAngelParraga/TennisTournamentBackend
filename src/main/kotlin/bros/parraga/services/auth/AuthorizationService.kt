package bros.parraga.services.auth

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.ClubsTable
import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.MatchesTable
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentsTable
import bros.parraga.errors.ForbiddenException
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException

class AuthorizationService {
    suspend fun isClubOwner(userId: Int, clubId: Int): Boolean = dbQuery {
        val club = ClubDAO.findById(clubId)
            ?: throw EntityNotFoundException(DaoEntityID(clubId, ClubsTable), ClubDAO)
        club.user.id.value == userId
    }

    suspend fun isClubOwnerOrAdmin(userId: Int, clubId: Int): Boolean = dbQuery {
        val club = ClubDAO.findById(clubId)
            ?: throw EntityNotFoundException(DaoEntityID(clubId, ClubsTable), ClubDAO)
        club.user.id.value == userId || club.admins.any { it.id.value == userId }
    }

    suspend fun requireClubManager(userId: Int, clubId: Int) {
        if (!isClubOwnerOrAdmin(userId, clubId)) {
            throw ForbiddenException("You do not have permission to manage this club")
        }
    }

    suspend fun requireTournamentManager(userId: Int, tournamentId: Int) {
        val clubId = dbQuery {
            val tournament = TournamentDAO.findById(tournamentId)
                ?: throw EntityNotFoundException(DaoEntityID(tournamentId, TournamentsTable), TournamentDAO)
            tournament.club.id.value
        }
        requireClubManager(userId, clubId)
    }

    suspend fun requireMatchManager(userId: Int, matchId: Int) {
        val clubId = dbQuery {
            val match = MatchDAO.findById(matchId)
                ?: throw EntityNotFoundException(DaoEntityID(matchId, MatchesTable), MatchDAO)
            match.phase.tournament.club.id.value
        }
        requireClubManager(userId, clubId)
    }
}
