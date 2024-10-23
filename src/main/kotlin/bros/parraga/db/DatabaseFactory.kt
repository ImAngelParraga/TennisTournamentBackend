package bros.parraga.db

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("unused")
object DatabaseFactory {
    val db = createSupabaseClient(
        supabaseUrl = System.getenv("SUPABASE_URL"),
        supabaseKey = System.getenv("SUPABASE_KEY")
    ) {
        install(Postgrest)
    }

    suspend fun <T> dbQuery(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}