package bros.parraga.modules

data class AuthConfig(
    val issuer: String,
    val audience: String?,
    val allowedOrigins: List<String>,
    val testJwtSecret: String
)

fun loadAuthConfig(isTest: Boolean): AuthConfig {
    val issuer = System.getenv("CLERK_ISSUER") ?: if (isTest) "http://localhost/test-issuer" else ""
    val audience = System.getenv("CLERK_AUDIENCE")
        ?.takeIf { it.isNotBlank() }
        ?: if (isTest) "test-audience" else null
    val allowedOriginsRaw = System.getenv("ALLOWED_ORIGINS")
    val allowedOrigins = allowedOriginsRaw
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: if (isTest) {
            listOf("http://localhost:3000", "http://127.0.0.1:3000", "http://localhost:5173", "http://127.0.0.1:5173")
        } else {
            emptyList()
        }
    val testJwtSecret = System.getenv("AUTH_TEST_JWT_SECRET") ?: "test-secret"

    if (!isTest) {
        require(issuer.isNotBlank()) { "Missing CLERK_ISSUER environment variable" }
        require(allowedOrigins.isNotEmpty()) { "Missing ALLOWED_ORIGINS environment variable" }
    }

    return AuthConfig(
        issuer = issuer,
        audience = audience,
        allowedOrigins = allowedOrigins,
        testJwtSecret = testJwtSecret
    )
}
