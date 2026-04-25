package bros.parraga.db

import bros.parraga.db.schema.AchievementsTable
import bros.parraga.db.schema.ClubsTable
import bros.parraga.db.schema.ClubAdminsTable
import bros.parraga.db.schema.GroupStandingsTable
import bros.parraga.db.schema.GroupsTable
import bros.parraga.db.schema.MatchDependenciesTable
import bros.parraga.db.schema.MatchesTable
import bros.parraga.db.schema.PlayersTable
import bros.parraga.db.schema.RacketStringingsTable
import bros.parraga.db.schema.RacketsTable
import bros.parraga.db.schema.SwissRankingsTable
import bros.parraga.db.schema.TournamentPhasesTable
import bros.parraga.db.schema.TournamentPlayersTable
import bros.parraga.db.schema.TournamentsTable
import bros.parraga.db.schema.UserTrainingsTable
import bros.parraga.db.schema.UsersTable
import org.jetbrains.exposed.sql.Table

object DatabaseTables {
    val all: List<Table> = listOf(
        AchievementsTable,
        UsersTable,
        UserTrainingsTable,
        PlayersTable,
        RacketsTable,
        RacketStringingsTable,
        ClubsTable,
        ClubAdminsTable,
        TournamentsTable,
        TournamentPlayersTable,
        TournamentPhasesTable,
        GroupsTable,
        GroupStandingsTable,
        SwissRankingsTable,
        MatchesTable,
        MatchDependenciesTable
    )
}
