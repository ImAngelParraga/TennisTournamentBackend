package bros.parraga

import bros.parraga.services.rating.EloCalculator
import bros.parraga.services.rating.EloCalculator.RatingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EloCalculatorTest {

    // ----- expectedScore -----

    @Test
    fun `expectedScore of equal ratings is one half`() {
        assertEquals(0.5, EloCalculator.expectedScore(1000, 1000), 1e-9)
        assertEquals(0.5, EloCalculator.expectedScore(1500, 1500), 1e-9)
    }

    @Test
    fun `expectedScore is symmetric and sums to one`() {
        val own = EloCalculator.expectedScore(1200, 1000)
        val opp = EloCalculator.expectedScore(1000, 1200)
        assertEquals(1.0, own + opp, 1e-9)
        assertTrue(own > 0.5, "higher-rated player should be favoured")
        assertTrue(opp < 0.5)
    }

    // ----- kFactor -----

    @Test
    fun `kFactor is 40 while provisional`() {
        assertEquals(40, EloCalculator.kFactor(RatingState(rating = 1000, ratedMatches = 9)))
        assertEquals(40, EloCalculator.kFactor(RatingState(rating = 1000, ratedMatches = 0)))
    }

    @Test
    fun `kFactor drops to 24 after the provisional window`() {
        assertEquals(24, EloCalculator.kFactor(RatingState(rating = 1000, ratedMatches = 10)))
        assertEquals(24, EloCalculator.kFactor(RatingState(rating = 1399, ratedMatches = 20)))
    }

    @Test
    fun `kFactor is 16 for established high-rated players`() {
        assertEquals(16, EloCalculator.kFactor(RatingState(rating = 1400, ratedMatches = 10)))
        assertEquals(16, EloCalculator.kFactor(RatingState(rating = 1800, ratedMatches = 50)))
    }

    @Test
    fun `provisional window overrides high-rating dampening`() {
        assertEquals(40, EloCalculator.kFactor(RatingState(rating = 1500, ratedMatches = 5)))
    }

    // ----- matchDeltas -----

    @Test
    fun `huge favourite still gains the minimum winner amount`() {
        val winner = RatingState(rating = 2000, ratedMatches = 50) // K = 16
        val loser = RatingState(rating = 800, ratedMatches = 50)
        val deltas = EloCalculator.matchDeltas(winner, loser)
        assertEquals(EloCalculator.MIN_WINNER_GAIN, deltas.winnerDelta)
        assertTrue(deltas.loserDelta <= 0, "loser never gains")
    }

    @Test
    fun `underdog win gains a lot`() {
        val winner = RatingState(rating = 800, ratedMatches = 0) // provisional K = 40
        val loser = RatingState(rating = 2000, ratedMatches = 50)
        val deltas = EloCalculator.matchDeltas(winner, loser)
        // Expected score of the underdog is ~0, so gain ~= full K.
        assertEquals(40, deltas.winnerDelta)
    }

    @Test
    fun `evenly matched winner gains about half K and loser loses about half K`() {
        val a = RatingState(rating = 1000, ratedMatches = 20) // K = 24
        val b = RatingState(rating = 1000, ratedMatches = 20)
        val deltas = EloCalculator.matchDeltas(a, b)
        assertEquals(12, deltas.winnerDelta) // round(24 * 0.5)
        assertEquals(-12, deltas.loserDelta)
    }

    // ----- applyFloor -----

    @Test
    fun `applyFloor clamps at 800`() {
        assertEquals(800, EloCalculator.applyFloor(700))
        assertEquals(800, EloCalculator.applyFloor(800))
        assertEquals(900, EloCalculator.applyFloor(900))
    }

    // ----- guestWinDelta -----

    @Test
    fun `guestWinDelta is flat until the tournament cap then reduced`() {
        assertEquals(10, EloCalculator.guestWinDelta(0))
        assertEquals(10, EloCalculator.guestWinDelta(2)) // 2 prior wins -> still full
        assertEquals(2, EloCalculator.guestWinDelta(3))  // 3 prior wins -> capped
        assertEquals(2, EloCalculator.guestWinDelta(10))
    }

    // ----- tournamentBonus -----

    @Test
    fun `tournamentBonus N8 single phase average 1000`() {
        assertEquals(11 to 6, EloCalculator.tournamentBonus(8, 1, 1000.0))
    }

    @Test
    fun `tournamentBonus N16 group plus KO average 1100`() {
        assertEquals(23 to 12, EloCalculator.tournamentBonus(16, 2, 1100.0))
    }

    @Test
    fun `tournamentBonus N32 two phases average 1250`() {
        assertEquals(35 to 18, EloCalculator.tournamentBonus(32, 2, 1250.0))
    }

    @Test
    fun `tournamentBonus clamps champion to the minimum of 10`() {
        // Tiny field, low strength multiplier -> raw well below 10.
        val (champion, finalist) = EloCalculator.tournamentBonus(2, 1, 500.0)
        assertEquals(10, champion)
        assertEquals(5, finalist)
    }

    @Test
    fun `tournamentBonus caps champion at 60`() {
        // Large field, many phases, high strength -> raw well above 60.
        val (champion, finalist) = EloCalculator.tournamentBonus(256, 5, 5000.0)
        assertEquals(60, champion)
        assertEquals(30, finalist)
    }

    @Test
    fun `tournamentBonus strength multiplier clamps at floor 0_8`() {
        // avg 500 -> M clamps to 0.8, same as avg 800.
        assertEquals(
            EloCalculator.tournamentBonus(16, 2, 800.0),
            EloCalculator.tournamentBonus(16, 2, 500.0)
        )
    }

    @Test
    fun `tournamentBonus strength multiplier clamps at ceiling 1_5`() {
        // avg 2000 -> M clamps to 1.5, same as avg 1500.
        assertEquals(
            EloCalculator.tournamentBonus(16, 2, 1500.0),
            EloCalculator.tournamentBonus(16, 2, 2000.0)
        )
    }

    // ----- decayStep -----

    @Test
    fun `decayStep is zero before the grace period`() {
        assertEquals(0, EloCalculator.decayStep(daysSinceLastRated = 55, currentRating = 1200, alreadyApplied = 0))
    }

    @Test
    fun `decayStep is zero at exactly the grace boundary (zero weeks overdue)`() {
        assertEquals(0, EloCalculator.decayStep(daysSinceLastRated = 56, currentRating = 1200, alreadyApplied = 0))
        // Still within the first overdue week.
        assertEquals(0, EloCalculator.decayStep(daysSinceLastRated = 62, currentRating = 1200, alreadyApplied = 0))
    }

    @Test
    fun `decayStep applies minus 4 after one full overdue week`() {
        assertEquals(-4, EloCalculator.decayStep(daysSinceLastRated = 63, currentRating = 1200, alreadyApplied = 0))
    }

    @Test
    fun `decayStep is idempotent against what has already been applied this cycle`() {
        // 63 days -> one week overdue -> total owed is 4; already applied 4 -> nothing left.
        assertEquals(0, EloCalculator.decayStep(daysSinceLastRated = 63, currentRating = 1196, alreadyApplied = 4))
    }

    @Test
    fun `decayStep is capped at minus 4 per run even when many weeks overdue`() {
        // 56 + 10*7 = 126 days -> 10 weeks overdue -> owed 40, but one run caps at 4.
        assertEquals(-4, EloCalculator.decayStep(daysSinceLastRated = 126, currentRating = 1200, alreadyApplied = 0))
    }

    @Test
    fun `decayStep never takes rating below the 1000 baseline`() {
        assertEquals(0, EloCalculator.decayStep(daysSinceLastRated = 126, currentRating = 1000, alreadyApplied = 0))
        // Only 2 points of room above baseline -> decay limited to that room.
        assertEquals(-2, EloCalculator.decayStep(daysSinceLastRated = 126, currentRating = 1002, alreadyApplied = 0))
    }
}
