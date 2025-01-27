package bros.parraga.routes.dto

import kotlinx.datetime.Instant

data class UpdateTournamentRequest(
    val id: Int,
    val name: String? = null,
    val description: String? = null,
    val surface: String? = null,
    val clubId: Int? = null,
    val startDate: Instant? = null,
    val endDate: Instant? = null
)
