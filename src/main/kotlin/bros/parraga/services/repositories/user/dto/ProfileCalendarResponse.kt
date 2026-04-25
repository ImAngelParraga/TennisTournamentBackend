package bros.parraga.services.repositories.user.dto

import bros.parraga.domain.MatchStatus
import bros.parraga.domain.TennisScore
import bros.parraga.domain.UserTrainingEntry
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ProfileCalendarResponse(
    val userId: Int,
    val from: String,
    val to: String,
    val calendarDays: List<ProfileCalendarDay>,
    val events: List<ProfileCalendarEvent>
)

@Serializable
data class ProfileCalendarDay(
    val date: String,
    val totalCount: Int,
    val scheduledMatchCount: Int,
    val liveMatchCount: Int,
    val completedMatchCount: Int,
    val walkoverMatchCount: Int,
    val trainingCount: Int
)

@Serializable
data class ProfileCalendarEvent(
    val eventId: String,
    val eventType: String,
    val date: String,
    val sortTime: Instant? = null,
    val match: UserProfileMatchEntry? = null,
    val training: UserTrainingEntry? = null
)

@Serializable
data class UserProfileMatchEntry(
    val matchId: Int,
    val status: MatchStatus,
    val result: UserMatchResult? = null,
    val scheduledTime: Instant? = null,
    val completedAt: Instant? = null,
    val score: TennisScore? = null,
    val court: String? = null,
    val tournament: UserMatchTournamentSummary,
    val phase: UserMatchPhaseSummary,
    val opponent: UserMatchOpponentSummary? = null
)
