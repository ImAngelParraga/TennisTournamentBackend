package bros.parraga.routes.dto

import kotlinx.datetime.Instant

data class CreateTournamentRequest(
    val name: String,
    val description: String?,
    val surface: String?,
    val clubId: Int,
    val startDate: Instant,
    val endDate: Instant
)