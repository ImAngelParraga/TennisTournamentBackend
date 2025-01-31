package bros.parraga.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PhaseConfiguration {
    @Serializable
    @SerialName("knockout")
    data class KnockoutConfig(
        val thirdPlacePlayoff: Boolean,
        val seedByPreviousPhase: Boolean
    ) : PhaseConfiguration

    @Serializable
    @SerialName("group")
    data class GroupConfig(
        val groupCount: Int,
        val teamsPerGroup: Int,
        val advancingPerGroup: Int
    ) : PhaseConfiguration

    @Serializable
    @SerialName("swiss")
    data class SwissConfig(
        val pointsPerWin: Int,
        val pointsPerDraw: Int
    ) : PhaseConfiguration
}