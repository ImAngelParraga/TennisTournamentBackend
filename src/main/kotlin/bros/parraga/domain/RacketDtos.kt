package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class RacketDetails(
    val publicToken: String,
    val displayName: String,
    val brand: String?,
    val model: String?,
    val stringPattern: String?,
    val ownerName: String?,
    val latestStringing: RacketStringingHistoryEntry?,
    val history: List<RacketStringingHistoryEntry>
)

@Serializable
data class RacketStringingHistoryEntry(
    val id: Int,
    val stringingDate: String,
    val mainsKg: Double,
    val crossesKg: Double,
    val mainsLb: Double,
    val crossesLb: Double,
    val mainStringBrand: String?,
    val mainStringModel: String?,
    val mainStringGauge: String?,
    val crossStringBrand: String?,
    val crossStringModel: String?,
    val crossStringGauge: String?,
    val notes: String?,
    val stringerUsername: String,
    val createdAt: Instant,
    val updatedAt: Instant?
)
