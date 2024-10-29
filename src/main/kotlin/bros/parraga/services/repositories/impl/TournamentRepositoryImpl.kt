package bros.parraga.services.repositories.impl

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentsTable
import bros.parraga.domain.Tournament
import bros.parraga.domain.toDomain
import bros.parraga.services.repositories.TournamentRepository
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException

class TournamentRepositoryImpl : TournamentRepository {
    override suspend fun getTournaments(): List<Tournament> = dbQuery { TournamentDAO.all().map { it.toDomain() } }

    override suspend fun getTournament(id: Int): Tournament = dbQuery { TournamentDAO[id].toDomain() }

    override suspend fun createTournament(tournament: Tournament): Tournament = dbQuery {
        TournamentDAO.new {
            name = tournament.name
            description = tournament.description
            surface = tournament.surface
            startDate = tournament.startDate
            endDate = tournament.endDate
            created = tournament.created
            modified = tournament.modified
        }.toDomain()
    }

    override suspend fun updateTournament(tournament: Tournament): Tournament = dbQuery {
        TournamentDAO.findByIdAndUpdate(tournament.id) {
            it.name = tournament.name
            it.description = tournament.description
            it.surface = tournament.surface
            it.startDate = tournament.startDate
            it.endDate = tournament.endDate
            it.created = tournament.created
            it.modified = tournament.modified
        }?.toDomain() ?: throw EntityNotFoundException(DaoEntityID(tournament.id, TournamentsTable), TournamentDAO)
    }

    override suspend fun deleteTournament(id: Int) = dbQuery {
        TournamentDAO[id].delete()
    }
}