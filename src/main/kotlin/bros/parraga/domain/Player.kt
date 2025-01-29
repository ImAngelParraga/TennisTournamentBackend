package bros.parraga.domain

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: Int,
    val name: String,
    val external: Boolean,
    val user: User? = null,
    val tournaments: List<Tournament> = emptyList<Tournament>()
)