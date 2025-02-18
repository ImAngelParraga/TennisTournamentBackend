package bros.parraga.services.repositories.tournament

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.*
import bros.parraga.domain.TennisScore
import bros.parraga.domain.Tournament
import bros.parraga.domain.TournamentPhase
import bros.parraga.services.repositories.tournament.dto.AddPlayersRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentRequest
import bros.parraga.services.repositories.tournament.dto.TournamentPlayerRequest
import bros.parraga.services.repositories.tournament.dto.UpdateTournamentRequest
import io.ktor.server.plugins.*
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.and
import parraga.bros.tournament.domain.Format
import parraga.bros.tournament.domain.Match
import parraga.bros.tournament.domain.Phase
import parraga.bros.tournament.services.TournamentService
import java.time.Instant

class TournamentRepositoryImpl : TournamentRepository {
    override suspend fun getTournaments(): List<Tournament> = dbQuery { TournamentDAO.all().map { it.toDomain() } }

    override suspend fun getTournament(id: Int): Tournament = dbQuery { TournamentDAO[id].toDomain() }

    override suspend fun createTournament(request: CreateTournamentRequest): Tournament = dbQuery {
        TournamentDAO.new {
            name = request.name
            description = request.description
            surface = request.surface
            club = ClubDAO[request.clubId]
            startDate = request.startDate.toJavaInstant()
            endDate = request.endDate.toJavaInstant()
        }.toDomain()
    }

    override suspend fun updateTournament(request: UpdateTournamentRequest): Tournament = dbQuery {
        TournamentDAO.findByIdAndUpdate(request.id) {
            it.apply {
                request.name?.let { name = it }
                request.description?.let { description = it }
                request.surface?.let { surface = it }
                request.clubId?.let { club = ClubDAO[it] }
                request.startDate?.let { startDate = it.toJavaInstant() }
                request.endDate?.let { endDate = it.toJavaInstant() }
                updatedAt = Instant.now()
            }
        }?.toDomain()
            ?: throw EntityNotFoundException(
                DaoEntityID(request.id, TournamentsTable),
                TournamentDAO
            )
    }

    override suspend fun deleteTournament(id: Int) = dbQuery { TournamentDAO[id].delete() }

    override suspend fun addPlayersToTournament(
        tournamentId: Int,
        request: AddPlayersRequest
    ) = dbQuery {
        val tournament = TournamentDAO[tournamentId]

        request.players.forEach { request ->
            val player = getOrCreatePlayer(request)

            if (tournament.players.none { it.id == player.id }) {
                TournamentPlayerDAO.new {
                    this.tournament = tournament
                    this.player = player
                }
            }
        }
    }

    override suspend fun removePlayerFromTournament(tournamentId: Int, playerId: Int) = dbQuery {
        val association = TournamentPlayerDAO.find {
            TournamentPlayersTable.tournamentId.eq(tournamentId) and TournamentPlayersTable.playerId.eq(playerId)
        }.firstOrNull() ?: throw NotFoundException(
            "No association found between tournament $tournamentId and player $playerId"
        )

        association.delete()
    }

    override suspend fun startTournament(id: Int): TournamentPhase = dbQuery {
        val tournament = TournamentDAO[id]
        require(tournament.phases.count() > 0) { "Tournament has no phases" }

        val firstPhase = tournament.phases.first { it.phaseOrder == 1 }
        val playerIds = tournament.players.map { it.id.value }

        val phaseLib = Phase(
            firstPhase.phaseOrder,
            Format.valueOf(firstPhase.format),
            firstPhase.rounds,
            firstPhase.configuration.toPhaseConfigurationLib(),
            emptyList()
        )

        val firstPhaseMatches = TournamentService.startPhase(phaseLib, playerIds)

        saveMatchesForPhase(firstPhaseMatches, firstPhase)

        TournamentPhaseDAO[firstPhase.id.value].toDomain()
    }

    fun saveMatchesForPhase(
        matches: List<Match>,
        phaseDao: TournamentPhaseDAO
    ) {
        val fakeIdsToRealMatches = mutableMapOf<Int, MatchDAO>()

        matches.forEach { match ->
            val player1Dao = match.player1Id?.let { PlayerDAO[it] }
            val player2Dao = match.player2Id?.let { PlayerDAO[it] }
            val winnerDao = match.winnerId?.let {
                if (match.winnerId == match.player1Id) player1Dao else player2Dao
            }
            val tennisScore = TennisScore.fromLib(match.score)

            val matchDao = MatchDAO.new {
                phase = phaseDao
                round = match.round
                player1 = player1Dao
                player2 = player2Dao
                winner = winnerDao
                score = tennisScore
                status = match.status.name
            }

            fakeIdsToRealMatches.put(match.id, matchDao)

            match.dependencies.forEach { dependency ->
                MatchDependencyDAO.new {
                    matchId = matchDao.id
                    requiredMatch = fakeIdsToRealMatches[dependency.requiredMatchId]!!
                    requiredOutcome = dependency.requiredOutcome.name
                }
            }
        }
    }

    private fun getOrCreatePlayer(request: TournamentPlayerRequest) = when {
        request.playerId != null -> {
            PlayerDAO.findById(request.playerId)
                ?: throw EntityNotFoundException(
                    DaoEntityID(request.playerId, PlayersTable),
                    PlayerDAO
                )
        }

        request.name != null -> {
            PlayerDAO.new {
                name = request.name
                external = true
            }
        }

        else -> throw IllegalArgumentException("Invalid player request")
    }
}