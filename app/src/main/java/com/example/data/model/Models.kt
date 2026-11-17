package com.example.data.model

import androidx.annotation.Keep
import com.example.localization.AppLanguage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class StaffRole(val displayName: String) {
    SUPER_ADMIN("Super Admin"),
    ADMIN("Admin"),
    SPORTS_MANAGER("Sports Manager"),
    SCORE_MANAGER("Score Manager"),
    STREAMING_MANAGER("Streaming Manager"),
    NEWS_EDITOR("News Editor"),
    MODERATOR("Moderator"),
    VIEWER("Viewer");

    val isStaff: Boolean
        get() = this != VIEWER

    fun canManageBroadcasts(): Boolean = this in setOf(SUPER_ADMIN, ADMIN, STREAMING_MANAGER)
    fun canManageScores(): Boolean = this in setOf(SUPER_ADMIN, ADMIN, SPORTS_MANAGER, SCORE_MANAGER)
    fun canManageSports(): Boolean = this in setOf(SUPER_ADMIN, ADMIN, SPORTS_MANAGER)
    fun canManageNews(): Boolean = this in setOf(SUPER_ADMIN, ADMIN, NEWS_EDITOR)
    fun canModerateChat(): Boolean = this in setOf(SUPER_ADMIN, ADMIN, MODERATOR)
    fun canManageStaff(): Boolean = this == SUPER_ADMIN
    fun canManageBanners(): Boolean = this in setOf(SUPER_ADMIN, ADMIN)

    companion object {
        fun fromRaw(raw: String?): StaffRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: VIEWER
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val preferredLanguage: String = "en",
    val favoriteTeamIds: List<String> = emptyList(),
    val favoriteWrestlerIds: List<String> = emptyList(),
    val favoriteTournamentIds: List<String> = emptyList(),
    val notifyMatchStart: Boolean = true,
    val notifyGoals: Boolean = true,
    val notifyKokowa: Boolean = true,
    val notifyTournaments: Boolean = true,
    val notifyNews: Boolean = false,
    val chatBlocked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StaffMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = StaffRole.MODERATOR.name,
    val status: String = "ACTIVE",
    val permissions: List<String> = emptyList(),
    val assignedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val actorRole: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SportCategory(
    @DocumentId val id: String = "",
    val slug: String = "",
    val nameEn: String = "",
    val nameHa: String = "",
    val nameFr: String = "",
    val nameAr: String = "",
    val iconKey: String = "SPORTS",
    val traditional: Boolean = false,
    val active: Boolean = true,
    val sortOrder: Long = 0L,
    val visibility: String = "PUBLIC",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun localizedName(language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> nameEn.ifBlank { slug }
        AppLanguage.HAUSA -> nameHa.ifBlank { nameEn }
        AppLanguage.FRENCH -> nameFr.ifBlank { nameEn }
        AppLanguage.ARABIC -> nameAr.ifBlank { nameEn }
    }
}

@Keep
@IgnoreExtraProperties
data class Broadcast(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val sportCategory: String = "FOOTBALL",
    val streamType: String = "HLS",
    val playbackUrl: String = "",
    val youtubeVideoId: String? = null,
    val ingestProtocol: String = "OBS_RTMP",
    val ingestEndpoint: String = "",
    val streamKeyConfigured: Boolean = true,
    val status: String = "LIVE",
    val viewerCount: Long = 0L,
    val chatEnabled: Boolean = true,
    val featured: Boolean = false,
    val matchId: String? = null,
    val scheduledLabel: String = "",
    val visibility: String = "PUBLIC",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BroadcastSecret(
    @DocumentId val id: String = "",
    val broadcastId: String = "",
    val rtmpServerUrl: String = "",
    val streamKeyMasked: String = "",
    val obsProfileNote: String = "",
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatComment(
    @DocumentId val id: String = "",
    val broadcastId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val message: String = "",
    val deleted: Boolean = false,
    val reported: Boolean = false,
    val reportCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class League(
    @DocumentId val id: String = "",
    val name: String = "",
    val country: String = "Niger",
    val season: String = "2026/27",
    val sportCategory: String = "FOOTBALL",
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Team(
    @DocumentId val id: String = "",
    val name: String = "",
    val shortName: String = "",
    val city: String = "",
    val leagueId: String = "",
    val badgeColorHex: String = "#1565FF",
    val played: Long = 0L,
    val won: Long = 0L,
    val drawn: Long = 0L,
    val lost: Long = 0L,
    val goalsFor: Long = 0L,
    val goalsAgainst: Long = 0L,
    val points: Long = 0L,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val goalDifference: Long get() = goalsFor - goalsAgainst
}

@Keep
@IgnoreExtraProperties
data class SportMatch(
    @DocumentId val id: String = "",
    val leagueId: String = "",
    val leagueName: String = "",
    val sportCategory: String = "FOOTBALL",
    val homeTeamId: String = "",
    val homeTeamName: String = "",
    val awayTeamId: String = "",
    val awayTeamName: String = "",
    val homeScore: Long = 0L,
    val awayScore: Long = 0L,
    val status: String = "UPCOMING",
    val matchMinute: String = "",
    val venue: String = "",
    val sourceType: String = "MANUAL",
    val broadcastId: String? = null,
    val possessionHome: Long = 50L,
    val shotsHome: Long = 0L,
    val shotsAway: Long = 0L,
    val cornersHome: Long = 0L,
    val cornersAway: Long = 0L,
    val featured: Boolean = false,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MatchEvent(
    @DocumentId val id: String = "",
    val matchId: String = "",
    val minute: Long = 0L,
    val type: String = "GOAL",
    val teamSide: String = "HOME",
    val playerName: String = "",
    val detail: String? = null,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Wrestler(
    @DocumentId val id: String = "",
    val name: String = "",
    val nickname: String = "",
    val village: String = "",
    val region: String = "",
    val weightKg: Long = 100L,
    val wins: Long = 0L,
    val losses: Long = 0L,
    val draws: Long = 0L,
    val titlesCount: Long = 0L,
    val bio: String = "",
    val photoUrl: String? = null,
    val featured: Boolean = false,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class KokowaEvent(
    @DocumentId val id: String = "",
    val title: String = "",
    val location: String = "",
    val region: String = "",
    val status: String = "UPCOMING",
    val wrestlerAId: String = "",
    val wrestlerAName: String = "",
    val wrestlerBId: String = "",
    val wrestlerBName: String = "",
    val winnerName: String? = null,
    val roundLabel: String = "",
    val broadcastId: String? = null,
    val scheduledLabel: String = "",
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Tournament(
    @DocumentId val id: String = "",
    val name: String = "",
    val sportCategory: String = "FOOTBALL",
    val season: String = "",
    val location: String = "",
    val status: String = "ONGOING",
    val championName: String? = null,
    val participantNames: List<String> = emptyList(),
    val knockoutBracketSummary: List<String> = emptyList(),
    val announcement: String? = null,
    val popular: Boolean = false,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NewsArticle(
    @DocumentId val id: String = "",
    val title: String = "",
    val summary: String = "",
    val body: String = "",
    val sportCategory: String = "FOOTBALL",
    val authorName: String = "",
    val imageUrl: String? = null,
    val breaking: Boolean = false,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class HighlightVideo(
    @DocumentId val id: String = "",
    val title: String = "",
    val sportCategory: String = "FOOTBALL",
    val durationLabel: String = "03:45",
    val videoUrl: String = "",
    val youtubeVideoId: String? = null,
    val viewCount: Long = 0L,
    val featured: Boolean = false,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SponsorBanner(
    @DocumentId val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val sponsorName: String = "",
    val ctaUrl: String? = null,
    val active: Boolean = true,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
