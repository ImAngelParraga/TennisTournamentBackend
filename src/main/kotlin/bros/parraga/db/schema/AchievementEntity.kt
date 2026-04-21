package bros.parraga.db.schema

import bros.parraga.domain.Achievement
import bros.parraga.domain.AchievementRuleType
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object AchievementsTable : IntIdTable("achievements") {
    val key = varchar("key", 100).uniqueIndex()
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val ruleType = varchar("rule_type", 100).check { it.inList(AchievementRuleType.entries.map { type -> type.name }) }
    val threshold = integer("threshold")
    val active = bool("active").default(true)
}

class AchievementDAO(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AchievementDAO>(AchievementsTable)

    var key by AchievementsTable.key
    var name by AchievementsTable.name
    var description by AchievementsTable.description
    var ruleType by AchievementsTable.ruleType
    var threshold by AchievementsTable.threshold
    var active by AchievementsTable.active

    fun toDomain() = Achievement(
        id = id.value,
        key = key,
        name = name,
        description = description
    )
}
