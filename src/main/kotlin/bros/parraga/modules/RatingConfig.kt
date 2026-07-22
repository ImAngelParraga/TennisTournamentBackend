package bros.parraga.modules

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.lockPlayerRowInCurrentTransaction
import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.MatchesTable
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.PlayersTable
import bros.parraga.db.schema.RatingEventDAO
import bros.parraga.db.schema.RatingEventsTable
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentsTable
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.TournamentStatus
import bros.parraga.domain.TournamentVisibility
import bros.parraga.services.rating.EloCalculator
import bros.parraga.services.rating.RatingService
import io.ktor.server.application.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.hours

/**
 * Wires the rating engine's out-of-band work into the running application. MUST be
 * called from [bros.parraga.module] only (never `testModule`): the backfill and the
 * decay loop are real side effects that integration tests must not trigger.
 */
fun Application.configureRating() {
    // One-off history reconstruction, synchronous so it finishes before the server
    // accepts traffic. Idempotent (skipped once any rating event exists), and its
    // failure must never block startup.
    runCatching {
        runBlocking { dbQuery { backfillRatingsIfEmpty() } }
    }.onFailure { log.error("Rating backfill failed: ${it.message}", it) }

    // Inactivity decay: one pass per day for as long as the app runs.
    launch {
        while (isActive) {
            runCatching { runDecayPass() }
                .onFailure { log.error("Rating decay pass failed: ${it.message}", it) }
            delay(24.hours)
        }
    }
}

/**
 * Replays earned rating history when the log is empty but completed play exists —
 * e.g. an existing deployment adopting the engine, or a fresh H2 dev seed. All
 * completed matches are replayed in completion order, then completed tournaments'
 * bonuses. Known approximation: bonuses land after ALL matches instead of being
 * interleaved at each tournament's real completion, so the average-field-rating
 * input can differ slightly from a live run.
 */
private fun backfillRatingsIfEmpty() {
    if (!RatingEventDAO.all().empty()) return

    val completedMatches = MatchDAO.find { MatchesTable.status eq MatchStatus.COMPLETED.name }
    if (completedMatches.empty()) return

    completedMatches
        .sortedWith(compareBy<MatchDAO>({ it.completedAt == null }, { it.completedAt }, { it.id.value }))
        .filter { it.phase.tournament.visibility == TournamentVisibility.PUBLIC.name }
        .forEach { RatingService.applyMatchRating(it) }

    TournamentDAO.find {
        (TournamentsTable.status eq TournamentStatus.COMPLETED.name) and
            TournamentsTable.championPlayerId.isNotNull() and
            (TournamentsTable.visibility eq TournamentVisibility.PUBLIC.name)
    }
        .sortedWith(compareBy<TournamentDAO>({ it.updatedAt }, { it.id.value }))
        .forEach { tournament ->
            val finalPhase = tournament.phases.maxByOrNull { it.phaseOrder } ?: return@forEach
            RatingService.applyTournamentCompletionBonus(tournament, finalPhase)
        }
}

/**
 * Decays inactive registered players toward the baseline. Candidates are selected
 * cheaply, then each is settled in its OWN transaction that locks the row and
 * recomputes what it still owes ([EloCalculator.decayStep] against DECAY events
 * since the last rated match), so overlapping runs converge without over-decaying.
 */
private suspend fun runDecayPass() {
    val staleBefore = Instant.now().minus(EloCalculator.DECAY_GRACE_DAYS, ChronoUnit.DAYS)

    val candidateIds = dbQuery {
        PlayersTable
            .select(PlayersTable.id)
            .where {
                (PlayersTable.external eq false) and
                    (PlayersTable.rating greater EloCalculator.DECAY_BASELINE) and
                    PlayersTable.lastRatedAt.isNotNull() and
                    (PlayersTable.lastRatedAt less staleBefore)
            }
            .map { it[PlayersTable.id].value }
    }

    candidateIds.forEach { playerId ->
        dbQuery { applyDecayForPlayer(playerId) }
    }
}

private fun applyDecayForPlayer(playerId: Int) {
    lockPlayerRowInCurrentTransaction(playerId)
    val player = PlayerDAO.findById(playerId) ?: return
    if (player.external) return
    val lastRatedAt = player.lastRatedAt ?: return

    val now = Instant.now()
    val daysSince = Duration.between(lastRatedAt, now).toDays()

    val deltaSum = RatingEventsTable.delta.sum()
    val decayAppliedSinceLastRated = RatingEventsTable
        .select(deltaSum)
        .where {
            (RatingEventsTable.playerId eq playerId) and
                (RatingEventsTable.reason eq RatingService.REASON_DECAY) and
                (RatingEventsTable.createdAt greater lastRatedAt)
        }
        .firstOrNull()
        ?.get(deltaSum) ?: 0
    // decay deltas are non-positive; alreadyApplied is the positive amount drained.
    val alreadyApplied = -decayAppliedSinceLastRated

    val step = EloCalculator.decayStep(daysSince, player.rating, alreadyApplied)
    if (step >= 0) return

    val newRating = player.rating + step
    player.rating = newRating
    RatingEventDAO.new {
        this.player = player
        match = null
        tournament = null
        reason = RatingService.REASON_DECAY
        delta = step
        ratingAfter = newRating
        createdAt = now
    }
}
