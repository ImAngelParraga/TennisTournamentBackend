package bros.parraga

import bros.parraga.db.DatabaseFactory
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
    abstract val tables: List<Table>

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
            SchemaUtils.create(*tables.toTypedArray())
        }
    }

    @After
    fun tearDown() {
        transaction {
            SchemaUtils.drop(*tables.toTypedArray())
        }
    }
}
