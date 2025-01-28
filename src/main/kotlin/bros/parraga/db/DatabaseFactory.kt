package bros.parraga.db

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object DatabaseFactory {
    private var db: Database? = null

    fun init(
        url: String = System.getenv("DATABASE_URL"),
        driver: String = System.getenv("DATABASE_DRIVER"),
        user: String = System.getenv("DATABASE_USER"),
        password: String = System.getenv("DATABASE_PASSWORD")
    ) {
        db = Database.connect(
            url = url,
            driver = driver,
            user = user,
            password = password
        )
    }

    suspend fun <T> dbQuery(block: Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, requireNotNull(db) { "Database not initialized" }, statement = block)
}
