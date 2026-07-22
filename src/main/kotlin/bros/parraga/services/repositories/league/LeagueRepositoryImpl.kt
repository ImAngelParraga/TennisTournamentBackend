package bros.parraga.services.repositories.league

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.LeagueDAO
import bros.parraga.db.schema.LeagueMatchDAO
import bros.parraga.db.schema.LeagueMatchesTable
import bros.parraga.db.schema.LeagueMemberDAO
import bros.parraga.db.schema.LeagueMembersTable
import bros.parraga.db.schema.LeaguesTable
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.PlayersTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.errors.ConflictException
import bros.parraga.services.InviteCodes
import bros.parraga.services.PlayerResolutionService
import bros.parraga.services.rating.LeagueRatingService
import bros.parraga.services.repositories.league.dto.AddLeagueMemberRequest
import bros.parraga.services.repositories.league.dto.CreateLeagueRequest
import bros.parraga.services.repositories.league.dto.JoinLeagueRequest
import bros.parraga.services.repositories.league.dto.RecordLeagueMatchRequest
import bros.parraga.services.repositories.league.dto.UpdateLeagueRequest
import io.ktor.server.plugins.NotFoundException
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import java.time.Instant

class LeagueRepositoryImpl(
    private val playerResolutionService: PlayerResolutionService
) : LeagueRepository {
    override suspend fun createLeague(ownerUserId: Int, request: CreateLeagueRequest) = dbQuery {
        val owner = UserDAO[ownerUserId]
        val ownerPlayer = playerResolutionService.findOrCreateForUser(owner)
        val league = LeagueDAO.new {
            name = normalizeName(request.name)
            description = normalizeDescription(request.description)
            this.owner = owner
            inviteCode = generateUniqueInviteCode()
        }
        addMemberIfMissing(league, ownerPlayer)
        league.toDomain()
    }

    override suspend fun getMyLeagues(userId: Int) = dbQuery {
        val player = PlayerDAO.find { PlayersTable.userId eq userId }.firstOrNull()
        val owned = LeagueDAO.find { LeaguesTable.ownerUserId eq userId }.toList()
        val memberLeagueIds = player?.let {
            LeagueMemberDAO.find { LeagueMembersTable.playerId eq it.id }
                .map { member -> member.league.id.value }
        }.orEmpty()
        (owned + memberLeagueIds.map { LeagueDAO[it] })
            .distinctBy { it.id.value }
            .sortedBy { it.name.lowercase() }
            .map { it.toDomain() }
    }

    override suspend fun getLeague(id: Int) = dbQuery { LeagueDAO[id].toDomain() }

    override suspend fun updateLeague(id: Int, request: UpdateLeagueRequest) = dbQuery {
        val league = LeagueDAO[id]
        request.name?.let { league.name = normalizeName(it) }
        if (request.description != null) league.description = normalizeDescription(request.description)
        league.updatedAt = Instant.now()
        league.toDomain()
    }

    override suspend fun deleteLeague(id: Int) = dbQuery {
        LeagueDAO[id].delete()
    }

    override suspend fun joinLeague(userId: Int, request: JoinLeagueRequest) = dbQuery {
        val inviteCode = normalizeInviteCode(request.inviteCode)
        val league = LeagueDAO.find { LeaguesTable.inviteCode eq inviteCode }.firstOrNull()
            ?: throw NotFoundException("League invite code not found")
        val player = playerResolutionService.findOrCreateForUser(UserDAO[userId])
        addMemberIfMissing(league, player)
        league.toDomain()
    }

    override suspend fun addMember(id: Int, request: AddLeagueMemberRequest) = dbQuery {
        val league = LeagueDAO[id]
        val player = playerResolutionService.findRegisteredByEmail(request.email)
        addMemberIfMissing(league, player)
    }

    override suspend fun removeMember(id: Int, playerId: Int) = dbQuery {
        val member = requireMember(id, playerId)
        val hasMatches = LeagueMatchDAO.find {
            (LeagueMatchesTable.leagueId eq id) and
                ((LeagueMatchesTable.player1Id eq playerId) or (LeagueMatchesTable.player2Id eq playerId))
        }.firstOrNull() != null
        if (hasMatches) {
            throw ConflictException("Cannot remove a league member with recorded matches")
        }
        if (member.league.owner.id.value == member.player.user?.id?.value) {
            throw ConflictException("Cannot remove the league owner")
        }
        member.delete()
    }

    override suspend fun getMembers(id: Int) = dbQuery {
        LeagueDAO[id]
        LeagueMemberDAO.find { LeagueMembersTable.leagueId eq id }
            .sortedWith(compareByDescending<LeagueMemberDAO> { it.rating }.thenBy { it.player.name.lowercase() })
            .map { it.toDomain() }
    }

    override suspend fun getMatches(id: Int) = dbQuery {
        LeagueDAO[id]
        LeagueMatchDAO.find { LeagueMatchesTable.leagueId eq id }
            .sortedWith(compareByDescending<LeagueMatchDAO> { it.playedAt }.thenByDescending { it.id.value })
            .map { it.toDomain() }
    }

    override suspend fun recordMatch(id: Int, createdByUserId: Int, request: RecordLeagueMatchRequest) = dbQuery {
        val league = LeagueDAO[id]
        require(request.player1Id != request.player2Id) { "player1Id and player2Id must be different" }
        require(request.winnerId == request.player1Id || request.winnerId == request.player2Id) {
            "winnerId must be one of player1Id or player2Id"
        }
        val player1 = requireMember(id, request.player1Id).player
        val player2 = requireMember(id, request.player2Id).player
        val winner = if (request.winnerId == player1.id.value) player1 else player2
        val match = LeagueMatchDAO.new {
            this.league = league
            this.player1 = player1
            this.player2 = player2
            this.winner = winner
            score = request.score
            playedAt = request.playedAt?.toJavaInstant() ?: Instant.now()
            createdBy = UserDAO[createdByUserId]
        }
        LeagueRatingService.recalculateLeague(league)
        match.toDomain()
    }

    override suspend fun deleteMatch(id: Int, matchId: Int) = dbQuery {
        val league = LeagueDAO[id]
        val match = LeagueMatchDAO.findById(matchId)
            ?: throw EntityNotFoundException(DaoEntityID(matchId, LeagueMatchesTable), LeagueMatchDAO)
        if (match.league.id.value != id) throw NotFoundException("League match $matchId was not found for league $id")
        match.delete()
        LeagueRatingService.recalculateLeague(league)
    }

    override suspend fun regenerateInviteCode(id: Int) = dbQuery {
        val league = LeagueDAO[id]
        league.inviteCode = generateUniqueInviteCode()
        league.updatedAt = Instant.now()
        league.toDomain()
    }

    private fun normalizeName(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotBlank()) { "League name is required" }
        require(normalized.length <= 255) { "League name must be 255 characters or fewer" }
        return normalized
    }

    private fun normalizeDescription(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }

    private fun normalizeInviteCode(value: String): String {
        val normalized = value.trim().uppercase()
        require(normalized.isNotBlank()) { "Invite code is required" }
        return normalized
    }

    private fun generateUniqueInviteCode(): String {
        repeat(5) {
            val code = InviteCodes.generate()
            if (LeagueDAO.find { LeaguesTable.inviteCode eq code }.empty()) return code
        }
        throw ConflictException("Could not generate a unique invite code")
    }

    private fun addMemberIfMissing(league: LeagueDAO, player: PlayerDAO): bros.parraga.domain.LeagueMember {
        val existing = LeagueMemberDAO.find {
            (LeagueMembersTable.leagueId eq league.id) and (LeagueMembersTable.playerId eq player.id)
        }.firstOrNull()
        if (existing != null) throw ConflictException("Player ${player.id.value} is already a member of league ${league.id.value}")

        return LeagueMemberDAO.new {
            this.league = league
            this.player = player
        }.toDomain()
    }

    private fun requireMember(leagueId: Int, playerId: Int): LeagueMemberDAO =
        LeagueMemberDAO.find {
            (LeagueMembersTable.leagueId eq leagueId) and (LeagueMembersTable.playerId eq playerId)
        }.firstOrNull() ?: throw NotFoundException("Player $playerId is not a member of league $leagueId")
}
