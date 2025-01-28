package bros.parraga


import bros.parraga.db.schema.UsersTable
import bros.parraga.domain.User
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.user.dto.CreateUserRequest
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserTest : BaseIntegrationTest() {
    override val tables = listOf(UsersTable)

    @Test
    fun create() = testApplication {
        application { testModule() }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                })
            }
        }

        val request = CreateUserRequest("user", "password", "email@email.com")
        val response = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue { response.body<ApiResponse<User>>().data?.username == request.username }
        assertTrue { response.body<ApiResponse<User>>().data?.email == request.email }
    }
}