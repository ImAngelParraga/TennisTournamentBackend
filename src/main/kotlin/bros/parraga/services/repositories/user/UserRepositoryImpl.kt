package bros.parraga.services.repositories.user

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.*
import bros.parraga.errors.ConflictException
import bros.parraga.domain.Achievement
import bros.parraga.domain.AchievementRuleType
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.RatingEvent
import bros.parraga.domain.TournamentBasic
import bros.parraga.domain.TournamentVisibility
import bros.parraga.domain.TrainingVisibility
import bros.parraga.domain.User
import bros.parraga.services.repositories.user.dto.*
import io.ktor.server.plugins.NotFoundException
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class UserRepositoryImpl : UserRepository {
    override suspend fun getUsers(): List<User> = dbQuery {
        val winsByUserId = resolveMatchWinsByUserId()
        val ratingsByUserId = resolveRatingsByUserId()
        UserDAO.all().map {
            val rating = ratingsByUserId[it.id.value]
            it.toDomain(
                matchWins = winsByUserId[it.id.value] ?: 0,
                rating = rating?.first ?: 1000,
                ratedMatches = rating?.second ?: 0
            )
        }
    }

    override suspend fun getUser(id: Int): User = dbQuery {
        val user = UserDAO[id]
        val (rating, ratedMatches) = resolveRatingForUser(user.id.value)
        user.toDomain(
            achievements = resolveAchievementsForUser(user.id.value),
            matchWins = resolveMatchWinsForUser(user.id.value),
            rating = rating,
            ratedMatches = ratedMatches
        )
    }

    override suspend fun getUserRatingHistory(userId: Int, limit: Int): List<RatingEvent> = dbQuery {
        UserDAO[userId] // 404 when the user does not exist
        val player = PlayerDAO.find { PlayersTable.userId eq userId }.firstOrNull()
            ?: return@dbQuery emptyList()
        val clampedLimit = limit.coerceIn(1, 200)
        RatingEventDAO.find { RatingEventsTable.playerId eq player.id }
            .orderBy(
                RatingEventsTable.createdAt to SortOrder.DESC,
                RatingEventsTable.id to SortOrder.DESC
            )
            .limit(clampedLimit)
            .map { it.toDomain() }
    }

    // Same as getUser plus the clubs the user owns or administers, so the frontend
    // can gate host UI from a single /users/me call.
    override suspend fun getMe(userId: Int): User = dbQuery {
        val user = UserDAO[userId]
        val owned = ClubDAO.find { ClubsTable.userId eq userId }.map { it.id.value }
        val administered = ClubAdminsTable
            .selectAll()
            .where { ClubAdminsTable.userId eq userId }
            .map { it[ClubAdminsTable.clubId].value }
        val (rating, ratedMatches) = resolveRatingForUser(userId)
        user.toDomain(
            achievements = resolveAchievementsForUser(userId),
            managedClubIds = (owned + administered).distinct().sorted(),
            matchWins = resolveMatchWinsForUser(userId),
            rating = rating,
            ratedMatches = ratedMatches
        )
    }

    override suspend fun getUserByUsername(username: String): User = dbQuery {
        val user = UserDAO.find { UsersTable.username eq username }.firstOrNull()
            ?: throw NotFoundException("User '$username' not found")
        val (rating, ratedMatches) = resolveRatingForUser(user.id.value)
        user.toDomain(
            achievements = resolveAchievementsForUser(user.id.value),
            matchWins = resolveMatchWinsForUser(user.id.value),
            rating = rating,
            ratedMatches = ratedMatches
        )
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

    override suspend fun getUserTournaments(userId: Int): List<TournamentBasic> = dbQuery {
        UserDAO[userId] // 404 if the user does not exist
        val player = PlayerDAO.find { PlayersTable.userId eq userId }.firstOrNull()
            ?: return@dbQuery emptyList()
        val tournamentIds = TournamentPlayersTable
            .selectAll()
            .where { TournamentPlayersTable.playerId eq player.id }
            .map { it[TournamentPlayersTable.tournamentId].value }
        if (tournamentIds.isEmpty()) return@dbQuery emptyList()
        TournamentDAO.find {
            (TournamentsTable.id inList tournamentIds) and
                (TournamentsTable.visibility eq TournamentVisibility.PUBLIC.name)
        }
            .sortedBy { it.startDate }
            .map { it.toBasic() }
    }

    override suspend fun getPublicProfileCalendar(
        userId: Int,
        from: LocalDate,
        to: LocalDate,
        timezone: ZoneId
    ): ProfileCalendarResponse = dbQuery {
        buildProfileCalendar(userId, from, to, timezone, includePrivateTrainings = false)
    }

    override suspend fun getOwnProfileCalendar(
        userId: Int,
        from: LocalDate,
        to: LocalDate,
        timezone: ZoneId
    ): ProfileCalendarResponse = dbQuery {
        buildProfileCalendar(userId, from, to, timezone, includePrivateTrainings = true)
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

    override suspend fun updateOwnProfile(userId: Int, request: UpdateProfileRequest): User = dbQuery {
        // Each field is independent: only the fields present in the request change. The
        // username is auto-generated once at account creation and never re-synced from the
        // name afterward, so editing the name alone leaves the handle (and URL) untouched.
        val user = UserDAO.findByIdAndUpdate(userId) {
            it.apply {
                request.name?.let { value -> name = value }
                request.username?.let { value ->
                    // User-chosen handle: slugify, validate, and enforce uniqueness.
                    username = resolveRequestedUsername(value, excludeUserId = id.value)
                }
                request.imageUrl?.let { value -> imageUrl = value }
                updatedAt = java.time.Instant.now()
            }
        } ?: throw EntityNotFoundException(DaoEntityID(userId, UsersTable), UserDAO)
        user.toDomain(resolveAchievementsForUser(user.id.value))
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
                ?: claimByEmail(authSubject, email, preferredName)
                ?: UserDAO.new {
                    username = generateUniqueUsername(preferredName, email)
                    this.name = preferredName
                    this.email = email
                    authProvider = "clerk"
                    this.authSubject = authSubject
                }.toDomain()
        }

    // First sign-in claim: a pre-provisioned row (seeded persona, or manually created user)
    // carries a known email and no auth subject yet. The email arrives in a Clerk-verified
    // token, so binding the subject to that row is safe. Rows already bound to another
    // subject are never claimed.
    private fun claimByEmail(authSubject: String, email: String?, preferredName: String?): User? {
        if (email == null) return null
        val unclaimed = UserDAO
            .find { (UsersTable.email eq email) and UsersTable.authSubject.isNull() }
            .firstOrNull() ?: return null
        unclaimed.authSubject = authSubject
        unclaimed.authProvider = "clerk"
        if (unclaimed.name == null) unclaimed.name = preferredName
        unclaimed.updatedAt = java.time.Instant.now()
        return unclaimed.toDomain()
    }

    // Slug used as the public username / profile URL: "José Díaz" -> "jose-diaz".
    private fun slugifyUsername(value: String): String {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
    }

    // Validates a user-chosen handle: slugify it (same rules as auto-generated handles),
    // reject empties, and 409 on collision instead of silently suffixing so the caller
    // knows their exact choice was not honored.
    private fun resolveRequestedUsername(requested: String, excludeUserId: Int): String {
        val slug = slugifyUsername(requested).take(220)
        require(slug.isNotBlank()) { "Username must contain at least one letter or number" }
        val taken = UserDAO.find { UsersTable.username eq slug }.any { it.id.value != excludeUserId }
        if (taken) throw ConflictException("Username '$slug' is already taken")
        return slug
    }

    // Unique slug for the username. excludeUserId lets a user keep/regenerate their own
    // handle without colliding with their existing row. Collisions get -2, -3, ... suffixes.
    private fun generateUniqueUsername(preferredName: String?, email: String?, excludeUserId: Int? = null): String {
        val base = preferredName?.let { slugifyUsername(it) }?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.let { slugifyUsername(it) }?.takeIf { it.isNotBlank() }
            ?: "user"

        val root = base.take(220)
        var candidate = root
        var attempt = 1

        while (true) {
            val existing = UserDAO.find { UsersTable.username eq candidate }.firstOrNull()
            if (existing == null || existing.id.value == excludeUserId) break
            attempt += 1
            candidate = "${root.take(210)}-$attempt"
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

    private fun buildProfileCalendar(
        userId: Int,
        from: LocalDate,
        to: LocalDate,
        timezone: ZoneId,
        includePrivateTrainings: Boolean
    ): ProfileCalendarResponse {
        UserDAO[userId]

        val trainings = loadTrainingEntries(userId, from, to, includePrivateTrainings)
        val player = PlayerDAO.find { PlayersTable.userId eq userId }.firstOrNull()
        val matchEvents = player?.let { loadProfileMatchEvents(it, from, to, timezone) } ?: emptyList()
        val trainingEvents = trainings.map {
            ProfileCalendarEvent(
                eventId = "training-${it.id}",
                eventType = "TRAINING",
                date = it.trainingDate,
                sortTime = null,
                training = it
            )
        }

        val events = (matchEvents + trainingEvents).sortedWith(profileCalendarEventComparator)
        val calendarDays = buildCalendarDays(events)

        return ProfileCalendarResponse(
            userId = userId,
            from = from.toString(),
            to = to.toString(),
            calendarDays = calendarDays,
            events = events
        )
    }

    private fun loadTrainingEntries(
        userId: Int,
        from: LocalDate,
        to: LocalDate,
        includePrivateTrainings: Boolean
    ): List<bros.parraga.domain.UserTrainingEntry> {
        val baseCondition =
            (UserTrainingsTable.ownerUserId eq userId) and
                (UserTrainingsTable.trainingDate greaterEq from) and
                (UserTrainingsTable.trainingDate lessEq to)

        return if (includePrivateTrainings) {
            UserTrainingDAO.find { baseCondition }
        } else {
            UserTrainingDAO.find { baseCondition and (UserTrainingsTable.visibility eq TrainingVisibility.PUBLIC.name) }
        }
            .sortedWith(compareByDescending<UserTrainingDAO> { it.trainingDate }.thenByDescending { it.createdAt })
            .map { it.toDomain() }
    }

    private fun loadProfileMatchEvents(
        player: PlayerDAO,
        from: LocalDate,
        to: LocalDate,
        timezone: ZoneId
    ): List<ProfileCalendarEvent> {
        val includedStatuses = MatchStatus.entries.map { it.name }

        return MatchDAO.find {
            (((MatchesTable.player1Id eq player.id) or (MatchesTable.player2Id eq player.id)) and
                    (MatchesTable.status inList includedStatuses))
        }
            .mapNotNull { it.toProfileCalendarEvent(player.id.value, from, to, timezone) }
    }

    private fun MatchDAO.toProfileCalendarEvent(
        profilePlayerId: Int,
        from: LocalDate,
        to: LocalDate,
        timezone: ZoneId
    ): ProfileCalendarEvent? {
        val matchStatus = MatchStatus.valueOf(status)
        val relevantTime = when (matchStatus) {
            MatchStatus.SCHEDULED, MatchStatus.LIVE -> scheduledTime ?: completedAt
            MatchStatus.COMPLETED, MatchStatus.WALKOVER -> completedAt ?: scheduledTime
        } ?: return null

        val eventDate = relevantTime.atZone(timezone).toLocalDate()
        if (eventDate.isBefore(from) || eventDate.isAfter(to)) {
            return null
        }

        val opponent = when (profilePlayerId) {
            player1?.id?.value -> player2
            player2?.id?.value -> player1
            else -> null
        }
        val winnerId = winner?.id?.value
        val result = when {
            winnerId == null -> null
            winnerId == profilePlayerId -> UserMatchResult.WIN
            else -> UserMatchResult.LOSS
        }

        return ProfileCalendarEvent(
            eventId = "match-${id.value}",
            eventType = "MATCH",
            date = eventDate.toString(),
            sortTime = relevantTime.toKotlinInstant(),
            match = UserProfileMatchEntry(
                matchId = id.value,
                status = matchStatus,
                result = result,
                scheduledTime = when (matchStatus) {
                    MatchStatus.SCHEDULED, MatchStatus.LIVE -> scheduledTime?.toKotlinInstant()
                    MatchStatus.COMPLETED, MatchStatus.WALKOVER -> null
                },
                completedAt = when (matchStatus) {
                    MatchStatus.COMPLETED, MatchStatus.WALKOVER -> completedAt?.toKotlinInstant()
                    MatchStatus.SCHEDULED, MatchStatus.LIVE -> null
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
        )
    }

    private fun buildCalendarDays(events: List<ProfileCalendarEvent>): List<ProfileCalendarDay> {
        val countsByDate = linkedMapOf<String, CalendarDayAccumulator>()

        events.forEach { event ->
            val counts = countsByDate.getOrPut(event.date) { CalendarDayAccumulator() }
            when (event.eventType) {
                "MATCH" -> when (event.match?.status) {
                    MatchStatus.SCHEDULED -> counts.scheduledMatchCount += 1
                    MatchStatus.LIVE -> counts.liveMatchCount += 1
                    MatchStatus.COMPLETED -> counts.completedMatchCount += 1
                    MatchStatus.WALKOVER -> counts.walkoverMatchCount += 1
                    null -> Unit
                }

                "TRAINING" -> counts.trainingCount += 1
            }
            counts.totalCount += 1
        }

        return countsByDate.entries.map { (date, counts) ->
            ProfileCalendarDay(
                date = date,
                totalCount = counts.totalCount,
                scheduledMatchCount = counts.scheduledMatchCount,
                liveMatchCount = counts.liveMatchCount,
                completedMatchCount = counts.completedMatchCount,
                walkoverMatchCount = counts.walkoverMatchCount,
                trainingCount = counts.trainingCount
            )
        }
    }

    private fun compareSortTimes(first: Instant?, second: Instant?): Int = when {
        first == null && second == null -> 0
        first == null -> 1
        second == null -> -1
        else -> first.compareTo(second)
    }

    private val profileCalendarEventComparator = Comparator<ProfileCalendarEvent> { first, second ->
        compareValues(first.date, second.date)
            .takeIf { it != 0 }
            ?: compareSortTimes(first.sortTime, second.sortTime)
                .takeIf { it != 0 }
            ?: compareValues(first.eventId, second.eventId)
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

    // Wins for every user with a linked player, in one grouped query (ranking list).
    private fun resolveMatchWinsByUserId(): Map<Int, Int> {
        val winsCount = MatchesTable.id.count()
        return MatchesTable
            .join(PlayersTable, JoinType.INNER, MatchesTable.winner, PlayersTable.id)
            .select(PlayersTable.userId, winsCount)
            .where { PlayersTable.userId.isNotNull() }
            .groupBy(PlayersTable.userId)
            .associate { row -> row[PlayersTable.userId]!!.value to row[winsCount].toInt() }
    }

    private fun resolveMatchWinsForUser(userId: Int): Int {
        val player = PlayerDAO.find { PlayersTable.userId eq userId }.firstOrNull() ?: return 0
        return MatchesTable
            .selectAll()
            .where { MatchesTable.winner eq player.id.value }
            .count()
            .toInt()
    }

    // Rating + rated-match count for every user with a linked player, in one query
    // (ranking list). Users without a linked player fall back to the defaults.
    private fun resolveRatingsByUserId(): Map<Int, Pair<Int, Int>> {
        return PlayersTable
            .select(PlayersTable.userId, PlayersTable.rating, PlayersTable.ratedMatches)
            .where { PlayersTable.userId.isNotNull() }
            .associate { row ->
                row[PlayersTable.userId]!!.value to (row[PlayersTable.rating] to row[PlayersTable.ratedMatches])
            }
    }

    private fun resolveRatingForUser(userId: Int): Pair<Int, Int> {
        val player = PlayerDAO.find { PlayersTable.userId eq userId }.firstOrNull() ?: return 1000 to 0
        return player.rating to player.ratedMatches
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

    private data class CalendarDayAccumulator(
        var totalCount: Int = 0,
        var scheduledMatchCount: Int = 0,
        var liveMatchCount: Int = 0,
        var completedMatchCount: Int = 0,
        var walkoverMatchCount: Int = 0,
        var trainingCount: Int = 0
    )
}
