package bros.parraga.db

import org.jetbrains.exposed.sql.Database

@Suppress("unused")
object DatabaseFactory {
    val db by lazy {
        Database.connect(
            System.getenv("DATABASE_URL"),
            driver = System.getenv("DATABASE_DRIVER"),
            user = System.getenv("DATABASE_USER"),
            password = System.getenv("DATABASE_PASSWORD")
        )
    }
}