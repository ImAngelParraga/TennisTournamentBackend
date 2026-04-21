package bros.parraga

import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.RacketDetails
import bros.parraga.domain.RacketSummary
import bros.parraga.domain.RacketVisibility
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.racket.dto.CreateRacketRequest
import bros.parraga.services.repositories.racket.dto.CreateRacketStringingRequest
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
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RacketRepositoryTest : BaseIntegrationTest() {

    @Test
    fun `owner list should include private rackets while public list shows only public ones`() = testApplicationWithClient { client ->
        val ownerUserId = createLocalUser("racket_owner", "owner-subject")
        val ownerToken = createAuthToken("owner-subject", "owner@email.com", "Owner User")

        val privateRacket = client.post("/users/me/rackets") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateRacketRequest(
                    displayName = "Practice Frame",
                    brand = "Yonex",
                    visibility = RacketVisibility.PRIVATE
                )
            )
        }
        assertEquals(HttpStatusCode.Created, privateRacket.status)

        val publicRacket = client.post("/users/me/rackets") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateRacketRequest(
                    displayName = "Match Frame",
                    brand = "Wilson",
                    visibility = RacketVisibility.PUBLIC
                )
            )
        }
        assertEquals(HttpStatusCode.Created, publicRacket.status)

        val ownListResponse = client.get("/users/me/rackets") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, ownListResponse.status)
        val ownRackets = ownListResponse.body<ApiResponse<List<RacketSummary>>>().data ?: error("missing own rackets")
        assertEquals(2, ownRackets.size)
        assertTrue(ownRackets.any { it.displayName == "Practice Frame" && it.visibility == RacketVisibility.PRIVATE })
        assertTrue(ownRackets.any { it.displayName == "Match Frame" && it.visibility == RacketVisibility.PUBLIC })

        val publicListResponse = client.get("/users/$ownerUserId/rackets")
        assertEquals(HttpStatusCode.OK, publicListResponse.status)
        val publicRackets = publicListResponse.body<ApiResponse<List<RacketSummary>>>().data ?: error("missing public rackets")
        assertEquals(1, publicRackets.size)
        assertEquals("Match Frame", publicRackets.single().displayName)
    }

    @Test
    fun `owner should manage stringing history ordering and visibility`() = testApplicationWithClient { client ->
        val ownerUserId = createLocalUser("history_owner", "history-subject")
        val ownerToken = createAuthToken("history-subject", "history@email.com", "History Owner")

        val createdRacket = client.post("/users/me/rackets") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateRacketRequest(
                    displayName = "Blade 98",
                    brand = "Wilson",
                    model = "V9",
                    stringPattern = "16x19",
                    visibility = RacketVisibility.PRIVATE
                )
            )
        }.body<ApiResponse<RacketDetails>>().data ?: error("missing created racket")

        val firstStringing = client.post("/users/me/rackets/${createdRacket.id}/stringings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateRacketStringingRequest(
                    stringingDate = "2026-04-01",
                    mainsTensionKg = 24.0,
                    crossesTensionKg = 23.0,
                    mainStringType = "Alu Power 1.25",
                    crossStringType = "Sensation 1.30",
                    performanceNotes = "Stable on serve"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, firstStringing.status)

        val secondStringingResponse = client.post("/users/me/rackets/${createdRacket.id}/stringings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                CreateRacketStringingRequest(
                    stringingDate = "2026-04-10",
                    mainsTensionKg = 23.5,
                    crossesTensionKg = 22.5,
                    mainStringType = "RPM Blast 1.25",
                    crossStringType = "X-One 1.30",
                    performanceNotes = "More pocketing"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, secondStringingResponse.status)

        val secondStringing = secondStringingResponse.body<ApiResponse<RacketDetails>>().data ?: error("missing updated racket")
        assertEquals("2026-04-10", secondStringing.latestStringing?.stringingDate)
        assertEquals(listOf("2026-04-10", "2026-04-01"), secondStringing.history.map { it.stringingDate })

        val latestStringingId = secondStringing.latestStringing?.id ?: error("missing latest stringing id")
        val updatedRacketResponse = client.put("/users/me/rackets/${createdRacket.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateRacketRequest(visibility = RacketVisibility.PUBLIC, model = "V9 2026"))
        }
        assertEquals(HttpStatusCode.OK, updatedRacketResponse.status)

        val updatedStringingResponse = client.put("/users/me/rackets/${createdRacket.id}/stringings/$latestStringingId") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateRacketStringingRequest(performanceNotes = "More pocketing and spin", mainsTensionKg = 24.5))
        }
        assertEquals(HttpStatusCode.OK, updatedStringingResponse.status)
        val updatedRacket = updatedStringingResponse.body<ApiResponse<RacketDetails>>().data ?: error("missing racket detail")
        assertEquals(RacketVisibility.PUBLIC, updatedRacket.visibility)
        assertEquals(24.5, updatedRacket.latestStringing?.mainsTensionKg)
        assertEquals("More pocketing and spin", updatedRacket.latestStringing?.performanceNotes)

        val publicDetailResponse = client.get("/users/$ownerUserId/rackets/${createdRacket.id}")
        assertEquals(HttpStatusCode.OK, publicDetailResponse.status)
        val publicRacket = publicDetailResponse.body<ApiResponse<RacketDetails>>().data ?: error("missing public racket")
        assertEquals(RacketVisibility.PUBLIC, publicRacket.visibility)
        assertEquals(2, publicRacket.history.size)
    }

    @Test
    fun `soft delete should hide deleted stringings and deleted rackets from normal reads`() = testApplicationWithClient { client ->
        val ownerUserId = createLocalUser("delete_owner", "delete-subject")
        val ownerToken = createAuthToken("delete-subject", "delete@email.com", "Delete Owner")

        val racket = client.post("/users/me/rackets") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateRacketRequest(displayName = "Gravity Pro", visibility = RacketVisibility.PUBLIC))
        }.body<ApiResponse<RacketDetails>>().data ?: error("missing racket")

        client.post("/users/me/rackets/${racket.id}/stringings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateRacketStringingRequest("2026-04-03", 23.0, 22.5, performanceNotes = "Round one"))
        }

        val racketWithSecondStringing = client.post("/users/me/rackets/${racket.id}/stringings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateRacketStringingRequest("2026-04-12", 24.0, 23.5, performanceNotes = "Round two"))
        }.body<ApiResponse<RacketDetails>>().data ?: error("missing updated racket")

        val latestStringingId = racketWithSecondStringing.latestStringing?.id ?: error("missing latest stringing id")
        val deleteStringingResponse = client.delete("/users/me/rackets/${racket.id}/stringings/$latestStringingId") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.NoContent, deleteStringingResponse.status)

        val ownRacketAfterStringingDelete = client.get("/users/me/rackets/${racket.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }.body<ApiResponse<RacketDetails>>().data ?: error("missing own racket after stringing delete")
        assertEquals(1, ownRacketAfterStringingDelete.history.size)
        assertEquals("Round one", ownRacketAfterStringingDelete.latestStringing?.performanceNotes)

        val deleteRacketResponse = client.delete("/users/me/rackets/${racket.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.NoContent, deleteRacketResponse.status)

        val ownListResponse = client.get("/users/me/rackets") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        val ownRackets = ownListResponse.body<ApiResponse<List<RacketSummary>>>().data ?: error("missing own rackets after delete")
        assertTrue(ownRackets.isEmpty())

        val publicDetailResponse = client.get("/users/$ownerUserId/rackets/${racket.id}")
        assertEquals(HttpStatusCode.NotFound, publicDetailResponse.status)
    }

    @Test
    fun `should forbid non owner writes and reject invalid tension values`() = testApplicationWithClient { client ->
        val ownerToken = createAuthToken("owner-subject", "owner@email.com", "Owner")
        val otherToken = createAuthToken("other-subject", "other@email.com", "Other")

        val racket = client.post("/users/me/rackets") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateRacketRequest(displayName = "Pure Aero", visibility = RacketVisibility.PRIVATE))
        }.body<ApiResponse<RacketDetails>>().data ?: error("missing racket")

        val forbiddenUpdateResponse = client.put("/users/me/rackets/${racket.id}") {
            header(HttpHeaders.Authorization, "Bearer $otherToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateRacketRequest(displayName = "Not yours"))
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenUpdateResponse.status)

        val forbiddenStringingResponse = client.post("/users/me/rackets/${racket.id}/stringings") {
            header(HttpHeaders.Authorization, "Bearer $otherToken")
            contentType(ContentType.Application.Json)
            setBody(CreateRacketStringingRequest("2026-04-10", 23.0, 22.5))
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenStringingResponse.status)

        val invalidStringingResponse = client.post("/users/me/rackets/${racket.id}/stringings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateRacketStringingRequest("2026-04-10", 0.0, 22.5))
        }
        assertEquals(HttpStatusCode.BadRequest, invalidStringingResponse.status)
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
