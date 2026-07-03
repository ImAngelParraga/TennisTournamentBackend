package bros.parraga.modules

import bros.parraga.db.seed.SeedData
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.getKoin

/**
 * Optional, development-only data seeding. Runs after [configureDatabase] so the schema exists.
 *
 * Gating:
 *  - Only runs when `SEED_DATA=true`.
 *  - Refuses to run against anything other than the local H2 fallback DB unless `SEED_FORCE=true`,
 *    so it can never accidentally write to a hosted Postgres instance.
 *
 * Seeding failures are logged and swallowed so they never block application startup.
 */
fun Application.configureSeeding() {
    val enabled = System.getenv("SEED_DATA")?.toBooleanStrictOrNull() ?: false
    if (!enabled) return

    val force = System.getenv("SEED_FORCE")?.toBooleanStrictOrNull() ?: false
    if (!usingLocalH2Fallback() && !force) {
        log.warn("SEED_DATA is set but the DB is not the local H2 fallback. Refusing to seed. Set SEED_FORCE=true to override.")
        return
    }

    runCatching {
        runBlocking { SeedData.seed(getKoin()) }
    }.onFailure { log.error("Seeding failed: ${it.message}", it) }
}
