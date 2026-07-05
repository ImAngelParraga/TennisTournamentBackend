package bros.parraga.services.rating

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure, DB-free Elo math for the rating system. Every rule and constant lives
 * here so it can be unit-tested in isolation; callers ([RatingService], decay
 * loop, backfill) are responsible for persistence, locking and the floor clamp
 * against the *resulting* rating.
 */
object EloCalculator {
    const val START_RATING = 1000
    const val RATING_FLOOR = 800
    const val DECAY_BASELINE = 1000
    const val PROVISIONAL_MATCHES = 10
    const val HIGH_RATING_THRESHOLD = 1400
    const val MIN_WINNER_GAIN = 2
    const val GUEST_WIN_DELTA = 10
    const val GUEST_WIN_CAP_PER_TOURNAMENT = 3
    const val GUEST_WIN_CAPPED_DELTA = 2
    const val GUEST_LOSS_DELTA = -5
    const val DECAY_GRACE_DAYS = 56L
    const val DECAY_PER_WEEK = 4
    const val DECAY_MAX_PER_RUN = 4

    data class RatingState(val rating: Int, val ratedMatches: Int)

    data class MatchDeltas(val winnerDelta: Int, val loserDelta: Int)

    /** Expected score of `own` against `opponent`: `1 / (1 + 10^((opp - own) / 400))`. */
    fun expectedScore(own: Int, opponent: Int): Double =
        1.0 / (1.0 + 10.0.pow((opponent - own) / 400.0))

    /**
     * K-factor: provisional players (fewer than [PROVISIONAL_MATCHES] rated
     * matches) converge fast at 40; established high-rated players (>=
     * [HIGH_RATING_THRESHOLD]) are dampened to 16; everyone else is 24. The
     * provisional window takes precedence over the high-rating dampening.
     */
    fun kFactor(state: RatingState): Int = when {
        state.ratedMatches < PROVISIONAL_MATCHES -> 40
        state.rating >= HIGH_RATING_THRESHOLD -> 16
        else -> 24
    }

    /**
     * Registered-vs-registered deltas. The winner always gains at least
     * [MIN_WINNER_GAIN]. The floor (see [applyFloor]) is intentionally NOT
     * applied here — it clamps the resulting absolute rating, which only the
     * caller knows.
     */
    fun matchDeltas(winner: RatingState, loser: RatingState): MatchDeltas {
        val kw = kFactor(winner)
        val kl = kFactor(loser)
        val ew = expectedScore(winner.rating, loser.rating)
        val el = expectedScore(loser.rating, winner.rating)
        val winnerDelta = maxOf(roundHalfUp(kw * (1.0 - ew)), MIN_WINNER_GAIN)
        val loserDelta = -roundHalfUp(kl * el)
        return MatchDeltas(winnerDelta, loserDelta)
    }

    /**
     * Rating gain for a registered player beating a guest: flat
     * [GUEST_WIN_DELTA], dropping to [GUEST_WIN_CAPPED_DELTA] once the player
     * already has [GUEST_WIN_CAP_PER_TOURNAMENT] guest wins in the tournament
     * (anti-farming).
     */
    fun guestWinDelta(priorGuestWinsInTournament: Int): Int =
        if (priorGuestWinsInTournament < GUEST_WIN_CAP_PER_TOURNAMENT) GUEST_WIN_DELTA else GUEST_WIN_CAPPED_DELTA

    /**
     * Tournament completion bonus as `(champion, finalist)`:
     * `champion = clamp(round((4·√N + 5·(P−1)) · M), 10, 60)`,
     * `finalist = round(champion / 2)`,
     * where `N` = rated field size, `P` = phase count and
     * `M = clamp(avgFieldRating / 1000, 0.8, 1.5)`.
     */
    fun tournamentBonus(ratedFieldSize: Int, phaseCount: Int, avgFieldRating: Double): Pair<Int, Int> {
        val m = (avgFieldRating / 1000.0).coerceIn(0.8, 1.5)
        val raw = (4.0 * sqrt(ratedFieldSize.toDouble()) + 5.0 * (phaseCount - 1)) * m
        val champion = roundHalfUp(raw).coerceIn(10, 60)
        val finalist = roundHalfUp(champion / 2.0)
        return champion to finalist
    }

    /**
     * Non-positive delta to apply on a single decay run. Total decay owed is
     * `4 · floor((days − 56) / 7)` once past the grace period; this run applies
     * whatever remains after [alreadyApplied], never more than
     * [DECAY_MAX_PER_RUN], and never taking [currentRating] below
     * [DECAY_BASELINE]. Returns 0 when nothing is owed.
     */
    fun decayStep(daysSinceLastRated: Long, currentRating: Int, alreadyApplied: Int): Int {
        if (daysSinceLastRated < DECAY_GRACE_DAYS) return 0
        val weeksOverdue = ((daysSinceLastRated - DECAY_GRACE_DAYS) / 7L).toInt()
        val target = DECAY_PER_WEEK * weeksOverdue
        val remaining = target - alreadyApplied
        if (remaining <= 0) return 0
        val roomAboveBaseline = currentRating - DECAY_BASELINE
        if (roomAboveBaseline <= 0) return 0
        val step = minOf(remaining, DECAY_MAX_PER_RUN, roomAboveBaseline)
        return -step
    }

    /** Clamps an absolute rating to the hard floor [RATING_FLOOR]. */
    fun applyFloor(rating: Int): Int = maxOf(rating, RATING_FLOOR)

    /** Round half up via [Math.round] (Long), matching typical Elo rounding. */
    private fun roundHalfUp(value: Double): Int = Math.round(value).toInt()
}
