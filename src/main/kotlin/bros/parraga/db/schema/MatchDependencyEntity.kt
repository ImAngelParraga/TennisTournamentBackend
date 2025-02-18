package bros.parraga.db.schema

import bros.parraga.domain.MatchDependency
import bros.parraga.domain.Outcome
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID

object MatchDependenciesTable : CompositeIdTable("match_dependencies") {
    val matchId = reference("match_id", MatchesTable)
    val requiredMatchId = reference("required_match_id", MatchesTable)
    val requiredOutcome =
        varchar("required_outcome", 20).check { column -> column.inList(Outcome.entries.map { it.name }) }

    init {
        addIdColumn(matchId)
        addIdColumn(requiredMatchId)
    }

    override val primaryKey = PrimaryKey(matchId, requiredMatchId)
}

class MatchDependencyDAO(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<MatchDependencyDAO>(MatchDependenciesTable)

    var matchId by MatchDependenciesTable.matchId
    var requiredMatch by MatchDAO referencedOn MatchDependenciesTable.requiredMatchId
    var requiredOutcome by MatchDependenciesTable.requiredOutcome

    fun toDomain(): MatchDependency = MatchDependency(
        requiredMatch.id.value,
        Outcome.valueOf(requiredOutcome)
    )
}