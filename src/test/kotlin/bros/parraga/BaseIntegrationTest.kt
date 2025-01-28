package bros.parraga

import bros.parraga.db.DatabaseFactory
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before

abstract class BaseIntegrationTest {
    abstract val tables: List<Table>

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
