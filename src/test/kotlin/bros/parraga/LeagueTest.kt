package bros.parraga

import bros.parraga.db.schema.LeagueRatingEventDAO
import bros.parraga.db.schema.PlayerDAO
import bros.parraga.db.schema.RatingEventDAO
import bros.parraga.db.schema.UserDAO
import bros.parraga.domain.League
import bros.parraga.domain.LeagueMatch
import bros.parraga.domain.LeagueMember
import bros.parraga.routes.ApiResponse
import bros.parraga.services.rating.EloCalculator
import bros.parraga.services.repositories.league.dto.AddLeagueMemberRequest
import bros.parraga.services.repositories.league.dto.CreateLeagueRequest
import bros.parraga.services.repositories.league.dto.JoinLeagueRequest
import bros.parraga.services.repositories.league.dto.RecordLeagueMatchRequest
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LeagueTest : BaseIntegrationTest() {
    @Test
    fun `league lifecycle applies isolated Elo and replays after result delete`() = testApplicationWithClient { client ->
        val ownerToken = createAuthToken("league-owner-subject", "league-owner@example.com", "Owner")
        val memberToken = createAuthToken("league-member-subject", "league-member@example.com", "Member")
        val secondMemberToken = createAuthToken("league-second-subject", "league-second@example.com", "Second")

        val league = client.post("/leagues") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateLeagueRequest(name = "Friends Ladder", description = "Friday nights"))
        }.body<ApiResponse<League>>().data ?: error("missing league")
        assertEquals(8, league.inviteCode.length)

        val joined = client.post("/leagues/join") {
            header(HttpHeaders.Authorization, "Bearer $memberToken")
            contentType(ContentType.Application.Json)
            setBody(JoinLeagueRequest(league.inviteCode))
        }
        assertEquals(HttpStatusCode.OK, joined.status)

        val users = transaction {
            listOf(
                UserDAO.find { bros.parraga.db.schema.UsersTable.email eq "league-owner@example.com" }.first().id.value,
                UserDAO.find { bros.parraga.db.schema.UsersTable.email eq "league-second@example.com" }.firstOrNull()?.id?.value
            )
        }
        transaction {
            UserDAO.new {
                username = "league-second"
                email = "league-second@example.com"
                authProvider = "clerk"
                authSubject = "league-second-subject"
            }
        }

        val addMember = client.post("/leagues/${league.id}/members") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(AddLeagueMemberRequest(email = "league-second@example.com"))
        }
        assertEquals(HttpStatusCode.Created, addMember.status)

        val members = client.get("/leagues/${league.id}/members") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }.body<ApiResponse<List<LeagueMember>>>().data ?: error("missing members")
        assertEquals(3, members.size)
        val ownerMember = members.first { it.username == "owner" || it.userId == users.first() }
        val joinedMember = members.first { it.username == "member" }
        val secondMember = members.first { it.username == "league-second" }

        val firstMatch = client.post("/leagues/${league.id}/matches") {
            header(HttpHeaders.Authorization, "Bearer $memberToken")
            contentType(ContentType.Application.Json)
            setBody(
                RecordLeagueMatchRequest(
                    player1Id = joinedMember.playerId,
                    player2Id = ownerMember.playerId,
                    winnerId = joinedMember.playerId
                )
            )
        }
        assertEquals(HttpStatusCode.Created, firstMatch.status)

        val secondMatch = client.post("/leagues/${league.id}/matches") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(
                RecordLeagueMatchRequest(
                    player1Id = ownerMember.playerId,
                    player2Id = secondMember.playerId,
                    winnerId = ownerMember.playerId
                )
            )
        }.body<ApiResponse<LeagueMatch>>().data ?: error("missing match")

        val ratedMembers = client.get("/leagues/${league.id}/members") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }.body<ApiResponse<List<LeagueMember>>>().data ?: error("missing rated members")

        val firstDeltas = EloCalculator.matchDeltas(
            EloCalculator.RatingState(1000, 0),
            EloCalculator.RatingState(1000, 0)
        )
        assertEquals(1000 + firstDeltas.winnerDelta, ratedMembers.first { it.playerId == joinedMember.playerId }.rating)
        assertTrue(ratedMembers.first { it.playerId == ownerMember.playerId }.ratedMatches >= 2)

        transaction {
            assertEquals(4, LeagueRatingEventDAO.all().count())
            assertTrue(RatingEventDAO.all().empty(), "league results must not touch public rating_events")
            assertTrue(PlayerDAO.all().all { it.rating == 1000 && it.ratedMatches == 0 })
        }

        val deleteResponse = client.delete("/leagues/${league.id}/matches/${secondMatch.id}") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        transaction {
            assertEquals(2, LeagueRatingEventDAO.all().count())
        }
    }

    @Test
    fun `non member cannot read league and non participant cannot record result`() = testApplicationWithClient { client ->
        val ownerToken = createAuthToken("auth-league-owner", "auth-league-owner@example.com", "Owner")
        val outsiderToken = createAuthToken("auth-league-outsider", "auth-league-outsider@example.com", "Outsider")

        val league = client.post("/leagues") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json)
            setBody(CreateLeagueRequest(name = "Private Ladder"))
        }.body<ApiResponse<League>>().data ?: error("missing league")

        val outsiderRead = client.get("/leagues/${league.id}") {
            header(HttpHeaders.Authorization, "Bearer $outsiderToken")
        }
        assertEquals(HttpStatusCode.NotFound, outsiderRead.status)

        val members = client.get("/leagues/${league.id}/members") {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
        }.body<ApiResponse<List<LeagueMember>>>().data ?: error("missing members")
        val ownerMember = assertNotNull(members.single())

        val outsiderRecord = client.post("/leagues/${league.id}/matches") {
            header(HttpHeaders.Authorization, "Bearer $outsiderToken")
            contentType(ContentType.Application.Json)
            setBody(
                RecordLeagueMatchRequest(
                    player1Id = ownerMember.playerId,
                    player2Id = ownerMember.playerId + 1,
                    winnerId = ownerMember.playerId
                )
            )
        }
        assertEquals(HttpStatusCode.Forbidden, outsiderRecord.status)
    }
}

