package bros.parraga.db.schema

import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import bros.parraga.domain.TournamentPhase
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import java.time.Instant

object TournamentPhasesTable : IntIdTable("tournament_phases") {
    val tournamentId = reference("tournament_id", TournamentsTable, onDelete = ReferenceOption.CASCADE)
    val phaseOrder = integer("phase_order").check { it greater 0 }
    val format = varchar("format", 20).check { column -> column.inList(PhaseFormat.entries.map { it.name }) }
    val rounds = integer("rounds").check { it greater 0 }
    val configuration = jsonb<PhaseConfiguration>(
        "configuration",
        { config -> Json.encodeToString(config) },
        { json -> Json.decodeFromString(json) }
    )
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").nullable()
}

class TournamentPhaseDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TournamentPhaseDAO>(TournamentPhasesTable)

    var tournament by TournamentDAO referencedOn TournamentPhasesTable.tournamentId
    var phaseOrder by TournamentPhasesTable.phaseOrder
    var format by TournamentPhasesTable.format
    var rounds by TournamentPhasesTable.rounds
    var configuration by TournamentPhasesTable.configuration
    var createdAt by TournamentPhasesTable.createdAt
    var updatedAt by TournamentPhasesTable.updatedAt

    fun toDomain() = TournamentPhase(
        id = id.value,
        tournamentId = tournament.id.value,
        phaseOrder = phaseOrder,
        format = PhaseFormat.valueOf(format),
        rounds = rounds,
        configuration = configuration,
        createdAt = createdAt.toKotlinInstant(),
        updatedAt = updatedAt?.toKotlinInstant()
    )
}