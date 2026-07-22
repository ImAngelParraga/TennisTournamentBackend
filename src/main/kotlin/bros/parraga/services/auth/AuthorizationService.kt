package bros.parraga.services.auth

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.ClubsTable
import bros.parraga.db.schema.LeagueDAO
import bros.parraga.db.schema.LeagueMembersTable
import bros.parraga.db.schema.LeaguesTable
import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.MatchesTable
import bros.parraga.db.schema.PlayersTable
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentsTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import bros.parraga.domain.UserRole
import bros.parraga.errors.ForbiddenException
import io.ktor.server.plugins.NotFoundException
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

class AuthorizationService {
    suspend fun isPlatformAdmin(userId: Int): Boolean = dbQuery {
        val user = UserDAO.findById(userId)
            ?: throw EntityNotFoundException(DaoEntityID(userId, UsersTable), UserDAO)
        user.role == UserRole.PLATFORM_ADMIN.name
    }

    suspend fun requirePlatformAdmin(userId: Int) {
        if (!isPlatformAdmin(userId)) {
            throw ForbiddenException("Platform administrator role required")
        }
    }

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
        val manager = dbQuery {
            val tournament = TournamentDAO.findById(tournamentId)
                ?: throw EntityNotFoundException(DaoEntityID(tournamentId, TournamentsTable), TournamentDAO)
            tournament.owner?.let { owner -> return@dbQuery owner.id.value == userId }
            tournament.club?.id?.value ?: return@dbQuery false
        }
        if (manager is Boolean) {
            if (!manager) throw ForbiddenException("You do not have permission to manage this tournament")
        } else {
            requireClubManager(userId, manager as Int)
        }
    }

    suspend fun requireTournamentReadable(userId: Int?, tournamentId: Int) {
        val allowed = dbQuery {
            val tournament = TournamentDAO.findById(tournamentId)
                ?: throw EntityNotFoundException(DaoEntityID(tournamentId, TournamentsTable), TournamentDAO)
            if (tournament.visibility == "PUBLIC") return@dbQuery true
            if (userId == null) return@dbQuery false
            if (tournament.owner?.id?.value == userId) return@dbQuery true
            val playerId = PlayersTable
                .selectAll()
                .where { PlayersTable.userId eq userId }
                .map { it[PlayersTable.id].value }
                .firstOrNull() ?: return@dbQuery false
            bros.parraga.db.schema.TournamentPlayersTable
                .selectAll()
                .where {
                    (bros.parraga.db.schema.TournamentPlayersTable.tournamentId eq tournamentId) and
                        (bros.parraga.db.schema.TournamentPlayersTable.playerId eq playerId)
                }
                .count() > 0
        }
        if (!allowed) throw NotFoundException("Tournament $tournamentId not found")
    }

    suspend fun requireMatchManager(userId: Int, matchId: Int) {
        val manager = dbQuery {
            val match = MatchDAO.findById(matchId)
                ?: throw EntityNotFoundException(DaoEntityID(matchId, MatchesTable), MatchDAO)
            val tournament = match.phase.tournament
            tournament.owner?.let { owner -> return@dbQuery owner.id.value == userId }
            tournament.club?.id?.value ?: return@dbQuery false
        }
        if (manager is Boolean) {
            if (!manager) throw ForbiddenException("You do not have permission to manage this match")
        } else {
            requireClubManager(userId, manager as Int)
        }
    }

    suspend fun requireLeagueOwner(userId: Int, leagueId: Int) {
        if (!isLeagueOwner(userId, leagueId)) {
            throw ForbiddenException("You do not have permission to manage this league")
        }
    }

    suspend fun isLeagueOwner(userId: Int, leagueId: Int): Boolean = dbQuery {
        val league = LeagueDAO.findById(leagueId)
            ?: throw EntityNotFoundException(DaoEntityID(leagueId, LeaguesTable), LeagueDAO)
        league.owner.id.value == userId
    }

    suspend fun isLeagueMember(userId: Int, leagueId: Int): Boolean = dbQuery {
        LeagueDAO.findById(leagueId)
            ?: throw EntityNotFoundException(DaoEntityID(leagueId, LeaguesTable), LeagueDAO)
        LeagueMembersTable
            .innerJoin(PlayersTable)
            .selectAll()
            .where {
                (LeagueMembersTable.leagueId eq leagueId) and
                    (PlayersTable.userId eq userId)
            }
            .count() > 0
    }

    suspend fun requireLeagueMemberOrOwner(userId: Int, leagueId: Int) {
        val allowed = isLeagueOwner(userId, leagueId) || isLeagueMember(userId, leagueId)
        if (!allowed) throw NotFoundException("League $leagueId not found")
    }

    suspend fun requireLeagueResultRecorder(userId: Int, leagueId: Int, player1Id: Int, player2Id: Int) {
        if (isLeagueOwner(userId, leagueId)) return
        val ownPlayerId = dbQuery {
            PlayersTable
                .selectAll()
                .where { PlayersTable.userId eq userId }
                .map { it[PlayersTable.id].value }
                .firstOrNull()
        }
        if (ownPlayerId != player1Id && ownPlayerId != player2Id) {
            throw ForbiddenException("Only a match participant or league owner can record this result")
        }
        requireLeagueMemberOrOwner(userId, leagueId)
    }
}
