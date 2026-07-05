package bros.parraga.modules

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                serializersModule = SerializersModule {
                    contextual(Instant::class, InstantIso8601Serializer)
                }
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                // Emit fields that hold their default value (e.g. KnockoutConfig
                // qualifiers=1, seedingStrategy=INPUT_ORDER). Without this they are
                // dropped from JSON and the client reads them as undefined.
                encodeDefaults = true
            }
        )
    }
}
