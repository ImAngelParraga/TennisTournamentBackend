package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class TournamentBasic(
    val id: Int,
    val name: String,
    val description: String?,
    val surface: SurfaceType?,
    val clubId: Int,
    val startDate: Instant,
    val endDate: Instant,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

@Serializable
data class TournamentPhaseSummary(
    val id: Int,
    val tournamentId: Int,
    val phaseOrder: Int,
    val format: PhaseFormat,
    val rounds: Int,
    val configuration: PhaseConfiguration,
    val createdAt: Instant,
    val updatedAt: Instant?
)
