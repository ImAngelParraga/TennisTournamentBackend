package bros.parraga

import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.UserTrainingEntry
import bros.parraga.domain.UserTrainingMonthResponse
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
    fun `owner should create trainings and retrieve a monthly calendar summary`() = testApplicationWithClient { client ->
        createLocalUser("training_owner", "training-owner-subject")
        val ownerToken = createAuthToken("training-owner-subject", "owner@email.com", "Training Owner")

        val aprilThirdResponse = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "2026-04-03", notes = "Serve basket and volleys"))
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
            setBody(CreateTrainingRequest(trainingDate = "2026-04-12", notes = "Crosscourt patterns"))
        }
        assertEquals(HttpStatusCode.Created, secondAprilResponse.status)

        val mayResponse = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "2026-05-01", notes = "Next month"))
        }
        assertEquals(HttpStatusCode.Created, mayResponse.status)

        val monthResponse = client.get("/users/me/trainings?month=2026-04") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.OK, monthResponse.status)

        val monthData = monthResponse.body<ApiResponse<UserTrainingMonthResponse>>().data ?: error("missing month data")
        assertEquals("2026-04", monthData.month)
        assertEquals(listOf("2026-04-12", "2026-04-12", "2026-04-03"), monthData.trainings.map { it.trainingDate })
        assertEquals(listOf(1, 2), monthData.calendarDays.map { it.trainingCount })
        assertEquals(listOf("2026-04-03", "2026-04-12"), monthData.calendarDays.map { it.date })
        assertEquals(listOf("Crosscourt patterns", null, "Serve basket and volleys"), monthData.trainings.map { it.notes })
    }

    @Test
    fun `owner should update and delete own trainings`() = testApplicationWithClient { client ->
        createLocalUser("manage_owner", "manage-owner-subject")
        val ownerToken = createAuthToken("manage-owner-subject", "owner@email.com", "Manage Owner")

        val createdTraining = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "2026-04-12", notes = "Initial session"))
        }.body<ApiResponse<UserTrainingEntry>>().data ?: error("missing training")

        val updatedResponse = client.put("/users/me/trainings/${createdTraining.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateTrainingRequest(trainingDate = "2026-04-20", notes = "   "))
        }
        assertEquals(HttpStatusCode.OK, updatedResponse.status)
        val updatedTraining = updatedResponse.body<ApiResponse<UserTrainingEntry>>().data ?: error("missing updated training")
        assertEquals("2026-04-20", updatedTraining.trainingDate)
        assertEquals(null, updatedTraining.notes)
        assertNotNull(updatedTraining.updatedAt)

        val monthResponse = client.get("/users/me/trainings?month=2026-04") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        val monthData = monthResponse.body<ApiResponse<UserTrainingMonthResponse>>().data ?: error("missing month data")
        assertEquals(listOf("2026-04-20"), monthData.trainings.map { it.trainingDate })
        assertEquals(listOf(null), monthData.trainings.map { it.notes })
        assertEquals(listOf("2026-04-20"), monthData.calendarDays.map { it.date })

        val deleteResponse = client.delete("/users/me/trainings/${createdTraining.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val monthAfterDelete = client.get("/users/me/trainings?month=2026-04") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }.body<ApiResponse<UserTrainingMonthResponse>>().data ?: error("missing month data after delete")
        assertEquals(emptyList(), monthAfterDelete.trainings)
        assertEquals(emptyList(), monthAfterDelete.calendarDays)
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
            setBody(CreateTrainingRequest(trainingDate = "2026-04-12", notes = "Owned entry"))
        }.body<ApiResponse<UserTrainingEntry>>().data ?: error("missing owned training")

        val unauthorizedResponse = client.get("/users/me/trainings?month=2026-04")
        assertEquals(HttpStatusCode.Unauthorized, unauthorizedResponse.status)

        val invalidMonthResponse = client.get("/users/me/trainings?month=2026/04") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidMonthResponse.status)

        val invalidDateResponse = client.post("/users/me/trainings") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateTrainingRequest(trainingDate = "04-12-2026", notes = "Invalid date"))
        }
        assertEquals(HttpStatusCode.BadRequest, invalidDateResponse.status)

        val invalidUpdateDateResponse = client.put("/users/me/trainings/${createdTraining.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateTrainingRequest(trainingDate = "2026/04/20"))
        }
        assertEquals(HttpStatusCode.BadRequest, invalidUpdateDateResponse.status)

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
