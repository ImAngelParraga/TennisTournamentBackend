package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class RacketVisibility {
    PUBLIC,
    PRIVATE
}

@Serializable
data class RacketSummary(
    val id: Int,
    val displayName: String,
    val brand: String?,
    val model: String?,
    val stringPattern: String?,
    val visibility: RacketVisibility,
    val latestStringing: RacketStringingHistoryEntry?,
    val createdAt: Instant,
    val updatedAt: Instant?
)

@Serializable
data class RacketDetails(
    val id: Int,
    val displayName: String,
    val brand: String?,
    val model: String?,
    val stringPattern: String?,
    val visibility: RacketVisibility,
    val latestStringing: RacketStringingHistoryEntry?,
    val history: List<RacketStringingHistoryEntry>,
    val createdAt: Instant,
    val updatedAt: Instant?
)

@Serializable
data class RacketStringingHistoryEntry(
    val id: Int,
    val stringingDate: String,
    val mainsTensionKg: Double,
    val crossesTensionKg: Double,
    val mainStringType: String?,
    val crossStringType: String?,
    val performanceNotes: String?,
    val createdAt: Instant,
    val updatedAt: Instant?
)
