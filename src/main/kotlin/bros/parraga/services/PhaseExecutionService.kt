package bros.parraga.services

import bros.parraga.db.lockPhaseRowInCurrentTransaction
import bros.parraga.db.schema.GroupDAO
import bros.parraga.db.schema.GroupStandingDAO
import bros.parraga.db.schema.GroupStandingsTable
import bros.parraga.db.schema.MatchDAO
import bros.parraga.db.schema.MatchDependencyDAO
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.SwissRankingsTable
import bros.parraga.db.schema.TournamentPhaseDAO
import bros.parraga.domain.MatchStatus
import bros.parraga.domain.Outcome
import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.log2
import parraga.bros.tournament.domain.Format
import parraga.bros.tournament.domain.Match as LibMatch
import parraga.bros.tournament.domain.MatchStatus as LibMatchStatus
import parraga.bros.tournament.domain.Phase
import parraga.bros.tournament.domain.SeededParticipant as LibSeededParticipant
import parraga.bros.tournament.services.TournamentService

object PhaseExecutionService {

    fun startPhase(
        phaseDao: TournamentPhaseDAO,
        participants: List<LibSeededParticipant>
    ) {
        lockPhaseRowInCurrentTransaction(phaseDao.id.value)
        if (phaseDao.matches.any()) return

        val format = PhaseFormat.valueOf(phaseDao.format)
        val roundsToPlay = computeRounds(phaseDao, participants.size)
        phaseDao.rounds = roundsToPlay
        phaseDao.updatedAt = Instant.now()

        val phaseLib = Phase(
            order = phaseDao.phaseOrder,
            format = Format.valueOf(phaseDao.format),
            rounds = roundsToPlay,
            configuration = phaseDao.configuration.toPhaseConfigurationLib(),
            matches = emptyList()
        )

        val generatedMatches = TournamentService.startPhaseWithParticipants(phaseLib, participants)
        val phaseMatches = if (format == PhaseFormat.KNOCKOUT) {
            generatedMatches.filter { it.round <= roundsToPlay }
        } else {
            generatedMatches
        }

        val groupsByTempId = if (format == PhaseFormat.GROUP) {
            createGroupsForPhase(phaseDao, phaseMatches)
        } else {
            emptyMap()
        }
        if (format == PhaseFormat.GROUP) {
            createGroupStandings(groupsByTempId, phaseMatches)
        }
        if (format == PhaseFormat.SWISS) {
            recordSwissRankings(phaseDao, round = 0, pointsByPlayer = emptyMap())
        }

        val createdMatches = saveMatchesForPhase(phaseMatches, phaseDao, groupsByTempId)
        applyWalkovers(createdMatches)
    }

    fun createNextSwissRound(phaseDao: TournamentPhaseDAO) {
        val phaseLib = phaseDao.toLibPhase()
        val nextRoundMatches = TournamentService.startNextRound(phaseLib)
        if (nextRoundMatches.isEmpty()) return

        val createdMatches = saveMatchesForPhase(nextRoundMatches, phaseDao, emptyMap())
        applyWalkovers(createdMatches)
    }

    fun startNextPhaseIfNeeded(completedPhase: TournamentPhaseDAO) {
        val nextPhase = completedPhase.tournament.phases
            .filter { it.phaseOrder > completedPhase.phaseOrder }
            .minByOrNull { it.phaseOrder }
            ?: return

        lockPhaseRowInCurrentTransaction(nextPhase.id.value)
        if (nextPhase.matches.any()) return

        val participants = when (PhaseFormat.valueOf(completedPhase.format)) {
            PhaseFormat.KNOCKOUT -> getKnockoutAdvancers(completedPhase)
            PhaseFormat.GROUP -> getGroupAdvancers(completedPhase)
            PhaseFormat.SWISS -> getSwissAdvancers(completedPhase)
        }
        if (participants.size < 2) return

        startPhase(nextPhase, participants)
    }

    fun recordSwissRankings(
        phaseDao: TournamentPhaseDAO,
        round: Int,
        pointsByPlayer: Map<Int, Int>
    ) {
        SwissRankingsTable.deleteWhere {
            (SwissRankingsTable.phaseId eq phaseDao.id) and (SwissRankingsTable.round eq round)
        }

        phaseDao.tournament.players
            .sortedBy { it.id.value }
            .forEach { player ->
                SwissRankingsTable.insert {
                    it[phaseId] = phaseDao.id
                    it[playerId] = player.id
                    it[SwissRankingsTable.round] = round
                    it[points] = pointsByPlayer[player.id.value] ?: 0
                    it[updatedAt] = Instant.now()
                }
            }
    }

    private fun computeRounds(phaseDao: TournamentPhaseDAO, playerCount: Int): Int {
        return when (val format = PhaseFormat.valueOf(phaseDao.format)) {
            PhaseFormat.KNOCKOUT -> {
                val config = phaseDao.configuration as? PhaseConfiguration.KnockoutConfig
                    ?: throw IllegalArgumentException("Knockout configuration is required")
                computeKnockoutRounds(playerCount, config.qualifiers)
            }

            PhaseFormat.GROUP -> {
                val config = phaseDao.configuration as? PhaseConfiguration.GroupConfig
                    ?: throw IllegalArgumentException("Group configuration is required")
                require(playerCount == config.groupCount * config.teamsPerGroup) {
                    "Group phase requires exactly ${config.groupCount * config.teamsPerGroup} players but got $playerCount"
                }
                if (config.teamsPerGroup % 2 == 0) config.teamsPerGroup - 1 else config.teamsPerGroup
            }

            PhaseFormat.SWISS -> ceil(log2(playerCount.toDouble())).toInt().coerceAtLeast(1)
        }
    }

    private fun saveMatchesForPhase(
        matches: List<LibMatch>,
        phaseDao: TournamentPhaseDAO,
        groupsByTempId: Map<Int, GroupDAO>
    ): List<MatchDAO> {
        val fakeIdsToRealMatches = mutableMapOf<Int, MatchDAO>()
        val roundSlotsByMatchId = computeRoundSlotsByMatchId(matches)

        matches.forEach { match ->
            val player1Dao = match.player1Id?.let { PlayerDAO[it] }
            val player2Dao = match.player2Id?.let { PlayerDAO[it] }
            val winnerDao = match.winnerId?.let {
                if (match.winnerId == match.player1Id) player1Dao else player2Dao
            }

            val matchDao = MatchDAO.new {
                phase = phaseDao
                round = match.round
                roundSlot = roundSlotsByMatchId.getValue(match.id)
                group = match.groupId?.let { groupsByTempId.getValue(it) }
                player1 = player1Dao
                player2 = player2Dao
                winner = winnerDao
                score = bros.parraga.domain.TennisScore.fromLib(match.score)
                status = match.status.name
            }

            fakeIdsToRealMatches[match.id] = matchDao
        }

        matches.forEach { match ->
            val matchDao = fakeIdsToRealMatches.getValue(match.id)
            match.dependencies.forEach { dependency ->
                val requiredMatch = fakeIdsToRealMatches.getValue(dependency.requiredMatchId)
                MatchDependencyDAO.new {
                    matchId = matchDao.id
                    this.requiredMatch = requiredMatch
                    requiredOutcome = dependency.requiredOutcome.name
                }
            }
        }

        return fakeIdsToRealMatches.values.sortedBy { it.id.value }
    }

    private fun applyWalkovers(createdMatches: List<MatchDAO>) {
        createdMatches
            .filter { it.status == MatchStatus.WALKOVER.name }
            .forEach { match ->
                if (match.winner == null) {
                    match.winner = match.player1 ?: match.player2
                    match.updatedAt = Instant.now()
                }
                TournamentProgressionService.onMatchCompleted(match)
            }
    }

    private fun createGroupsForPhase(
        phaseDao: TournamentPhaseDAO,
        matches: List<LibMatch>
    ): Map<Int, GroupDAO> {
        val groupIds = matches.mapNotNull { it.groupId }.distinct().sorted()
        return groupIds.associateWith { groupId ->
            GroupDAO.new {
                phase = phaseDao
                name = groupName(groupId)
            }
        }
    }

    private fun createGroupStandings(
        groupsByTempId: Map<Int, GroupDAO>,
        matches: List<LibMatch>
    ) {
        matches.groupBy { it.groupId }.forEach { (tempGroupId, groupMatches) ->
            val groupDao = groupsByTempId[tempGroupId] ?: return@forEach
            groupMatches
                .flatMap { listOfNotNull(it.player1Id, it.player2Id) }
                .distinct()
                .sorted()
                .forEach { playerId ->
                    GroupStandingsTable.insert {
                        it[groupId] = groupDao.id
                        it[GroupStandingsTable.playerId] = PlayerDAO[playerId].id
                        it[matchesPlayed] = 0
                        it[wins] = 0
                        it[points] = 0
                    }
                }
        }
    }

    private fun getKnockoutAdvancers(phaseDao: TournamentPhaseDAO): List<LibSeededParticipant> {
        val highestRound = phaseDao.matches.maxOfOrNull { it.round } ?: return emptyList()
        return phaseDao.matches
            .filter { it.round == highestRound }
            .filter { match ->
                val dependencies = match.matchDependencies.toList()
                dependencies.isEmpty() || dependencies.all { it.requiredOutcome == Outcome.WINNER.name }
            }
            .sortedBy { it.roundSlot }
            .mapNotNull { it.winner?.id?.value }
            .map { LibSeededParticipant(it) }
    }

    private fun getGroupAdvancers(phaseDao: TournamentPhaseDAO): List<LibSeededParticipant> {
        val config = phaseDao.configuration as? PhaseConfiguration.GroupConfig
            ?: throw IllegalArgumentException("Group configuration is required")
        val groups = GroupDAO.find { bros.parraga.db.schema.GroupsTable.phaseId eq phaseDao.id }
            .sortedBy { it.id.value }

        val rankedByGroup = groups.associateWith { group ->
            GroupStandingDAO.find { bros.parraga.db.schema.GroupStandingsTable.groupId eq group.id }
                .toList()
                .sortedWith(
                    compareByDescending<GroupStandingDAO> { it.points }
                        .thenByDescending { it.wins }
                        .thenBy { it.player.id.value }
                )
        }

        val advancers = mutableListOf<LibSeededParticipant>()
        repeat(config.advancingPerGroup) { position ->
            groups.forEach { group ->
                rankedByGroup[group]?.getOrNull(position)?.player?.id?.value?.let { advancers += LibSeededParticipant(it) }
            }
        }
        return advancers
    }

    private fun getSwissAdvancers(phaseDao: TournamentPhaseDAO): List<LibSeededParticipant> {
        val config = phaseDao.configuration as? PhaseConfiguration.SwissConfig
            ?: throw IllegalArgumentException("Swiss configuration is required")
        val pointsByPlayer = mutableMapOf<Int, Int>().withDefault { 0 }
        phaseDao.matches.forEach { match ->
            val winnerId = match.winner?.id?.value ?: return@forEach
            pointsByPlayer[winnerId] = (pointsByPlayer[winnerId] ?: 0) + config.pointsPerWin
        }
        val players = phaseDao.tournament.players.toList()
        val advancingCount = config.advancingCount ?: players.size

        return players
            .sortedWith(
                compareByDescending<PlayerDAO> { pointsByPlayer[it.id.value] ?: 0 }
                    .thenBy { it.id.value }
            )
            .take(advancingCount)
            .map { LibSeededParticipant(it.id.value) }
    }

    private fun computeRoundSlotsByMatchId(matches: List<LibMatch>): Map<Int, Int> {
        return matches
            .groupBy { it.round }
            .flatMap { (_, roundMatches) ->
                roundMatches.sortedBy { it.id }.mapIndexed { index, match -> match.id to index + 1 }
            }
            .toMap()
    }

    private fun computeKnockoutRounds(playerCount: Int, qualifiers: Int): Int {
        require(playerCount >= 2) { "Tournament must have at least 2 players" }
        val allowedQualifiers = allowedKnockoutQualifiers(playerCount)
        require(qualifiers in allowedQualifiers) {
            "Knockout qualifiers=$qualifiers are invalid for $playerCount players; allowed values are ${allowedQualifiers.joinToString()}"
        }

        val totalRounds = ceil(log2(playerCount.toDouble())).toInt()
        val targetRounds = log2(qualifiers.toDouble()).toInt()
        val roundsToPlay = totalRounds - targetRounds
        require(roundsToPlay > 0) { "Computed rounds must be greater than 0" }
        return roundsToPlay
    }

    private fun allowedKnockoutQualifiers(playerCount: Int): List<Int> {
        val qualifiers = mutableListOf<Int>()
        var value = 1
        while (value < playerCount) {
            qualifiers += value
            value *= 2
        }
        return qualifiers
    }

    private fun TournamentPhaseDAO.toLibPhase(): Phase = Phase(
        order = phaseOrder,
        format = Format.valueOf(format),
        rounds = rounds,
        configuration = configuration.toPhaseConfigurationLib(),
        matches = matches.sortedBy { it.id.value }.map { it.toLib() }
    )

    private fun MatchDAO.toLib(): LibMatch = LibMatch(
        id = id.value,
        round = round,
        groupId = group?.id?.value,
        player1Id = player1?.id?.value,
        player2Id = player2?.id?.value,
        winnerId = winner?.id?.value,
        score = score?.toLib(),
        status = LibMatchStatus.valueOf(status),
        dependencies = matchDependencies.map {
            parraga.bros.tournament.domain.MatchDependency(
                requiredMatchId = it.requiredMatch.id.value,
                requiredOutcome = parraga.bros.tournament.domain.Outcome.valueOf(it.requiredOutcome)
            )
        }
    )

    private fun groupName(groupId: Int): String {
        val index = groupId - 1
        return if (index in 0..25) {
            ('A'.code + index).toChar().toString()
        } else {
            "G$groupId"
        }
    }
}
