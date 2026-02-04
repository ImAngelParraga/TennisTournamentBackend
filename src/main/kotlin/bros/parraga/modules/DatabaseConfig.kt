package bros.parraga.modules

import bros.parraga.db.DatabaseFactory
import bros.parraga.db.DatabaseTables
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabase() {
    val autoCreate = System.getenv("DATABASE_AUTO_CREATE")?.toBooleanStrictOrNull() ?: true
    val url = System.getenv("DATABASE_URL")
    val driver = System.getenv("DATABASE_DRIVER")
    val user = System.getenv("DATABASE_USER")
    val password = System.getenv("DATABASE_PASSWORD")

    if (url.isNullOrBlank() || driver.isNullOrBlank() || user.isNullOrBlank()) {
        log.warn("DATABASE_* not set. Using H2 in-memory database for development.")
        DatabaseFactory.init(
            url = "jdbc:h2:mem:dev;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
    } else {
        DatabaseFactory.init(
            url = url,
            driver = driver,
            user = user,
            password = password ?: ""
        )
    }

    if (autoCreate) {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(*DatabaseTables.all.toTypedArray())
        }
    }
}
