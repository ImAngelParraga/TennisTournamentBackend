package bros.parraga.services.repositories.tournament.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UpdateTournamentRequest(
    val id: Int,
    val name: String? = null,
    val description: String? = null,
    val surface: String? = null,
    val clubId: Int? = null,
    val startDate: Instant? = null,
    val endDate: Instant? = null
)
