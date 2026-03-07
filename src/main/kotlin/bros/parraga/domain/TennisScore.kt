package bros.parraga.domain

import kotlinx.serialization.Serializable

@Serializable
data class TennisScore(
    val sets: List<SetScore>
) {
    fun validateForSubmission() {
        require(sets.isNotEmpty()) { "Score must include at least one set." }

        sets.forEachIndexed { index, set ->
            val setNumber = index + 1
            require(set.player1Games >= 0 && set.player2Games >= 0) {
                "Set $setNumber has negative game values."
            }

            set.tiebreak?.let { tiebreak ->
                require(tiebreak.player1Points >= 0 && tiebreak.player2Points >= 0) {
                    "Set $setNumber has negative tiebreak points."
                }
            }

            val hasSetWinner = when {
                set.player1Games > set.player2Games -> true
                set.player2Games > set.player1Games -> true
                set.tiebreak != null -> {
                    val p1 = set.tiebreak.player1Points
                    val p2 = set.tiebreak.player2Points
                    require(p1 != p2) { "Set $setNumber tiebreak cannot be tied." }
                    true
                }

                else -> false
            }

            require(hasSetWinner) {
                "Set $setNumber does not define a winner (equal games without tiebreak)."
            }
        }
    }

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

    fun toLib(): parraga.bros.tournament.domain.TennisScore =
        parraga.bros.tournament.domain.TennisScore(sets.map { it.toLib() })

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
    fun toLib(): parraga.bros.tournament.domain.SetScore =
        parraga.bros.tournament.domain.SetScore(
            player1Games,
            player2Games,
            tiebreak?.toLib()
        )

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
    fun toLib(): parraga.bros.tournament.domain.TiebreakScore =
        parraga.bros.tournament.domain.TiebreakScore(player1Points, player2Points)

    companion object {
        fun fromLib(lib: parraga.bros.tournament.domain.TiebreakScore): TiebreakScore {
            return TiebreakScore(lib.player1Points, lib.player2Points)
        }
    }
}
