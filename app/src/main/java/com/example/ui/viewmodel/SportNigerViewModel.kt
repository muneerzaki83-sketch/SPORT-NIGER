package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AuditLog
import com.example.data.model.Broadcast
import com.example.data.model.BroadcastSecret
import com.example.data.model.ChatComment
import com.example.data.model.HighlightVideo
import com.example.data.model.KokowaEvent
import com.example.data.model.MatchEvent
import com.example.data.model.NewsArticle
import com.example.data.model.SponsorBanner
import com.example.data.model.SportCategory
import com.example.data.model.SportMatch
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.Team
import com.example.data.model.Tournament
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.Wrestler
import com.example.data.remote.FootballApiFeedResult
import com.example.data.remote.FootballApiRepository
import com.example.data.repository.AdminRepository
import com.example.data.repository.SportNigerRepository
import com.example.localization.AppLanguage
import com.example.localization.LanguagePreferences
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SportNigerViewModel(
    private val repository: SportNigerRepository,
    private val adminRepository: AdminRepository,
    private val footballApiRepository: FootballApiRepository,
    private val languagePreferences: LanguagePreferences,
    val currentUserId: String,
    initialLanguageCode: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategorySlug = MutableStateFlow("ALL")
    val selectedCategorySlug: StateFlow<String> = _selectedCategorySlug.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _apiFeedResult = MutableStateFlow<FootballApiFeedResult?>(null)
    val apiFeedResult: StateFlow<FootballApiFeedResult?> = _apiFeedResult.asStateFlow()

    private val _selectedBroadcastSecret = MutableStateFlow<BroadcastSecret?>(null)
    val selectedBroadcastSecret: StateFlow<BroadcastSecret?> = _selectedBroadcastSecret.asStateFlow()

    val staffRole: StateFlow<StaffRole> = repository.observeCurrentStaffRole(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(StaffRole.VIEWER)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StaffRole.VIEWER)

    val userProfile: StateFlow<UserProfile?> = repository.observeCurrentUserProfile(currentUserId)
        .catch { error ->
            unwrapFirestoreOrRethrow(error, "observeCurrentUserProfile")
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val categories: StateFlow<UiState<List<SportCategory>>> =
        repository.observeCategories().toUiState("categories")

    val broadcasts: StateFlow<UiState<List<Broadcast>>> =
        repository.observeBroadcasts().toUiState("broadcasts")

    val matches: StateFlow<UiState<List<SportMatch>>> =
        repository.observeMatches().toUiState("matches")

    val teams: StateFlow<UiState<List<Team>>> =
        repository.observeTeams().toUiState("teams")

    val wrestlers: StateFlow<UiState<List<Wrestler>>> =
        repository.observeWrestlers().toUiState("wrestlers")

    val kokowaEvents: StateFlow<UiState<List<KokowaEvent>>> =
        repository.observeKokowaEvents().toUiState("kokowaEvents")

    val tournaments: StateFlow<UiState<List<Tournament>>> =
        repository.observeTournaments().toUiState("tournaments")

    val newsArticles: StateFlow<UiState<List<NewsArticle>>> =
        repository.observeNews().toUiState("news")

    val highlights: StateFlow<UiState<List<HighlightVideo>>> =
        repository.observeHighlights().toUiState("highlights")

    val banners: StateFlow<UiState<List<SponsorBanner>>> =
        repository.observeBanners().toUiState("banners")

    // Admin-only collections gated by staffRole so viewers never trigger PERMISSION_DENIED
    val staffDirectory: StateFlow<UiState<List<StaffMember>>> = staffRole
        .flatMapLatest { role ->
            if (role == StaffRole.SUPER_ADMIN || role == StaffRole.ADMIN) {
                adminRepository.observeStaffDirectory().map<List<StaffMember>, UiState<List<StaffMember>>> {
                    UiState.Success(it)
                }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }
        .catch { error ->
            val msg = unwrapFirestoreOrRethrow(error, "staffDirectory")
            emit(UiState.Error(msg))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val auditLogs: StateFlow<UiState<List<AuditLog>>> = staffRole
        .flatMapLatest { role ->
            if (role == StaffRole.SUPER_ADMIN || role == StaffRole.ADMIN) {
                adminRepository.observeAuditLogs().map<List<AuditLog>, UiState<List<AuditLog>>> {
                    UiState.Success(it)
                }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }
        .catch { error ->
            val msg = unwrapFirestoreOrRethrow(error, "auditLogs")
            emit(UiState.Error(msg))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    init {
        viewModelScope.launch {
            repository.ensureUserProfileExists(initialLanguageCode)
            refreshFootballApiFeed()
        }
    }

    private fun <T> Flow<List<T>>.toUiState(label: String): StateFlow<UiState<List<T>>> =
        this.map<List<T>, UiState<List<T>>> { UiState.Success(it) }
            .catch { error ->
                val msg = unwrapFirestoreOrRethrow(error, label)
                emit(UiState.Error(msg))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    private fun unwrapFirestoreOrRethrow(error: Throwable, tag: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $tag", rootError)
        return rootError.message ?: "Failed to load $tag"
    }

    fun observeCommentsForBroadcast(broadcastId: String): Flow<UiState<List<ChatComment>>> =
        repository.observeBroadcastComments(broadcastId)
            .map<List<ChatComment>, UiState<List<ChatComment>>> { UiState.Success(it) }
            .catch { error ->
                val msg = unwrapFirestoreOrRethrow(error, "comments:$broadcastId")
                emit(UiState.Error(msg))
            }

    fun observeEventsForMatch(matchId: String): Flow<UiState<List<MatchEvent>>> =
        repository.observeMatchEvents(matchId)
            .map<List<MatchEvent>, UiState<List<MatchEvent>>> { UiState.Success(it) }
            .catch { error ->
                val msg = unwrapFirestoreOrRethrow(error, "events:$matchId")
                emit(UiState.Error(msg))
            }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(slug: String) {
        _selectedCategorySlug.value = slug
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun refreshFootballApiFeed() {
        viewModelScope.launch {
            _apiFeedResult.value = footballApiRepository.fetchLiveMatches()
        }
    }

    fun switchLanguage(language: AppLanguage) {
        viewModelScope.launch {
            languagePreferences.setLanguage(language, markOnboarded = true)
            repository.updateUserLanguage(language.code)
        }
    }

    fun updateNotificationPrefs(
        matchStart: Boolean,
        goals: Boolean,
        kokowa: Boolean,
        tournaments: Boolean,
        news: Boolean
    ) {
        viewModelScope.launch {
            val res = repository.updateNotificationPreferences(matchStart, goals, kokowa, tournaments, news)
            res.onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun toggleFavoriteTeam(teamId: String) {
        viewModelScope.launch {
            val current = userProfile.value?.favoriteTeamIds ?: emptyList()
            repository.toggleFavoriteId("favoriteTeamIds", teamId, current)
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun toggleFavoriteWrestler(wrestlerId: String) {
        viewModelScope.launch {
            val current = userProfile.value?.favoriteWrestlerIds ?: emptyList()
            repository.toggleFavoriteId("favoriteWrestlerIds", wrestlerId, current)
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun toggleFavoriteTournament(tournamentId: String) {
        viewModelScope.launch {
            val current = userProfile.value?.favoriteTournamentIds ?: emptyList()
            repository.toggleFavoriteId("favoriteTournamentIds", tournamentId, current)
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun sendChatComment(broadcastId: String, message: String) {
        viewModelScope.launch {
            repository.postChatComment(broadcastId, message)
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun reportComment(broadcastId: String, comment: ChatComment) {
        viewModelScope.launch {
            repository.reportChatComment(broadcastId, comment)
                .onSuccess { _actionMessage.value = "Comment reported to moderators" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    // --- STAFF & ADMIN ACTIONS ---

    fun seedOfficialCatalog() {
        viewModelScope.launch {
            adminRepository.seedOfficialCatalog(staffRole.value)
                .onSuccess { _actionMessage.value = "Official SPORT NIGER catalog initialized" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun loadBroadcastSecret(broadcastId: String) {
        viewModelScope.launch {
            adminRepository.getBroadcastSecret(broadcastId)
                .onSuccess { _selectedBroadcastSecret.value = it }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun clearBroadcastSecret() {
        _selectedBroadcastSecret.value = null
    }

    fun createBroadcast(
        title: String,
        description: String,
        sportCategory: String,
        streamType: String,
        playbackUrl: String,
        youtubeVideoId: String?,
        ingestProtocol: String,
        ingestEndpoint: String,
        rtmpKeyMasked: String,
        obsProfileNote: String,
        status: String,
        chatEnabled: Boolean,
        featured: Boolean
    ) {
        viewModelScope.launch {
            adminRepository.createBroadcast(
                actorRole = staffRole.value,
                title = title,
                description = description,
                sportCategory = sportCategory,
                streamType = streamType,
                playbackUrl = playbackUrl,
                youtubeVideoId = youtubeVideoId,
                ingestProtocol = ingestProtocol,
                ingestEndpoint = ingestEndpoint,
                rtmpKeyMasked = rtmpKeyMasked,
                obsProfileNote = obsProfileNote,
                status = status,
                chatEnabled = chatEnabled,
                featured = featured
            ).onSuccess {
                _actionMessage.value = "Broadcast created"
            }.onFailure {
                _actionMessage.value = it.localizedMessage
            }
        }
    }

    fun updateBroadcastStatus(broadcastId: String, status: String) {
        viewModelScope.launch {
            adminRepository.updateBroadcastStatus(staffRole.value, broadcastId, status)
                .onSuccess { _actionMessage.value = "Stream status updated to $status" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun toggleBroadcastChat(broadcastId: String, enabled: Boolean) {
        viewModelScope.launch {
            adminRepository.toggleBroadcastChat(staffRole.value, broadcastId, enabled)
                .onSuccess { _actionMessage.value = if (enabled) "Chat enabled" else "Chat disabled" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun moderateDeleteComment(broadcastId: String, commentId: String) {
        viewModelScope.launch {
            adminRepository.moderateDeleteComment(staffRole.value, broadcastId, commentId)
                .onSuccess { _actionMessage.value = "Comment removed" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun blockUserFromChat(userId: String) {
        viewModelScope.launch {
            adminRepository.blockUserFromChat(staffRole.value, userId)
                .onSuccess { _actionMessage.value = "User blocked from chat" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun updateMatchScore(
        matchId: String,
        homeScore: Long,
        awayScore: Long,
        status: String,
        minute: String
    ) {
        viewModelScope.launch {
            adminRepository.updateMatchScore(staffRole.value, matchId, homeScore, awayScore, status, minute)
                .onSuccess { _actionMessage.value = "Score updated" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun addMatchEvent(
        matchId: String,
        minute: Long,
        type: String,
        teamSide: String,
        playerName: String,
        detail: String?
    ) {
        viewModelScope.launch {
            adminRepository.addMatchEvent(staffRole.value, matchId, minute, type, teamSide, playerName, detail)
                .onSuccess { _actionMessage.value = "Match event logged" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun addWrestler(
        name: String,
        nickname: String,
        village: String,
        region: String,
        weightKg: Long,
        bio: String
    ) {
        viewModelScope.launch {
            adminRepository.addWrestler(staffRole.value, name, nickname, village, region, weightKg, bio)
                .onSuccess { _actionMessage.value = "Wrestler profile added" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun createKokowaEvent(
        title: String,
        location: String,
        region: String,
        wrestlerAName: String,
        wrestlerBName: String,
        roundLabel: String,
        scheduledLabel: String,
        status: String,
        winnerName: String?
    ) {
        viewModelScope.launch {
            adminRepository.createKokowaEvent(
                staffRole.value,
                title,
                location,
                region,
                wrestlerAName,
                wrestlerBName,
                roundLabel,
                scheduledLabel,
                status,
                winnerName
            ).onSuccess {
                _actionMessage.value = "Kokowa event saved"
            }.onFailure {
                _actionMessage.value = it.localizedMessage
            }
        }
    }

    fun createSportCategory(
        slug: String,
        nameEn: String,
        nameHa: String,
        nameFr: String,
        nameAr: String,
        traditional: Boolean
    ) {
        viewModelScope.launch {
            adminRepository.createSportCategory(staffRole.value, slug, nameEn, nameHa, nameFr, nameAr, traditional)
                .onSuccess { _actionMessage.value = "Category '$nameEn' added" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun createTournament(
        name: String,
        sportCategory: String,
        season: String,
        location: String,
        participantsCsv: String,
        announcement: String?
    ) {
        viewModelScope.launch {
            adminRepository.createTournament(staffRole.value, name, sportCategory, season, location, participantsCsv, announcement)
                .onSuccess { _actionMessage.value = "Tournament created" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun publishNews(
        title: String,
        summary: String,
        body: String,
        sportCategory: String,
        breaking: Boolean
    ) {
        viewModelScope.launch {
            adminRepository.publishNews(staffRole.value, title, summary, body, sportCategory, breaking)
                .onSuccess { _actionMessage.value = "News published" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun assignStaffMember(
        targetUid: String,
        email: String,
        displayName: String,
        role: StaffRole
    ) {
        viewModelScope.launch {
            adminRepository.assignStaffMember(staffRole.value, targetUid, email, displayName, role)
                .onSuccess { _actionMessage.value = "Staff role assigned" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun setStaffStatus(staffUid: String, status: String) {
        viewModelScope.launch {
            adminRepository.setStaffStatus(staffRole.value, staffUid, status)
                .onSuccess { _actionMessage.value = "Staff account updated ($status)" }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    private companion object {
        const val TAG = "SportNigerVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
