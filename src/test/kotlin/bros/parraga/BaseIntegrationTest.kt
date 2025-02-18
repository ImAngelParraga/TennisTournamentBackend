package bros.parraga

import bros.parraga.db.DatabaseFactory
import bros.parraga.db.schema.ClubsTable
import bros.parraga.db.schema.GroupStandingsTable
import bros.parraga.db.schema.GroupsTable
import bros.parraga.db.schema.MatchDependenciesTable
import bros.parraga.db.schema.MatchesTable
import bros.parraga.db.schema.PlayersTable
import bros.parraga.db.schema.SwissRankingsTable
import bros.parraga.db.schema.TournamentPhasesTable
import bros.parraga.db.schema.TournamentPlayersTable
import bros.parraga.db.schema.TournamentsTable
import bros.parraga.db.schema.UsersTable
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before

abstract class BaseIntegrationTest {
    open val tables: List<Table> = listOf(
        UsersTable,
        PlayersTable,
        ClubsTable,
        TournamentsTable,
        TournamentPlayersTable,
        TournamentPhasesTable,
        GroupsTable,
        GroupStandingsTable,
        SwissRankingsTable,
        MatchesTable,
        MatchDependenciesTable
    )

    protected fun testApplicationWithClient(test: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application {
            testModule()
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                })
            }
        }

        test(client)
    }

    @Before
    fun setup() {
        DatabaseFactory.init(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )

        transaction {
            SchemaUtils.create(*tables.toTypedArray(), inBatch = true)
        }
    }

    @After
    fun tearDown() {
        transaction {
            SchemaUtils.drop(*tables.toTypedArray(), inBatch = true)
        }
    }
}
