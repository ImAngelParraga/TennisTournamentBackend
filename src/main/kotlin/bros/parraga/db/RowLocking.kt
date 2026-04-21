package bros.parraga.db

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager

fun Transaction.lockTournamentRow(tournamentId: Int) {
    lockRow("tournaments", tournamentId)
}

fun Transaction.lockPhaseRow(phaseId: Int) {
    lockRow("tournament_phases", phaseId)
}

fun Transaction.lockMatchRow(matchId: Int) {
    lockRow("matches", matchId)
}

fun lockPhaseRowInCurrentTransaction(phaseId: Int) {
    TransactionManager.current().lockPhaseRow(phaseId)
}

fun lockTournamentRowInCurrentTransaction(tournamentId: Int) {
    TransactionManager.current().lockTournamentRow(tournamentId)
}

fun lockMatchRowInCurrentTransaction(matchId: Int) {
    TransactionManager.current().lockMatchRow(matchId)
}

private fun Transaction.lockRow(tableName: String, id: Int) {
    exec("SELECT id FROM $tableName WHERE id = $id FOR UPDATE") { resultSet ->
        // Force row lock acquisition by consuming at least one row if present.
        resultSet.next()
    }
}
