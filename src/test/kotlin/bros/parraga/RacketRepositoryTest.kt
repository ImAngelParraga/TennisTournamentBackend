package bros.parraga

import bros.parraga.db.schema.RacketStringingAuditsTable
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.RacketDetails
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.racket.dto.CreateRacketStringingRequest
import bros.parraga.services.repositories.racket.dto.NewRacketRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketRequest
import bros.parraga.services.repositories.racket.dto.UpdateRacketStringingRequest
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RacketRepositoryTest : BaseIntegrationTest() {

    @Test
    fun `should implicitly create racket and expose latest stringing publicly`() = testApplicationWithClient { client ->
        val token = createAuthToken("stringer-subject", "stringer@email.com", "Stringer One")

        val createResponse = client.post("/rackets/stringings") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateRacketStringingRequest(
                    newRacket = NewRacketRequest(
                        displayName = "Blade 98",
                        brand = "Wilson",
                        model = "V9",
                        stringPattern = "16x19",
                        ownerName = "Carlos"
                    ),
                    stringingDate = "2026-04-10",
                    mainsKg = 24.0,
                    crossesKg = 23.5,
                    mainStringBrand = "Luxilon",
                    mainStringModel = "Alu Power",
                    mainStringGauge = "1.25",
                    crossStringBrand = "Wilson",
                    crossStringModel = "Sensation",
                    crossStringGauge = "1.30",
                    notes = "Fresh hybrid setup"
                )
            )
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = createResponse.body<ApiResponse<RacketDetails>>().data ?: error("missing racket details")
        assertEquals("Blade 98", created.displayName)
        assertEquals("Wilson", created.brand)
        assertEquals("Carlos", created.ownerName)
        assertEquals(1, created.history.size)
        assertEquals("2026-04-10", created.latestStringing?.stringingDate)
        assertEquals(52.91, created.latestStringing?.mainsLb)
        assertEquals(51.81, created.latestStringing?.crossesLb)
        assertEquals("Stringer_One", created.latestStringing?.stringerUsername)

        val publicResponse = client.get("/public/rackets/${created.publicToken}")
        assertEquals(HttpStatusCode.OK, publicResponse.status)
        val publicBody = publicResponse.body<ApiResponse<RacketDetails>>().data ?: error("missing public racket details")
        assertEquals(created.publicToken, publicBody.publicToken)
        assertEquals("Blade 98", publicBody.displayName)
        assertEquals(1, publicBody.history.size)
    }

    @Test
    fun `should forbid non creator from modifying ownerless racket`() = testApplicationWithClient { client ->
        val creatorToken = createAuthToken("creator-subject", "creator@email.com", "Creator")
        val otherToken = createAuthToken("other-subject", "other@email.com", "Other")

        val created = client.post("/rackets/stringings") {
            header(HttpHeaders.Authorization, "Bearer $creatorToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateRacketStringingRequest(
                    newRacket = NewRacketRequest(displayName = "Pure Aero"),
                    stringingDate = "2026-04-01",
                    mainsKg = 22.5,
                    crossesKg = 22.5
                )
            )
        }.body<ApiResponse<RacketDetails>>().data ?: error("missing created racket")

        val addStringingResponse = client.post("/rackets/stringings") {
            header(HttpHeaders.Authorization, "Bearer $otherToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateRacketStringingRequest(
                    racketPublicToken = created.publicToken,
                    stringingDate = "2026-04-15",
                    mainsKg = 23.0,
                    crossesKg = 23.0
                )
            )
        }

        assertEquals(HttpStatusCode.Forbidden, addStringingResponse.status)
    }

    @Test
    fun `owner user should be able to update racket metadata and stringing history`() = testApplicationWithClient { client ->
        val ownerUserId = createLocalUser("owner_user", "owner-subject")
        val stringerToken = createAuthToken("stringer-subject", "stringer@email.com", "Stringer")
        val ownerToken = createAuthToken("owner-subject", "owner@email.com", "Owner User")

        val created = client.post("/rackets/stringings") {
            header(HttpHeaders.Authorization, "Bearer $stringerToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateRacketStringingRequest(
                    newRacket = NewRacketRequest(
                        displayName = "Ezone 98",
                        ownerUserId = ownerUserId,
                        ownerName = "Owner User"
                    ),
                    stringingDate = "2026-04-05",
                    mainsKg = 23.0,
                    crossesKg = 22.0,
                    notes = "Initial setup"
                )
            )
        }.body<ApiResponse<RacketDetails>>().data ?: error("missing created racket")

        val updatedRacketResponse = client.put("/rackets/${created.publicToken}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateRacketRequest(brand = "Yonex", model = "2025", stringPattern = "16x19"))
        }
        assertEquals(HttpStatusCode.OK, updatedRacketResponse.status)
        val updatedRacket = updatedRacketResponse.body<ApiResponse<RacketDetails>>().data ?: error("missing updated racket")
        assertEquals("Yonex", updatedRacket.brand)
        assertEquals("2025", updatedRacket.model)

        val stringingId = created.history.single().id
        val updatedStringingResponse = client.put("/rackets/stringings/$stringingId") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateRacketStringingRequest(notes = "Owner adjusted notes", mainsKg = 24.0))
        }
        assertEquals(HttpStatusCode.OK, updatedStringingResponse.status)
        val updatedStringing = updatedStringingResponse.body<ApiResponse<RacketDetails>>().data ?: error("missing racket after stringing update")
        assertEquals("Owner adjusted notes", updatedStringing.latestStringing?.notes)
        assertEquals(24.0, updatedStringing.latestStringing?.mainsKg)
    }

    @Test
    fun `should hide logically deleted stringings from public history and keep audit trail`() = testApplicationWithClient { client ->
        val token = createAuthToken("stringer-subject", "stringer@email.com", "Stringer")

        val created = client.post("/rackets/stringings") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateRacketStringingRequest(
                    newRacket = NewRacketRequest(displayName = "Gravity Pro"),
                    stringingDate = "2026-04-01",
                    mainsKg = 22.0,
                    crossesKg = 21.5,
                    notes = "Round one"
                )
            )
        }.body<ApiResponse<RacketDetails>>().data ?: error("missing created racket")

        val second = client.post("/rackets/stringings") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateRacketStringingRequest(
                    racketPublicToken = created.publicToken,
                    stringingDate = "2026-04-08",
                    mainsKg = 23.0,
                    crossesKg = 22.5,
                    notes = "Round two"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, second.status)

        val latestBeforeDelete = client.get("/public/rackets/${created.publicToken}")
            .body<ApiResponse<RacketDetails>>()
            .data ?: error("missing public details before delete")
        assertEquals("2026-04-08", latestBeforeDelete.latestStringing?.stringingDate)
        assertEquals(2, latestBeforeDelete.history.size)

        val latestStringingId = latestBeforeDelete.latestStringing?.id ?: error("missing latest stringing id")
        val deleteResponse = client.delete("/rackets/stringings/$latestStringingId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val publicAfterDelete = client.get("/public/rackets/${created.publicToken}")
            .body<ApiResponse<RacketDetails>>()
            .data ?: error("missing public details after delete")
        assertEquals("2026-04-01", publicAfterDelete.latestStringing?.stringingDate)
        assertEquals(1, publicAfterDelete.history.size)
        assertEquals("Round one", publicAfterDelete.history.single().notes)

        val auditActions = transaction {
            RacketStringingAuditsTable
                .selectAll()
                .map { it[RacketStringingAuditsTable.action] }
                .sorted()
        }
        assertEquals(listOf("CREATED", "CREATED", "DELETED"), auditActions)
    }

    private fun createLocalUser(username: String, authSubject: String): Int = transaction {
        UserDAO.new {
            this.username = username
            email = "$username@example.com"
            authProvider = "clerk"
            this.authSubject = authSubject
            updatedAt = Instant.now()
        }.id.value
    }
}
