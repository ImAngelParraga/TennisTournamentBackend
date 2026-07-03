package bros.parraga.db.seed

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.ClubContactRequestDAO
import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.UserDAO
import bros.parraga.db.schema.UsersTable
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.UserRole
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.SeedingStrategy
import bros.parraga.domain.SetScore
import bros.parraga.domain.TennisScore
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
import org.koin.core.Koin
import org.slf4j.LoggerFactory
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

    // Claimable test personas: seeded with a known email and NO auth subject, so the first
    // real Clerk sign-in with that (verified) email claims the row — see
    // UserRepositoryImpl.claimByEmail. Create matching email+password users once in the
    // Clerk dev dashboard (docs/MANUAL_TESTING.md).
    private val adminEmail = System.getenv("SEED_ADMIN_EMAIL") ?: "admin+clerk_test@example.com"
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
        UserDAO.new {
            username = "platform-admin"
            name = "Platform Admin"
            email = adminEmail
            authProvider = "clerk"
            authSubject = null
            role = UserRole.PLATFORM_ADMIN.name
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

        val applicantIds = (1..2).map { index ->
            UserDAO.new {
                username = "seed-applicant-$index"
                name = "Seed Applicant $index"
                email = "seed-applicant-$index@example.com"
                authProvider = "clerk"
                authSubject = "seed-applicant-$index-subject"
            }.id.value
        }

        SeedContext(clubId = club.id.value, applicantUserIds = applicantIds)
    }

    private suspend fun seedDraftKnockout(
        tournaments: TournamentRepository,
        joinRequests: TournamentJoinRequestRepository,
        context: SeedContext
    ) {
        val tournament = createTournament(tournaments, context, "Spring Open (Draft)")
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
        val tournament = createTournament(tournaments, context, "Summer Slam (In Progress)")
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
        val tournament = createTournament(tournaments, context, "Winter Cup (Completed)")
        addNamedPlayers(tournaments, tournament.id, count = 4, prefix = "Cup Player")
        tournaments.createPhase(tournament.id, knockoutPhase(thirdPlacePlayoff = false))
        tournaments.startTournament(tournament.id)
        completeAllMatches(tournaments, matches, tournament.id)
    }

    private suspend fun seedGroup(tournaments: TournamentRepository, context: SeedContext) {
        val tournament = createTournament(tournaments, context, "Club Championship (Groups)")
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
        val tournament = createTournament(tournaments, context, "Open League (Swiss)")
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

    private suspend fun createTournament(
        tournaments: TournamentRepository,
        context: SeedContext,
        name: String
    ) = tournaments.createTournament(
        CreateTournamentRequest(
            name = name,
            description = "Seed data for local testing",
            surface = null,
            clubId = context.clubId,
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

    private data class SeedContext(
        val clubId: Int,
        val applicantUserIds: List<Int>
    )
}
