package bros.parraga

import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.UserTrainingEntry
import bros.parraga.domain.UserTrainingRangeResponse
import bros.parraga.routes.ApiResponse
import bros.parraga.services.repositories.training.dto.CreateTrainingRequest
import bros.parraga.services.repositories.training.dto.UpdateTrainingRequest
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
import kotlin.test.assertNotNull

class TrainingRepositoryTest : BaseIntegrationTest() {

    @Test
    fun `owner should create trainings and retrieve a date range calendar summary`() = testApplicationWithClient { client ->
        createLocalUser("training_owner", "training-owner-subject")
        val ownerToken = createAuthToken("training-owner-subject", "owner@email.com", "Training Owner")

        val aprilThirdResponse = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "2026-04-03", durationMinutes = 60, notes = "Serve basket and volleys"))
        }
        assertEquals(HttpStatusCode.Created, aprilThirdResponse.status)

        val blankNotesResponse = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "2026-04-12", notes = "   "))
        }
        assertEquals(HttpStatusCode.Created, blankNotesResponse.status)
        val blankNotesTraining = blankNotesResponse.body<ApiResponse<UserTrainingEntry>>().data ?: error("missing training")
        assertEquals(null, blankNotesTraining.notes)

        val secondAprilResponse = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "2026-04-12", durationMinutes = 90, notes = "Crosscourt patterns"))
        }
        assertEquals(HttpStatusCode.Created, secondAprilResponse.status)

        val mayResponse = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "2026-05-01", notes = "Next month"))
        }
        assertEquals(HttpStatusCode.Created, mayResponse.status)

        val rangeResponse = client.get("/users/me/trainings?from=2026-04-01&to=2026-04-30") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, rangeResponse.status)

        val rangeData = rangeResponse.body<ApiResponse<UserTrainingRangeResponse>>().data ?: error("missing range data")
        assertEquals("2026-04-01", rangeData.from)
        assertEquals("2026-04-30", rangeData.to)
        assertEquals(listOf("2026-04-12", "2026-04-12", "2026-04-03"), rangeData.trainings.map { it.trainingDate })
        assertEquals(listOf(90, null, 60), rangeData.trainings.map { it.durationMinutes })
        assertEquals(listOf(1, 2), rangeData.calendarDays.map { it.trainingCount })
        assertEquals(listOf("2026-04-03", "2026-04-12"), rangeData.calendarDays.map { it.date })
        assertEquals(listOf("Crosscourt patterns", null, "Serve basket and volleys"), rangeData.trainings.map { it.notes })

        val crossMonthResponse = client.get("/users/me/trainings?from=2026-04-12&to=2026-05-01") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, crossMonthResponse.status)
        val crossMonthData = crossMonthResponse.body<ApiResponse<UserTrainingRangeResponse>>().data ?: error("missing cross-month data")
        assertEquals(listOf("2026-05-01", "2026-04-12", "2026-04-12"), crossMonthData.trainings.map { it.trainingDate })

        val singleDayResponse = client.get("/users/me/trainings?from=2026-04-12&to=2026-04-12") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, singleDayResponse.status)
        val singleDayData = singleDayResponse.body<ApiResponse<UserTrainingRangeResponse>>().data ?: error("missing single-day data")
        assertEquals(listOf("2026-04-12", "2026-04-12"), singleDayData.trainings.map { it.trainingDate })
    }

    @Test
    fun `owner should update and delete own trainings`() = testApplicationWithClient { client ->
        createLocalUser("manage_owner", "manage-owner-subject")
        val ownerToken = createAuthToken("manage-owner-subject", "owner@email.com", "Manage Owner")

        val createdTraining = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "2026-04-12", durationMinutes = 45, notes = "Initial session"))
        }.body<ApiResponse<UserTrainingEntry>>().data ?: error("missing training")

        val updatedResponse = client.put("/users/me/trainings/${createdTraining.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateTrainingRequest(trainingDate = "2026-04-20", durationMinutes = 75, notes = "   "))
        }
        assertEquals(HttpStatusCode.OK, updatedResponse.status)
        val updatedTraining = updatedResponse.body<ApiResponse<UserTrainingEntry>>().data ?: error("missing updated training")
        assertEquals("2026-04-20", updatedTraining.trainingDate)
        assertEquals(75, updatedTraining.durationMinutes)
        assertEquals(null, updatedTraining.notes)
        assertNotNull(updatedTraining.updatedAt)

        val rangeResponse = client.get("/users/me/trainings?from=2026-04-01&to=2026-04-30") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        val rangeData = rangeResponse.body<ApiResponse<UserTrainingRangeResponse>>().data ?: error("missing range data")
        assertEquals(listOf("2026-04-20"), rangeData.trainings.map { it.trainingDate })
        assertEquals(listOf(75), rangeData.trainings.map { it.durationMinutes })
        assertEquals(listOf(null), rangeData.trainings.map { it.notes })
        assertEquals(listOf("2026-04-20"), rangeData.calendarDays.map { it.date })

        val deleteResponse = client.delete("/users/me/trainings/${createdTraining.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val rangeAfterDelete = client.get("/users/me/trainings?from=2026-04-01&to=2026-04-30") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }.body<ApiResponse<UserTrainingRangeResponse>>().data ?: error("missing range data after delete")
        assertEquals(emptyList(), rangeAfterDelete.trainings)
        assertEquals(emptyList(), rangeAfterDelete.calendarDays)
    }

    @Test
    fun `trainings endpoints should require auth validate payloads and enforce owner boundary`() = testApplicationWithClient { client ->
        createLocalUser("validation_owner", "validation-owner-subject")
        createLocalUser("other_owner", "other-owner-subject")
        val ownerToken = createAuthToken("validation-owner-subject", "owner@email.com", "Validation Owner")
        val otherToken = createAuthToken("other-owner-subject", "other@email.com", "Other Owner")

        val createdTraining = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "2026-04-12", durationMinutes = 60, notes = "Owned entry"))
        }.body<ApiResponse<UserTrainingEntry>>().data ?: error("missing owned training")

        val unauthorizedResponse = client.get("/users/me/trainings?from=2026-04-01&to=2026-04-30")
        assertEquals(HttpStatusCode.Unauthorized, unauthorizedResponse.status)

        val invalidFromResponse = client.get("/users/me/trainings?from=2026/04/01&to=2026-04-30") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidFromResponse.status)

        val reversedRangeResponse = client.get("/users/me/trainings?from=2026-04-30&to=2026-04-01") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.BadRequest, reversedRangeResponse.status)

        val missingToResponse = client.get("/users/me/trainings?from=2026-04-01") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.BadRequest, missingToResponse.status)

        val invalidDateResponse = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "04-12-2026", notes = "Invalid date"))
        }
        assertEquals(HttpStatusCode.BadRequest, invalidDateResponse.status)

        val invalidDurationResponse = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "2026-04-12", durationMinutes = 0, notes = "Invalid duration"))
        }
        assertEquals(HttpStatusCode.BadRequest, invalidDurationResponse.status)

        val invalidUpdateDateResponse = client.put("/users/me/trainings/${createdTraining.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateTrainingRequest(trainingDate = "2026/04/20"))
        }
        assertEquals(HttpStatusCode.BadRequest, invalidUpdateDateResponse.status)

        val invalidUpdateDurationResponse = client.put("/users/me/trainings/${createdTraining.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateTrainingRequest(durationMinutes = -15))
        }
        assertEquals(HttpStatusCode.BadRequest, invalidUpdateDurationResponse.status)

        val emptyUpdateResponse = client.put("/users/me/trainings/${createdTraining.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateTrainingRequest())
        }
        assertEquals(HttpStatusCode.BadRequest, emptyUpdateResponse.status)

        val forbiddenUpdateResponse = client.put("/users/me/trainings/${createdTraining.id}") {
            header(HttpHeaders.Authorization, "Bearer $otherToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateTrainingRequest(notes = "Not yours"))
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenUpdateResponse.status)

        val forbiddenDeleteResponse = client.delete("/users/me/trainings/${createdTraining.id}") {
            header(HttpHeaders.Authorization, "Bearer $otherToken")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenDeleteResponse.status)
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
