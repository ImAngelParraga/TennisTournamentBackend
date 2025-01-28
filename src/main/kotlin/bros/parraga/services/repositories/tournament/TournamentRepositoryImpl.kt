package bros.parraga.services.repositories.tournament

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.ClubDAO
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentsTable
import bros.parraga.domain.Tournament
import bros.parraga.domain.toDomain
import bros.parraga.services.repositories.tournament.dto.CreateTournamentRequest
import bros.parraga.services.repositories.tournament.dto.UpdateTournamentRequest
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
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
                modifiedAt = Instant.now()
            }
        }?.toDomain()
            ?: throw EntityNotFoundException(
                DaoEntityID(request.id, TournamentsTable),
                TournamentDAO
            )
    }

    override suspend fun deleteTournament(id: Int) = dbQuery {
        TournamentDAO[id].delete()
    }
}