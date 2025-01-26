package bros.parraga.db

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object DatabaseFactory {
    val db by lazy {
        Database.connect(
            System.getenv("DATABASE_URL"),
            driver = System.getenv("DATABASE_DRIVER"),
            user = System.getenv("DATABASE_USER"),
            password = System.getenv("DATABASE_PASSWORD")
        )
    }

    suspend fun <T> dbQuery(block: Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db, statement = block)
}