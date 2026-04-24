package bros.parraga.services.repositories.user

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.*
import bros.parraga.domain.Achievement
import bros.parraga.domain.AchievementRuleType
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.User
import bros.parraga.services.repositories.user.dto.*
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.util.*

class UserRepositoryImpl : UserRepository {
    override suspend fun getUsers(): List<User> = dbQuery {
        UserDAO.all().map { it.toDomain() }
    }

    override suspend fun getUser(id: Int): User = dbQuery {
        val user = UserDAO[id]
        user.toDomain(resolveAchievementsForUser(user.id.value))
    }

    override suspend fun getUserMatchActivity(userId: Int, from: Instant, to: Instant): UserMatchActivityResponse =
        dbQuery {
            UserDAO[userId]
            val player = PlayerDAO.find { PlayersTable.userId eq userId }.firstOrNull()
                ?: return@dbQuery UserMatchActivityResponse(
                    userId = userId,
                    from = from,
                    to = to,
                    matches = emptyList()
                )

            val completedStatuses = listOf(MatchStatus.COMPLETED.name, MatchStatus.WALKOVER.name)
            val playerId = player.id.value
            val matches = MatchDAO.find {
                (((MatchesTable.player1Id eq player.id) or (MatchesTable.player2Id eq player.id)) and
                        (MatchesTable.status inList completedStatuses) and
                        MatchesTable.completedAt.isNotNull() and
                        (MatchesTable.completedAt greaterEq from.toJavaInstant()) and
                        (MatchesTable.completedAt lessEq to.toJavaInstant()))
            }
                .sortedByDescending { it.completedAt }
                .map { it.toUserMatchActivityItem(playerId) }

            UserMatchActivityResponse(
                userId = userId,
                playerId = playerId,
                playerName = player.name,
                from = from,
                to = to,
                matches = matches
            )
        }

    override suspend fun createUser(request: CreateUserRequest): User = dbQuery {
        UserDAO.new {
            username = request.username
            email = request.email
            authProvider = "local"
            authSubject = null
        }.toDomain()
    }

    override suspend fun updateUser(request: UpdateUserRequest): User = dbQuery {
        UserDAO.findByIdAndUpdate(request.id) {
            it.apply {
                request.username?.let { username = it }
                request.email?.let { email = it }
                updatedAt = java.time.Instant.now()
            }
        }?.toDomain() ?: throw EntityNotFoundException(
            DaoEntityID(request.id, UsersTable),
            UserDAO
        )
    }

    override suspend fun deleteUser(id: Int) = dbQuery {
        UserDAO[id].delete()
    }

    override suspend fun findByAuthSubject(authSubject: String): User? = dbQuery {
        UserDAO.find { UsersTable.authSubject eq authSubject }
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun findOrCreateByAuthSubject(authSubject: String, email: String?, preferredName: String?): User =
        dbQuery {
            UserDAO.find { UsersTable.authSubject eq authSubject }
                .firstOrNull()
                ?.toDomain()
                ?: UserDAO.new {
                    username = generateUniqueUsername(preferredName, email)
                    this.email = email
                    authProvider = "clerk"
                    this.authSubject = authSubject
                }.toDomain()
        }

    private fun generateUniqueUsername(preferredName: String?, email: String?): String {
        val base = preferredName
            ?.trim()
            ?.replace("\\s+".toRegex(), "_")
            ?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "user"

        var candidate = base.take(220)
        var attempt = 0

        while (UserDAO.find { UsersTable.username eq candidate }.firstOrNull() != null) {
            attempt += 1
            candidate = "${base.take(200)}_${attempt}_${UUID.randomUUID().toString().take(8)}"
        }

        return candidate
    }

    private fun MatchDAO.toUserMatchActivityItem(profilePlayerId: Int): UserMatchActivityItem {
        val opponent = when (profilePlayerId) {
            player1?.id?.value -> player2
            player2?.id?.value -> player1
            else -> null
        }

        return UserMatchActivityItem(
            matchId = id.value,
            completedAt = requireNotNull(completedAt) { "Completed match ${id.value} is missing completedAt" }.toKotlinInstant(),
            status = MatchStatus.valueOf(status),
            result = if (requireNotNull(winner) { "Completed match ${id.value} is missing winner" }.id.value == profilePlayerId) {
                UserMatchResult.WIN
            } else {
                UserMatchResult.LOSS
            },
            score = score,
            court = court,
            tournament = UserMatchTournamentSummary(
                id = phase.tournament.id.value,
                name = phase.tournament.name
            ),
            phase = UserMatchPhaseSummary(
                id = phase.id.value,
                phaseOrder = phase.phaseOrder,
                format = bros.parraga.domain.PhaseFormat.valueOf(phase.format),
                round = round
            ),
            opponent = opponent?.let {
                UserMatchOpponentSummary(
                    id = it.id.value,
                    name = it.name,
                    userId = it.user?.id?.value
                )
            }
        )
    }

    private fun resolveAchievementsForUser(userId: Int): List<Achievement> {
        val player = PlayerDAO.find { PlayersTable.userId eq userId }.firstOrNull() ?: return emptyList()
        val stats = resolveAchievementStats(player.id.value)

        return AchievementDAO.find { AchievementsTable.active eq true }
            .sortedBy { it.id.value }
            .mapNotNull { definition ->
                val threshold = definition.threshold
                if (threshold <= 0) return@mapNotNull null

                val unlocked = when (AchievementRuleType.valueOf(definition.ruleType)) {
                    AchievementRuleType.TOURNAMENT_WINS_AT_LEAST -> stats.tournamentWins >= threshold
                    AchievementRuleType.MATCH_WINS_AT_LEAST -> stats.matchWins >= threshold
                    AchievementRuleType.MATCHES_PLAYED_AT_LEAST -> stats.matchesPlayed >= threshold
                }

                definition.toDomain().takeIf { unlocked }
            }
    }

    private fun resolveAchievementStats(playerId: Int): AchievementStats {
        val tournamentWins = TournamentsTable
            .selectAll()
            .where { TournamentsTable.championPlayerId eq playerId }
            .count()
            .toInt()

        val matchWins = MatchesTable
            .selectAll()
            .where { MatchesTable.winner eq playerId }
            .count()
            .toInt()

        val completedStatuses = listOf(MatchStatus.COMPLETED.name, MatchStatus.WALKOVER.name)
        val matchesPlayed = MatchesTable
            .selectAll()
            .where {
                ((MatchesTable.player1Id eq playerId) or (MatchesTable.player2Id eq playerId)) and
                        (MatchesTable.status inList completedStatuses)
            }
            .count()
            .toInt()

        return AchievementStats(
            tournamentWins = tournamentWins,
            matchWins = matchWins,
            matchesPlayed = matchesPlayed
        )
    }

    private data class AchievementStats(
        val tournamentWins: Int,
        val matchWins: Int,
        val matchesPlayed: Int
    )
}
