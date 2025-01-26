package bros.parraga.services.repositories.impl

import bros.parraga.db.DatabaseFactory.dbQuery
import bros.parraga.db.schema.TournamentDAO
import bros.parraga.db.schema.TournamentsTable
import bros.parraga.domain.Tournament
import bros.parraga.domain.fromDomain
import bros.parraga.domain.toDomain
import bros.parraga.services.repositories.TournamentRepository
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException

class TournamentRepositoryImpl : TournamentRepository {
    override suspend fun getTournaments(): List<Tournament> = dbQuery { TournamentDAO.all().map { it.toDomain() } }

    override suspend fun getTournament(id: Int): Tournament = dbQuery { TournamentDAO[id].toDomain() }

    override suspend fun createTournament(tournament: Tournament): Tournament = dbQuery {
        TournamentDAO.new { fromDomain(tournament) }.toDomain()
    }

    override suspend fun updateTournament(id: Int, tournament: Tournament): Tournament = dbQuery {
        TournamentDAO.findByIdAndUpdate(id) { it.fromDomain(tournament) }?.toDomain() ?: throw EntityNotFoundException(
            DaoEntityID(tournament.id, TournamentsTable),
            TournamentDAO
        )
    }

    override suspend fun deleteTournament(id: Int) = dbQuery {
        TournamentDAO[id].delete()
    }
}