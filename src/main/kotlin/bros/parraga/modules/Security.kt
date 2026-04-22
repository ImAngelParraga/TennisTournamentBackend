package bros.parraga.modules

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.configureSecurity(isTest: Boolean = false) {
    val authConfig = loadAuthConfig(isTest)

    install(Authentication) {
        jwt("clerk-jwt") {
            realm = "tournament-backend"

            if (isTest) {
                verifier(
                    JWT.require(Algorithm.HMAC256(authConfig.testJwtSecret))
                        .withIssuer(authConfig.issuer)
                        .withAudience(authConfig.audience ?: error("Missing test audience"))
                        .build()
                )
            } else {
                val jwkProvider = JwkProviderBuilder(URI.create("${authConfig.issuer}/.well-known/jwks.json").toURL())
                    .cached(10, 24, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build()

                verifier(jwkProvider, authConfig.issuer) {
                    authConfig.audience?.let { withAudience(it) }
                }
            }

            validate { credential ->
                val subject = credential.payload.subject
                if (subject.isNullOrBlank()) null else JWTPrincipal(credential.payload)
            }

            challenge { _, _ ->
                call.response.headers.append(HttpHeaders.WWWAuthenticate, "Bearer")
                call.respondText("Unauthorized", status = io.ktor.http.HttpStatusCode.Unauthorized)
            }
        }
    }
}
