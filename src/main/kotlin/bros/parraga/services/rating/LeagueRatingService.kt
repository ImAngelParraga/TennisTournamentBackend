package bros.parraga.services.rating

import bros.parraga.db.lockLeagueRowInCurrentTransaction
import bros.parraga.db.schema.LeagueDAO
import bros.parraga.db.schema.LeagueMatchesTable
import bros.parraga.db.schema.LeagueMemberDAO
import bros.parraga.db.schema.LeagueMembersTable
import bros.parraga.db.schema.LeagueRatingEventDAO
import bros.parraga.db.schema.LeagueRatingEventsTable
import bros.parraga.services.rating.EloCalculator.RatingState
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll

object LeagueRatingService {
    private const val REASON_MATCH = "MATCH"

    fun recalculateLeague(league: LeagueDAO) {
        lockLeagueRowInCurrentTransaction(league.id.value)

        LeagueMemberDAO.find { LeagueMembersTable.leagueId eq league.id }
            .forEach { member ->
                member.rating = EloCalculator.START_RATING
                member.ratedMatches = 0
                member.wins = 0
                member.losses = 0
            }
        LeagueRatingEventsTable.deleteWhere { leagueId eq league.id }

        val matches = LeagueMatchesTable
            .selectAll()
            .where { LeagueMatchesTable.leagueId eq league.id }
            .orderBy(LeagueMatchesTable.playedAt to org.jetbrains.exposed.sql.SortOrder.ASC, LeagueMatchesTable.id to org.jetbrains.exposed.sql.SortOrder.ASC)

        matches.forEach { row ->
            val match = bros.parraga.db.schema.LeagueMatchDAO[row[LeagueMatchesTable.id]]
            val winnerMember = requireMember(league.id.value, match.winner.id.value)
            val loser = if (match.winner.id == match.player1.id) match.player2 else match.player1
            val loserMember = requireMember(league.id.value, loser.id.value)

            val deltas = EloCalculator.matchDeltas(
                RatingState(winnerMember.rating, winnerMember.ratedMatches),
                RatingState(loserMember.rating, loserMember.ratedMatches)
            )
            applySide(league, match, winnerMember, deltas.winnerDelta, won = true)
            applySide(league, match, loserMember, deltas.loserDelta, won = false)
        }
    }

    private fun requireMember(leagueId: Int, playerId: Int): LeagueMemberDAO =
        LeagueMemberDAO.find {
            (LeagueMembersTable.leagueId eq leagueId) and (LeagueMembersTable.playerId eq playerId)
        }.firstOrNull() ?: error("Player $playerId is not a member of league $leagueId")

    private fun applySide(
        league: LeagueDAO,
        match: bros.parraga.db.schema.LeagueMatchDAO,
        member: LeagueMemberDAO,
        rawDelta: Int,
        won: Boolean
    ) {
        val newRating = EloCalculator.applyFloor(member.rating + rawDelta)
        val effectiveDelta = newRating - member.rating
        member.rating = newRating
        member.ratedMatches += 1
        if (won) member.wins += 1 else member.losses += 1
        LeagueRatingEventDAO.new {
            this.league = league
            leagueMatch = match
            player = member.player
            reason = REASON_MATCH
            delta = effectiveDelta
            ratingAfter = newRating
            createdAt = match.playedAt
        }
    }
}

