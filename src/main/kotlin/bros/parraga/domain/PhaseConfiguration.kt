package bros.parraga.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PhaseConfiguration {

    fun toPhaseConfigurationLib(): parraga.bros.tournament.domain.PhaseConfiguration

    @Serializable
    @SerialName("knockout")
    data class KnockoutConfig(
        val thirdPlacePlayoff: Boolean
    ) : PhaseConfiguration {
        override fun toPhaseConfigurationLib(): parraga.bros.tournament.domain.PhaseConfiguration =
            parraga.bros.tournament.domain.PhaseConfiguration.KnockoutConfig(thirdPlacePlayoff)
    }

    @Serializable
    @SerialName("group")
    data class GroupConfig(
        val groupCount: Int,
        val teamsPerGroup: Int,
        val advancingPerGroup: Int
    ) : PhaseConfiguration {
        override fun toPhaseConfigurationLib(): parraga.bros.tournament.domain.PhaseConfiguration =
            parraga.bros.tournament.domain.PhaseConfiguration.GroupConfig(groupCount, teamsPerGroup, advancingPerGroup)
    }

    @Serializable
    @SerialName("swiss")
    data class SwissConfig(
        val pointsPerWin: Int
    ) : PhaseConfiguration {
        override fun toPhaseConfigurationLib(): parraga.bros.tournament.domain.PhaseConfiguration =
            parraga.bros.tournament.domain.PhaseConfiguration.SwissConfig(pointsPerWin)
    }
}