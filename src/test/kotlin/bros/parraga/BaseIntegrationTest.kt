package bros.parraga

import bros.parraga.db.DatabaseFactory
import bros.parraga.db.DatabaseTables
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import java.util.Date
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before

abstract class BaseIntegrationTest {
    open val tables: List<Table> = DatabaseTables.all

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

    protected fun createAuthToken(
        subject: String,
        email: String? = null,
        name: String? = null
    ): String {
        val builder = JWT.create()
            .withIssuer("http://localhost/test-issuer")
            .withAudience("test-audience")
            .withSubject(subject)
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))

        email?.let { builder.withClaim("email", it) }
        name?.let { builder.withClaim("name", it) }

        return builder.sign(Algorithm.HMAC256("test-secret"))
    }
}
