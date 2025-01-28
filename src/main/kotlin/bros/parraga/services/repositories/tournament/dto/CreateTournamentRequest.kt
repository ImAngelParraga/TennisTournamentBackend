package bros.parraga.services.repositories.tournament.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CreateTournamentRequest(
    val name: String,
    val description: String?,
    val surface: String?,
    val clubId: Int,
    val startDate: Instant,
    val endDate: Instant
)