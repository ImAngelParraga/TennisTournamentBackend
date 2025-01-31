package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class TournamentPhase(
    val id: Int,
    val tournamentId: Int,
    val phaseOrder: Int,
    val format: String,
    val rounds: Int,
    val configuration: PhaseConfiguration,
    val createdAt: Instant,
    val updatedAt: Instant?
)