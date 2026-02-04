package bros.parraga.services.repositories.tournament.dto

import bros.parraga.domain.PhaseConfiguration
import bros.parraga.domain.PhaseFormat
import kotlinx.serialization.Serializable

@Serializable
data class CreatePhaseRequest(
    val phaseOrder: Int,
    val format: PhaseFormat,
    val configuration: PhaseConfiguration
)
