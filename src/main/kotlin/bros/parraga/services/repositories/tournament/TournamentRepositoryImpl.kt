package bros.parraga.services.repositories.tournament

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.*
import bros.parraga.domain.Tournament
import bros.parraga.services.repositories.tournament.dto.AddPlayersRequest
import bros.parraga.services.repositories.tournament.dto.CreateTournamentRequest
import bros.parraga.services.repositories.tournament.dto.TournamentPlayerRequest
import bros.parraga.services.repositories.tournament.dto.UpdateTournamentRequest
import io.ktor.server.plugins.*
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.and
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
                    this.tournamentId = tournament.id
                    playerId = player.id
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