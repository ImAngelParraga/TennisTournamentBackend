package bros.parraga.domain

import kotlinx.serialization.Serializable

@Serializable
data class TennisScore(
    val sets: List<SetScore>
) {
    fun getWinnerId(player1Id: Int?, player2Id: Int?): Int? {
        if (player1Id == null || player2Id == null) return null
        val player1Sets = sets.count { it.player1Games > it.player2Games }
        val player2Sets = sets.count { it.player2Games > it.player1Games }
        return when {
            player1Sets > player2Sets -> player1Id
            player2Sets > player1Sets -> player2Id
            else -> null
        }
    }

    companion object {
        fun fromLib(lib: parraga.bros.tournament.domain.TennisScore?): TennisScore? {
            return lib?.let { TennisScore(lib.sets.map { SetScore.fromLib(it) }) }
        }
    }
}

@Serializable
data class SetScore(
    val player1Games: Int,
    val player2Games: Int,
    val tiebreak: TiebreakScore?
) {
    companion object {
        fun fromLib(lib: parraga.bros.tournament.domain.SetScore): SetScore {
            return SetScore(
                lib.player1Games,
                lib.player2Games,
                lib.tiebreak?.let { TiebreakScore.fromLib(it) }
            )
        }
    }
}

@Serializable
data class GameScore(
    val player1Points: Int,
    val player2Points: Int
) {
    companion object {
        fun fromLib(lib: parraga.bros.tournament.domain.GameScore): GameScore {
            return GameScore(lib.player1Points, lib.player2Points)
        }
    }
}

@Serializable
data class TiebreakScore(
    val player1Points: Int,
    val player2Points: Int
) {
    companion object {
        fun fromLib(lib: parraga.bros.tournament.domain.TiebreakScore): TiebreakScore {
            return TiebreakScore(lib.player1Points, lib.player2Points)
        }
    }
}
