package bros.parraga.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// A club asking to be onboarded. Reviewed manually by the platform operator,
// who then provisions the club via POST /clubs.
@Serializable
data class ClubContactRequest(
    val id: Int,
    val clubName: String,
    val contactName: String,
    val email: String,
    val phone: String? = null,
    val message: String? = null,
    val createdAt: Instant
)
