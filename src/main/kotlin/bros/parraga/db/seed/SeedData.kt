package bros.parraga.db.seed

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.ClubContactRequestDAO
import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.RatingEventDAO
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.RatingEventsTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import bros.parraga.db.schema.UserTrainingDAO
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.SeedingStrategy
import bros.parraga.domain.SetScore
import bros.parraga.domain.SurfaceType
import bros.parraga.domain.TennisScore
import bros.parraga.domain.TournamentStatus
import bros.parraga.domain.TrainingVisibility
import bros.parraga.domain.UserRole
import bros.parraga.services.repositories.match.MatchRepository
import bros.parraga.services.repositories.match.dto.UpdateMatchScoreRequest
import bros.parraga.services.repositories.tournament.TournamentJoinRequestRepository
import bros.parraga.services.repositories.tournament.TournamentRepository
import bros.parraga.services.repositories.tournament.dto.AddPlayersRequest
import bros.parraga.services.repositories.tournament.dto.CreatePhaseRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentJoinRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentRequest
import bros.parraga.services.repositories.tournament.dto.TournamentPlayerRequest
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.koin.core.Koin
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.days

/**
 * Development-only seeder. Populates the local H2 database with a realistic dataset so that
 * manual/API testing has meaningful data without hand-building it every run.
 *
 * Base entities (users/clubs/players) are inserted directly via Exposed DAOs, but all tournament
 * lifecycle state (phases, matches, standings, champions) is produced by driving the real
 * repository/service layer so the generated data matches genuine domain behavior (bracket and
 * progression logic live in `TennisTournamentLib`).
 *
 * Invoked only from [bros.parraga.modules.configureSeeding], which gates it behind `SEED_DATA=true`
 * and the H2 fallback guard.
 */
object SeedData {
    private val log = LoggerFactory.getLogger(SeedData::class.java)

    /** Presence of this user marks the DB as already seeded (idempotency sentinel). */
    private const val SENTINEL_USERNAME = "seed-owner"

    private const val SECONDS_PER_DAY = 24L * 60L * 60L

    // Claimable test personas: seeded with a known email and NO auth subject, so the first
    // real Clerk sign-in with that (verified) email claims the row — see
    // UserRepositoryImpl.claimByEmail. Create matching email+password users once in the
    // Clerk dev dashboard (docs/MANUAL_TESTING.md).
    private val adminEmail = System.getenv("SEED_ADMIN_EMAIL") ?: "javiparmu@gmail.com"
    private val clubManagerEmail = System.getenv("SEED_CLUB_MANAGER_EMAIL") ?: "club+clerk_test@example.com"

    suspend fun seed(koin: Koin) {
        if (alreadySeeded()) {
            log.info("Seed data already present, skipping")
            return
        }

        val context = createBaseEntities()
        val tournaments = koin.get<TournamentRepository>()
        val matches = koin.get<MatchRepository>()
        val joinRequests = koin.get<TournamentJoinRequestRepository>()

        runScenario("DRAFT knockout") { seedDraftKnockout(tournaments, joinRequests, context) }
        runScenario("STARTED knockout") { seedStartedKnockout(tournaments, matches, context) }
        runScenario("COMPLETED knockout") { seedCompletedKnockout(tournaments, matches, context) }
        runScenario("GROUP sample") { seedGroup(tournaments, context) }
        runScenario("SWISS sample") { seedSwiss(tournaments, context) }
        runScenario("Santomera club") { seedSantomeraTournaments(tournaments, matches, context) }
        runScenario("Javiparmu profile showcase") { seedProfileShowcase(tournaments, matches, context) }
        runScenario("club contact requests") { seedClubContactRequests() }

        log.info("Seed data created")
    }

    private suspend fun runScenario(name: String, block: suspend () -> Unit) {
        runCatching { block() }
            .onFailure { log.warn("Failed to seed scenario '$name': ${it.message}", it) }
    }

    private suspend fun alreadySeeded(): Boolean = dbQuery {
        UserDAO.find { UsersTable.username eq SENTINEL_USERNAME }.empty().not()
    }

    /** A few users and a club to own the seeded tournaments, plus applicant users for join requests. */
    private suspend fun createBaseEntities(): SeedContext = dbQuery {
        // Claimable personas (auth_subject = null until the matching Clerk user signs in).
        val platformAdmin = UserDAO.new {
            username = "platform-admin"
            name = "Platform Admin"
            email = adminEmail
            authProvider = "clerk"
            authSubject = null
            role = UserRole.PLATFORM_ADMIN.name
        }
        // The claimable admin persona also competes: this linked player is what powers the
        // profile (matches, tournaments, rating history, achievements). Rating/ratedMatches/
        // lastRatedAt are left at defaults here and filled by the genuine tournament runs in
        // seedProfileShowcase.
        val platformAdminPlayer = PlayerDAO.new {
            name = "Javier Párraga"
            external = false
            user = platformAdmin
        }
        val clubManager = UserDAO.new {
            username = "club-manager"
            name = "Club Manager"
            email = clubManagerEmail
            authProvider = "clerk"
            authSubject = null
        }

        val sentinel = UserDAO.new {
            username = SENTINEL_USERNAME
            name = "Seed Owner"
            email = "seed-owner@example.com"
            authProvider = "clerk"
            authSubject = "seed-owner-subject"
        }
        val admin = UserDAO.new {
            username = "seed-admin"
            name = "Seed Admin"
            email = "seed-admin@example.com"
            authProvider = "clerk"
            authSubject = "seed-admin-subject"
        }

        // The claimable club-manager persona owns the club; the sentinel and seed-admin
        // stay on as club admins so multi-manager flows remain testable.
        val club = ClubDAO.new {
            name = "Seed Tennis Club"
            phoneNumber = "600000000"
            address = "1 Baseline Avenue"
            user = clubManager
        }
        club.admins = SizedCollection(listOf(sentinel, admin))

        // The platform-admin persona (javiparmu@gmail.com once claimed) owns its own club so
        // the operator can exercise the full host lifecycle from their own account.
        val santomeraClub = ClubDAO.new {
            name = "Santomera"
            phoneNumber = "600123456"
            address = "Calle del Tenis, Santomera"
            user = platformAdmin
        }

        val applicantIds = (1..2).map { index ->
            UserDAO.new {
                username = "seed-applicant-$index"
                name = "Seed Applicant $index"
                email = "seed-applicant-$index@example.com"
                authProvider = "clerk"
                authSubject = "seed-applicant-$index-subject"
            }.id.value
        }

        seedRankedUsers()

        SeedContext(
            ownerUserId = clubManager.id.value,
            clubId = club.id.value,
            santomeraClubId = santomeraClub.id.value,
            applicantUserIds = applicantIds,
            platformAdminUserId = platformAdmin.id.value,
            javiPlayerId = platformAdminPlayer.id.value
        )
    }

    /** Registered users with linked players and rating history for ranking/profile manual testing. */
    private fun seedRankedUsers() {
        listOf(
            RankedSeed(
                username = "ranking-alba",
                name = "Alba Navarro",
                email = "ranking-alba@example.com",
                events = listOf(40, 32, -12, 44, 36, 52, 28)
            ),
            RankedSeed(
                username = "ranking-bruno",
                name = "Bruno Soler",
                email = "ranking-bruno@example.com",
                events = listOf(36, 28, -14, 30, 42)
            ),
            RankedSeed(
                username = "ranking-carmen",
                name = "Carmen Rivas",
                email = "ranking-carmen@example.com",
                events = listOf(-12, 34, 28, -10, 42, -18)
            ),
            RankedSeed(
                username = "ranking-diego",
                name = "Diego Martín",
                email = "ranking-diego@example.com",
                events = listOf(40, -18, 26, -14, 30)
            ),
            RankedSeed(
                username = "ranking-elena",
                name = "Elena Campos",
                email = "ranking-elena@example.com",
                events = listOf(16, -10, 22, -8, 14, 55)
            ),
            RankedSeed(
                username = "ranking-ferran",
                name = "Ferran Vidal",
                email = "ranking-ferran@example.com",
                events = listOf(-18, 24, -14, 20, 24)
            ),
            RankedSeed(
                username = "ranking-gala",
                name = "Gala Torres",
                email = "ranking-gala@example.com",
                events = listOf(22, -18, -12, 8)
            ),
            RankedSeed(
                username = "ranking-hugo",
                name = "Hugo León",
                email = "ranking-hugo@example.com",
                events = listOf(-16, 20, -24, 12, 12)
            )
        ).forEach { seed ->
            val finalRating = 1000 + seed.events.sum()
            val user = UserDAO.new {
                username = seed.username
                name = seed.name
                email = seed.email
                authProvider = "clerk"
                authSubject = "${seed.username}-subject"
            }
            val player = PlayerDAO.new {
                name = seed.name
                external = false
                this.user = user
                rating = finalRating
                ratedMatches = seed.events.count { it != 0 }
                lastRatedAt = rankedEventTime(seed.events.lastIndex)
            }

            var ratingAfter = 1000
            seed.events.forEachIndexed { index, delta ->
                ratingAfter += delta
                RatingEventDAO.new {
                    this.player = player
                    match = null
                    tournament = null
                    reason = "MATCH"
                    this.delta = delta
                    this.ratingAfter = ratingAfter
                    createdAt = rankedEventTime(index)
                }
            }
        }
    }

    private suspend fun seedDraftKnockout(
        tournaments: TournamentRepository,
        joinRequests: TournamentJoinRequestRepository,
        context: SeedContext
    ) {
        val tournament = createTournament(tournaments, context, "Spring Open (Draft)", SurfaceType.CLAY)
        addNamedPlayers(tournaments, tournament.id, count = 4, prefix = "Draft Player")
        tournaments.createPhase(tournament.id, knockoutPhase(thirdPlacePlayoff = false))

        // Leave pending so managers can exercise accept/reject flows against the DRAFT tournament.
        context.applicantUserIds.forEachIndexed { index, userId ->
            joinRequests.createJoinRequest(
                tournamentId = tournament.id,
                userId = userId,
                request = CreateTournamentJoinRequest(
                    playerName = "Applicant ${index + 1}",
                    note = "Would love to join"
                )
            )
        }
    }

    private suspend fun seedStartedKnockout(
        tournaments: TournamentRepository,
        matches: MatchRepository,
        context: SeedContext
    ) {
        val tournament = createTournament(tournaments, context, "Summer Slam (In Progress)", SurfaceType.HARD)
        addNamedPlayers(tournaments, tournament.id, count = 8, prefix = "Slam Player")
        tournaments.createPhase(tournament.id, knockoutPhase(thirdPlacePlayoff = true))
        tournaments.startTournament(tournament.id)
        scoreFirstRound(tournaments, matches, tournament.id)
    }

    private suspend fun seedCompletedKnockout(
        tournaments: TournamentRepository,
        matches: MatchRepository,
        context: SeedContext
    ) {
        val tournament = createTournament(tournaments, context, "Winter Cup (Completed)", SurfaceType.GRASS)
        addNamedPlayers(tournaments, tournament.id, count = 4, prefix = "Cup Player")
        tournaments.createPhase(tournament.id, knockoutPhase(thirdPlacePlayoff = false))
        tournaments.startTournament(tournament.id)
        completeAllMatches(tournaments, matches, tournament.id)
    }

    private suspend fun seedGroup(tournaments: TournamentRepository, context: SeedContext) {
        val tournament = createTournament(tournaments, context, "Club Championship (Groups)", SurfaceType.HARD)
        // GroupConfig requires exactly groupCount * teamsPerGroup entrants.
        addNamedPlayers(tournaments, tournament.id, count = 8, prefix = "Group Player")
        tournaments.createPhase(
            tournament.id,
            CreatePhaseRequest(
                phaseOrder = 1,
                format = PhaseFormat.GROUP,
                configuration = PhaseConfiguration.GroupConfig(
                    groupCount = 2,
                    teamsPerGroup = 4,
                    advancingPerGroup = 2
                )
            )
        )
        tournaments.startTournament(tournament.id)
    }

    private suspend fun seedSwiss(tournaments: TournamentRepository, context: SeedContext) {
        val tournament = createTournament(tournaments, context, "Open League (Swiss)", SurfaceType.CLAY)
        addNamedPlayers(tournaments, tournament.id, count = 6, prefix = "Swiss Player")
        tournaments.createPhase(
            tournament.id,
            CreatePhaseRequest(
                phaseOrder = 1,
                format = PhaseFormat.SWISS,
                configuration = PhaseConfiguration.SwissConfig(pointsPerWin = 3)
            )
        )
        tournaments.startTournament(tournament.id)
    }

    /**
     * The platform-admin's own club, with one tournament per lifecycle state so the operator can
     * test the full host surface from their own account: a ready-to-start draft, an in-progress
     * bracket, a finished one, and a cancelled one.
     */
    private suspend fun seedSantomeraTournaments(
        tournaments: TournamentRepository,
        matches: MatchRepository,
        context: SeedContext
    ) {
        val clubId = context.santomeraClubId

        // Ready to start: 12 players and one knockout phase, left in DRAFT so "start" is one click.
        val open = createTournament(tournaments, context, "Santomera Open", SurfaceType.CLAY, clubId)
        addNamedPlayers(tournaments, open.id, count = 12, prefix = "Santomera Open Player")
        tournaments.createPhase(open.id, knockoutPhase(thirdPlacePlayoff = false))

        // In progress: started knockout with the first round already scored.
        val masters = createTournament(tournaments, context, "Santomera Masters", SurfaceType.HARD, clubId)
        addNamedPlayers(tournaments, masters.id, count = 8, prefix = "Santomera Masters Player")
        tournaments.createPhase(masters.id, knockoutPhase(thirdPlacePlayoff = true))
        tournaments.startTournament(masters.id)
        scoreFirstRound(tournaments, matches, masters.id)

        // Completed: knockout driven all the way to a champion.
        val classic = createTournament(tournaments, context, "Santomera Classic", SurfaceType.GRASS, clubId)
        addNamedPlayers(tournaments, classic.id, count = 4, prefix = "Santomera Classic Player")
        tournaments.createPhase(classic.id, knockoutPhase(thirdPlacePlayoff = false))
        tournaments.startTournament(classic.id)
        completeAllMatches(tournaments, matches, classic.id)

        // Cancelled: no service transition reaches this status yet, so set it directly on the row.
        val cup = createTournament(tournaments, context, "Santomera Cup", SurfaceType.HARD, clubId)
        addNamedPlayers(tournaments, cup.id, count = 6, prefix = "Santomera Cup Player")
        setTournamentStatus(cup.id, TournamentStatus.CANCELLED)
    }

    private suspend fun setTournamentStatus(tournamentId: Int, status: TournamentStatus) = dbQuery {
        val tournament = TournamentDAO.findById(tournamentId)
            ?: error("Seeded tournament $tournamentId not found")
        tournament.status = status.name
    }

    /** Pending onboarding inquiries so the /admin review flow has content on first run. */
    private suspend fun seedClubContactRequests() = dbQuery {
        ClubContactRequestDAO.new {
            clubName = "Club de Tenis Ribera"
            contactName = "Ana García"
            email = "ana@clubribera.com"
            phone = "+34 600 111 222"
            message = "Organizamos tres torneos al año en tierra batida y queremos publicarlos."
        }
        ClubContactRequestDAO.new {
            clubName = "CT Monteverde"
            contactName = "Luis Pérez"
            email = "luis@ctmonteverde.com"
            phone = null
            message = "Nos interesa gestionar las inscripciones desde la web."
        }
        ClubContactRequestDAO.new {
            clubName = "Padel & Tenis Sur"
            contactName = "Marta Ruiz"
            email = "marta@ptsur.com"
            phone = "+34 600 333 444"
            message = null
        }
    }

    // ---------------------------------------------------------------------------
    // Javiparmu profile showcase
    // ---------------------------------------------------------------------------
    // Populates the claimable platform-admin persona (javiparmu@gmail.com once claimed) with a
    // rich player profile: real tournament runs, completed matches, auto-generated rating events,
    // titles/achievements and a spread-out activity calendar. Everything is produced by driving
    // the genuine repository/service layer (so ratings/standings match real domain behavior), then
    // the timestamps are back-dated so the rating graph and calendar look lived-in instead of all
    // landing on "today".

    private enum class ShowcaseOutcome { WIN_TITLE, DEEP_RUN, EARLY_EXIT }

    private data class ShowcaseRun(
        val name: String,
        val surface: SurfaceType,
        val playerCount: Int,
        val outcome: ShowcaseOutcome,
        val daysAgo: Long
    )

    private suspend fun seedProfileShowcase(
        tournaments: TournamentRepository,
        matches: MatchRepository,
        context: SeedContext
    ) {
        val javiPlayerId = context.javiPlayerId
        val rivalPlayerIds = createShowcaseRivals()

        // Two titles, one deep run ending in a loss, and one first-round exit: a varied record
        // with rating swings both ways, laid out from ~2.5 months ago up to a couple weeks back.
        val runs = listOf(
            ShowcaseRun("Santomera Winter Open", SurfaceType.HARD, 8, ShowcaseOutcome.WIN_TITLE, 78),
            ShowcaseRun("Murcia Regional Open", SurfaceType.CLAY, 4, ShowcaseOutcome.WIN_TITLE, 54),
            ShowcaseRun("Costa Cálida Masters", SurfaceType.HARD, 8, ShowcaseOutcome.DEEP_RUN, 30),
            ShowcaseRun("Club Night Cup", SurfaceType.CLAY, 4, ShowcaseOutcome.EARLY_EXIT, 12)
        )

        runs.forEach { run ->
            val tournament = createTournament(tournaments, context, run.name, run.surface)
            val participantIds = listOf(javiPlayerId) + rivalPlayerIds.take(run.playerCount - 1)
            tournaments.addPlayersToTournament(
                tournament.id,
                AddPlayersRequest(participantIds.map { TournamentPlayerRequest(playerId = it) })
            )
            tournaments.createPhase(tournament.id, knockoutPhase(thirdPlacePlayoff = false))
            tournaments.startTournament(tournament.id)

            when (run.outcome) {
                ShowcaseOutcome.WIN_TITLE ->
                    completeKnockoutFavoring(tournaments, matches, tournament.id, favoredPlayerId = javiPlayerId)
                ShowcaseOutcome.DEEP_RUN ->
                    completeKnockoutFavoring(tournaments, matches, tournament.id, favoredPlayerId = rivalPlayerIds.first())
                ShowcaseOutcome.EARLY_EXIT ->
                    completeKnockoutLosing(tournaments, matches, tournament.id, losingPlayerId = javiPlayerId)
            }

            backdateShowcaseTournament(tournament.id, daysAgo = run.daysAgo)
        }

        // Match scoring stamps lastRatedAt with "now"; realign it with the back-dated events.
        refreshLastRatedAt(listOf(javiPlayerId) + rivalPlayerIds)
        seedShowcaseTrainings(context.platformAdminUserId)
    }

    /** Registered rivals (external = false) so javiparmu's matches produce normal rating events. */
    private suspend fun createShowcaseRivals(): List<Int> = dbQuery {
        listOf(
            "Pablo Ferrer", "Marc Ortega", "Iván Ruiz", "Sergio Molina",
            "Nacho Beltrán", "Álvaro Gil", "Dani Prieto"
        ).mapIndexed { index, fullName ->
            val handle = "showcase-rival-${index + 1}"
            val user = UserDAO.new {
                username = handle
                name = fullName
                email = "$handle@example.com"
                authProvider = "clerk"
                authSubject = "$handle-subject"
            }
            PlayerDAO.new {
                name = fullName
                external = false
                this.user = user
            }.id.value
        }
    }

    /**
     * Drives a knockout to completion making [favoredPlayerId] win every match they play (so they
     * finish champion); every other match goes to the first-listed player. Same one-match-at-a-time
     * loop as [completeAllMatches].
     */
    private suspend fun completeKnockoutFavoring(
        tournaments: TournamentRepository,
        matches: MatchRepository,
        tournamentId: Int,
        favoredPlayerId: Int,
        maxIterations: Int = 200
    ) {
        repeat(maxIterations) {
            val next = tournaments.getTournamentMatches(tournamentId).firstOrNull { it.isScoreable() } ?: return
            val request = if (next.player2?.id == favoredPlayerId) player2Win() else player1Win()
            matches.updateMatchScore(next.id, request)
        }
        log.warn("completeKnockoutFavoring hit iteration cap for tournament $tournamentId")
    }

    /**
     * Drives a knockout to completion making [losingPlayerId] lose every match they appear in
     * (a clean first-round exit); every other match goes to the first-listed player, so some
     * rival ends up champion.
     */
    private suspend fun completeKnockoutLosing(
        tournaments: TournamentRepository,
        matches: MatchRepository,
        tournamentId: Int,
        losingPlayerId: Int,
        maxIterations: Int = 200
    ) {
        repeat(maxIterations) {
            val next = tournaments.getTournamentMatches(tournamentId).firstOrNull { it.isScoreable() } ?: return
            val request = if (next.player1?.id == losingPlayerId) player2Win() else player1Win()
            matches.updateMatchScore(next.id, request)
        }
        log.warn("completeKnockoutLosing hit iteration cap for tournament $tournamentId")
    }

    /**
     * Rewrites a freshly completed showcase tournament's timestamps so its matches, rating events
     * and dates sit [daysAgo] in the past instead of "now". Winners and rating values are untouched
     * (only the clocks move), so the rating graph and profile calendar look spread out. Matches are
     * placed one day apart per knockout round; completion bonuses land the day after the final.
     */
    private suspend fun backdateShowcaseTournament(tournamentId: Int, daysAgo: Long) = dbQuery {
        val base = Instant.now().minusSeconds(daysAgo * SECONDS_PER_DAY)
        val tournament = TournamentDAO.findById(tournamentId) ?: return@dbQuery

        val completedMatches = tournament.phases
            .flatMap { it.matches }
            .filter { it.status == MatchStatus.COMPLETED.name }
            .sortedWith(compareBy({ it.round }, { it.id.value }))
        val maxRound = completedMatches.maxOfOrNull { it.round } ?: 0

        completedMatches.forEach { match ->
            val at = base.plusSeconds((match.round - 1).toLong() * SECONDS_PER_DAY)
            match.completedAt = at
            match.updatedAt = at
            RatingEventDAO.find { RatingEventsTable.matchId eq match.id }.forEach { it.createdAt = at }
        }

        val bonusTime = base.plusSeconds(maxRound.toLong() * SECONDS_PER_DAY)
        RatingEventDAO.find { RatingEventsTable.tournamentId eq tournament.id }
            .filter { it.match == null }
            .forEach { it.createdAt = bonusTime }

        tournament.startDate = base.minusSeconds(SECONDS_PER_DAY)
        tournament.endDate = bonusTime
        tournament.updatedAt = bonusTime
    }

    /** Re-points each player's lastRatedAt at their newest (back-dated) rating event. */
    private suspend fun refreshLastRatedAt(playerIds: List<Int>) = dbQuery {
        playerIds.distinct().forEach { playerId ->
            val latest = RatingEventDAO.find { RatingEventsTable.playerId eq playerId }
                .maxByOrNull { it.createdAt }
            PlayerDAO.findById(playerId)?.lastRatedAt = latest?.createdAt
        }
    }

    /** A mix of public/private trainings so the profile calendar has non-match activity too. */
    private suspend fun seedShowcaseTrainings(ownerUserId: Int) = dbQuery {
        val owner = UserDAO.findById(ownerUserId) ?: return@dbQuery
        val today = LocalDate.now()
        listOf(
            Triple(2L, "Serve & volley drills", TrainingVisibility.PUBLIC) to 75,
            Triple(5L, "Baseline consistency, cross-court", TrainingVisibility.PUBLIC) to 60,
            Triple(9L, "Physical + footwork", TrainingVisibility.PRIVATE) to 90,
            Triple(14L, "Match sparring", TrainingVisibility.PUBLIC) to 60,
            Triple(21L, "Return of serve", TrainingVisibility.PRIVATE) to 45,
            Triple(28L, "Clay sliding & defense", TrainingVisibility.PUBLIC) to 80,
            Triple(40L, "Tactics review", TrainingVisibility.PRIVATE) to 60
        ).forEach { (spec, minutes) ->
            val (daysAgo, note, vis) = spec
            UserTrainingDAO.new {
                ownerUser = owner
                trainingDate = today.minusDays(daysAgo)
                durationMinutes = minutes
                notes = note
                visibility = vis.name
            }
        }
    }

    private suspend fun createTournament(
        tournaments: TournamentRepository,
        context: SeedContext,
        name: String,
        surface: SurfaceType,
        clubId: Int = context.clubId
    ) = tournaments.createTournament(
        context.ownerUserId,
        CreateTournamentRequest(
            name = name,
            description = "Seed data for local testing",
            surface = surface.name,
            clubId = clubId,
            startDate = Clock.System.now(),
            endDate = Clock.System.now() + 7.days
        )
    )

    private suspend fun addNamedPlayers(
        tournaments: TournamentRepository,
        tournamentId: Int,
        count: Int,
        prefix: String
    ) {
        tournaments.addPlayersToTournament(
            tournamentId,
            AddPlayersRequest((1..count).map { TournamentPlayerRequest(name = "$prefix $it") })
        )
    }

    private fun knockoutPhase(thirdPlacePlayoff: Boolean) = CreatePhaseRequest(
        phaseOrder = 1,
        format = PhaseFormat.KNOCKOUT,
        configuration = PhaseConfiguration.KnockoutConfig(
            thirdPlacePlayoff = thirdPlacePlayoff,
            qualifiers = 1,
            seedingStrategy = SeedingStrategy.INPUT_ORDER
        )
    )

    /** Scores every match in the earliest still-playable round so the bracket has advanced once. */
    private suspend fun scoreFirstRound(
        tournaments: TournamentRepository,
        matches: MatchRepository,
        tournamentId: Int
    ) {
        val playable = tournaments.getTournamentMatches(tournamentId).filter { it.isScoreable() }
        val firstRound = playable.minOfOrNull { it.round } ?: return
        playable.filter { it.round == firstRound }.forEach { matches.updateMatchScore(it.id, player1Win()) }
    }

    /** Drives the tournament to completion, one playable match at a time, until a champion is set. */
    private suspend fun completeAllMatches(
        tournaments: TournamentRepository,
        matches: MatchRepository,
        tournamentId: Int,
        maxIterations: Int = 200
    ) {
        repeat(maxIterations) {
            val next = tournaments.getTournamentMatches(tournamentId).firstOrNull { it.isScoreable() } ?: return
            matches.updateMatchScore(next.id, player1Win())
        }
        log.warn("completeAllMatches hit iteration cap for tournament $tournamentId")
    }

    private fun bros.parraga.domain.Match.isScoreable(): Boolean =
        (status == MatchStatus.SCHEDULED || status == MatchStatus.LIVE) && player1 != null && player2 != null

    /** A straight-sets win for player1 (the first-listed participant). */
    private fun player1Win() = UpdateMatchScoreRequest(
        score = TennisScore(
            sets = listOf(
                SetScore(6, 4, null),
                SetScore(6, 4, null)
            )
        )
    )

    /** A straight-sets win for player2 (the second-listed participant). */
    private fun player2Win() = UpdateMatchScoreRequest(
        score = TennisScore(
            sets = listOf(
                SetScore(4, 6, null),
                SetScore(4, 6, null)
            )
        )
    )

    private fun rankedEventTime(index: Int): Instant =
        Instant.now().minusSeconds((45L - index * 6L) * 24L * 60L * 60L)

    private data class RankedSeed(
        val username: String,
        val name: String,
        val email: String,
        val events: List<Int>
    )

    private data class SeedContext(
        val ownerUserId: Int,
        val clubId: Int,
        val santomeraClubId: Int,
        val applicantUserIds: List<Int>,
        val platformAdminUserId: Int,
        val javiPlayerId: Int
    )
}
