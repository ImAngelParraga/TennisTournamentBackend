package bros.parraga.services.rating

import bros.parraga.db.lockPlayerRowInCurrentTransaction
import bros.parraga.db.schema.GroupStandingDAO
import bros.parraga.db.schema.GroupStandingsTable
import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.RatingEventDAO
import bros.parraga.db.schema.RatingEventsTable
import bros.parraga.db.schema.SwissRankingsTable
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentPhaseDAO
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.Outcome
import bros.parraga.domain.PhaseFormat
import bros.parraga.services.rating.EloCalculator.RatingState
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant

/**
 * Applies the [EloCalculator] math to persistent state. Every function ASSUMES
 * it runs inside an already-open Exposed transaction that the caller owns (the
 * match-scoring / progression flow, backfill, or the decay loop). Persistence is
 * done by mutating [PlayerDAO] fields and appending [RatingEventDAO] rows.
 *
 * Guests (external players) keep a frozen rating of [EloCalculator.START_RATING],
 * never receive rating events and never decay. If an external player ever claims
 * an account (no such path exists today), their rating simply starts at the
 * baseline — earned history is not retroactively reconstructed.
 */
object RatingService {
    const val REASON_MATCH = "MATCH"
    const val REASON_GUEST_WIN = "GUEST_WIN"
    const val REASON_GUEST_LOSS = "GUEST_LOSS"
    const val REASON_TOURNAMENT_BONUS = "TOURNAMENT_BONUS"
    const val REASON_DECAY = "DECAY"

    /**
     * Records the rating change(s) for a freshly completed match. No-op unless the
     * match is COMPLETED with a resolved winner and both players set (walkovers
     * arrive as WALKOVER and are skipped — they are not a played match).
     */
    fun applyMatchRating(match: MatchDAO) {
        if (match.status != MatchStatus.COMPLETED.name) return
        val winner = match.winner ?: return
        val player1 = match.player1 ?: return
        val player2 = match.player2 ?: return

        val loser = when (winner.id) {
            player1.id -> player2
            player2.id -> player1
            else -> return
        }

        // Both guests: neither has a rating to move.
        if (winner.external && loser.external) return

        val tournament = match.phase.tournament
        val timing = match.completedAt ?: Instant.now()

        if (winner.external || loser.external) {
            val registered = if (winner.external) loser else winner
            val registeredWon = registered.id == winner.id
            if (registeredWon) {
                val priorGuestWins = RatingEventsTable
                    .selectAll()
                    .where {
                        (RatingEventsTable.playerId eq registered.id) and
                            (RatingEventsTable.tournamentId eq tournament.id) and
                            (RatingEventsTable.reason eq REASON_GUEST_WIN)
                    }
                    .count()
                    .toInt()
                applyGuestResult(
                    registered = registered,
                    match = match,
                    tournament = tournament,
                    reason = REASON_GUEST_WIN,
                    rawDelta = EloCalculator.guestWinDelta(priorGuestWins),
                    timing = timing
                )
            } else {
                applyGuestResult(
                    registered = registered,
                    match = match,
                    tournament = tournament,
                    reason = REASON_GUEST_LOSS,
                    rawDelta = EloCalculator.GUEST_LOSS_DELTA,
                    timing = timing
                )
            }
            return
        }

        // Both registered. Lock in ascending-id order so two concurrent completions
        // sharing a player can never lose an update, then re-read the live ratings.
        val firstId = minOf(winner.id.value, loser.id.value)
        val secondId = maxOf(winner.id.value, loser.id.value)
        lockPlayerRowInCurrentTransaction(firstId)
        lockPlayerRowInCurrentTransaction(secondId)
        winner.refresh(flush = false)
        loser.refresh(flush = false)

        val deltas = EloCalculator.matchDeltas(
            RatingState(winner.rating, winner.ratedMatches),
            RatingState(loser.rating, loser.ratedMatches)
        )

        applyMatchSide(winner, match, tournament, deltas.winnerDelta, timing)
        applyMatchSide(loser, match, tournament, deltas.loserDelta, timing)
    }

    fun applyTournamentMatchRatings(tournament: TournamentDAO) {
        revertTournamentRating(tournament)

        tournament.phases
            .flatMap { it.matches }
            .filter { it.status == MatchStatus.COMPLETED.name }
            .sortedWith(
                compareBy<MatchDAO> { it.completedAt ?: it.updatedAt ?: it.createdAt }
                    .thenBy { it.id.value }
            )
            .forEach { applyMatchRating(it) }
    }

    fun revertMatchRating(match: MatchDAO) {
        val events = RatingEventDAO.find { RatingEventsTable.matchId eq match.id }.toList()
        revertEvents(events)
        RatingEventsTable.deleteWhere { matchId eq match.id }
    }

    fun revertTournamentRating(tournament: TournamentDAO) {
        val events = RatingEventDAO.find { RatingEventsTable.tournamentId eq tournament.id }.toList()
        revertEvents(events)
        RatingEventsTable.deleteWhere { tournamentId eq tournament.id }
    }

    private fun revertEvents(events: List<RatingEventDAO>) {
        val eventIdsToRemove = events.map { it.id }.toSet()
        val orderedEvents = events.sortedWith(
            compareByDescending<RatingEventDAO> { it.createdAt }.thenByDescending { it.id.value }
        )
        orderedEvents.forEach { event ->
            lockPlayerRowInCurrentTransaction(event.player.id.value)
            event.player.refresh(flush = false)
            event.player.rating = EloCalculator.applyFloor(event.player.rating - event.delta)
            if (event.reason == REASON_MATCH) {
                event.player.ratedMatches = (event.player.ratedMatches - 1).coerceAtLeast(0)
            }
            val latestRemainingEvent = RatingEventDAO.find { RatingEventsTable.playerId eq event.player.id }
                .filter { it.id !in eventIdsToRemove }
                .maxByOrNull { it.createdAt }
            event.player.lastRatedAt = latestRemainingEvent?.createdAt
        }
    }

    private fun applyMatchSide(
        player: PlayerDAO,
        match: MatchDAO,
        tournament: TournamentDAO,
        rawDelta: Int,
        timing: Instant
    ) {
        val newRating = EloCalculator.applyFloor(player.rating + rawDelta)
        val effectiveDelta = newRating - player.rating
        player.rating = newRating
        player.ratedMatches += 1
        player.lastRatedAt = timing
        RatingEventDAO.new {
            this.player = player
            this.match = match
            this.tournament = tournament
            reason = REASON_MATCH
            delta = effectiveDelta
            ratingAfter = newRating
            createdAt = timing
        }
    }

    private fun applyGuestResult(
        registered: PlayerDAO,
        match: MatchDAO,
        tournament: TournamentDAO,
        reason: String,
        rawDelta: Int,
        timing: Instant
    ) {
        lockPlayerRowInCurrentTransaction(registered.id.value)
        registered.refresh(flush = false)
        val newRating = EloCalculator.applyFloor(registered.rating + rawDelta)
        val effectiveDelta = newRating - registered.rating
        registered.rating = newRating
        // Guest matches do NOT consume the provisional-K window (ratedMatches
        // untouched) but they DO refresh activity, so decay is deferred.
        registered.lastRatedAt = timing
        RatingEventDAO.new {
            this.player = registered
            this.match = match
            this.tournament = tournament
            this.reason = reason
            delta = effectiveDelta
            ratingAfter = newRating
            createdAt = timing
        }
    }

    /**
     * Awards the completion bonus to the champion (full) and runner-up (half),
     * scaled by the registered field's size, phase count and average rating.
     * Assumes the caller has already set [TournamentDAO.champion]. Skipped when the
     * tournament had zero COMPLETED matches (a pure-walkover farce). External
     * recipients are skipped individually.
     */
    fun applyTournamentCompletionBonus(tournament: TournamentDAO, finalPhase: TournamentPhaseDAO) {
        val hasCompletedMatch = tournament.phases.any { phase ->
            phase.matches.any { it.status == MatchStatus.COMPLETED.name }
        }
        if (!hasCompletedMatch) return

        val registeredPlayers = tournament.players.filter { !it.external }
        if (registeredPlayers.isEmpty()) return

        val fieldSize = registeredPlayers.size
        val avgFieldRating = registeredPlayers.map { it.rating }.average()
        val phaseCount = tournament.phases.count().toInt()
        val (championBonus, finalistBonus) = EloCalculator.tournamentBonus(fieldSize, phaseCount, avgFieldRating)

        val champion = tournament.champion
        val runnerUp = resolveTournamentRunnerUp(finalPhase, champion)

        champion?.takeIf { !it.external }?.let { applyBonus(it, tournament, championBonus) }
        runnerUp?.takeIf { !it.external }?.let { applyBonus(it, tournament, finalistBonus) }
    }

    private fun applyBonus(player: PlayerDAO, tournament: TournamentDAO, bonus: Int) {
        lockPlayerRowInCurrentTransaction(player.id.value)
        player.refresh(flush = false)
        val newRating = EloCalculator.applyFloor(player.rating + bonus)
        val effectiveDelta = newRating - player.rating
        player.rating = newRating
        // The final match already refreshed lastRatedAt / ratedMatches; a bonus
        // moves only the rating.
        RatingEventDAO.new {
            this.player = player
            match = null
            this.tournament = tournament
            reason = REASON_TOURNAMENT_BONUS
            delta = effectiveDelta
            ratingAfter = newRating
            createdAt = Instant.now()
        }
    }

    /**
     * The tournament runner-up, mirroring the winner resolvers in
     * [bros.parraga.services.TournamentProgressionService]:
     *  - KNOCKOUT: the other assigned player of the final (WINNER-dep) match won by
     *    the champion. Null on a bye/unresolved final.
     *  - GROUP: standings sorted (points desc, wins desc, playerId asc), first != champion.
     *  - SWISS: final-round rankings sorted (points desc, playerId asc), first != champion.
     * Null when it cannot be resolved (e.g. champion is null or the field is degenerate).
     */
    fun resolveTournamentRunnerUp(finalPhase: TournamentPhaseDAO, champion: PlayerDAO?): PlayerDAO? {
        if (champion == null) return null
        return when (PhaseFormat.valueOf(finalPhase.format)) {
            PhaseFormat.KNOCKOUT -> resolveKnockoutRunnerUp(finalPhase, champion)
            PhaseFormat.GROUP -> resolveGroupRunnerUp(finalPhase, champion)
            PhaseFormat.SWISS -> resolveSwissRunnerUp(finalPhase, champion)
        }
    }

    private fun resolveKnockoutRunnerUp(phase: TournamentPhaseDAO, champion: PlayerDAO): PlayerDAO? {
        val highestRound = phase.matches.maxOfOrNull { it.round } ?: return null
        val finalMatch = phase.matches
            .filter { it.round == highestRound }
            .filter { match ->
                val dependencies = match.matchDependencies.toList()
                dependencies.isEmpty() || dependencies.all { it.requiredOutcome == Outcome.WINNER.name }
            }
            .firstOrNull { it.winner?.id == champion.id }
            ?: return null

        return when (champion.id) {
            finalMatch.player1?.id -> finalMatch.player2
            finalMatch.player2?.id -> finalMatch.player1
            else -> null
        }
    }

    private fun resolveGroupRunnerUp(phase: TournamentPhaseDAO, champion: PlayerDAO): PlayerDAO? {
        val groupIds = phase.matches.mapNotNull { it.group?.id }.distinct()
        if (groupIds.isEmpty()) return null

        return GroupStandingDAO.find { GroupStandingsTable.groupId inList groupIds }
            .toList()
            .sortedWith(
                compareByDescending<GroupStandingDAO> { it.points }
                    .thenByDescending { it.wins }
                    .thenBy { it.player.id.value }
            )
            .map { it.player }
            .firstOrNull { it.id != champion.id }
    }

    private fun resolveSwissRunnerUp(phase: TournamentPhaseDAO, champion: PlayerDAO): PlayerDAO? {
        val runnerUpId = SwissRankingsTable
            .selectAll()
            .where { (SwissRankingsTable.phaseId eq phase.id) and (SwissRankingsTable.round eq phase.rounds) }
            .toList()
            .sortedWith(
                compareByDescending<ResultRow> { it[SwissRankingsTable.points] }
                    .thenBy { it[SwissRankingsTable.playerId].value }
            )
            .map { it[SwissRankingsTable.playerId].value }
            .firstOrNull { it != champion.id.value }
            ?: return null
        return PlayerDAO[runnerUpId]
    }
}
