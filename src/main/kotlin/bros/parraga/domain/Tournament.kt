package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Tournament(
    val id: Int,
    val name: String,
    val description: String?,
    val surface: SurfaceType?,
    val status: TournamentStatus,
    val clubId: Int,
    val startDate: Instant,
    val endDate: Instant,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val players: List<Player> = emptyList(),
    val phases: List<TournamentPhase> = emptyList()
)

enum class SurfaceType { CLAY, HARD, GRASS }

enum class TournamentStatus { DRAFT, STARTED, COMPLETED, CANCELLED, ABANDONED }
