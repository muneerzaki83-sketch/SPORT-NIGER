package com.example.data.repository

import com.example.data.model.Broadcast
import com.example.data.model.ChatComment
import com.example.data.model.HighlightVideo
import com.example.data.model.KokowaEvent
import com.example.data.model.League
import com.example.data.model.MatchEvent
import com.example.data.model.NewsArticle
import com.example.data.model.SponsorBanner
import com.example.data.model.SportCategory
import com.example.data.model.SportMatch
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
import com.example.data.model.Team
import com.example.data.model.Tournament
import com.example.data.model.UserProfile
import com.example.data.model.Wrestler
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val BOOTSTRAP_SUPER_ADMIN_EMAIL = "muneerzaki83@gmail.com"

class SportNigerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Google Sign-In required.")

    private fun <T> observePublicCollection(
        collectionName: String,
        clazz: Class<T>
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        db.collection(collectionName)
            .whereEqualTo("visibility", "PUBLIC")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logIfActiveUser(error, OperationType.LIST, collectionName, uid)
                throw error
            }
            .collect { emit(it) }
    }

    private fun logIfActiveUser(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val ex = firestoreError ?: (error as? Exception)
        if (ex != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(ex, operationType, path)
        }
    }

    fun observeCategories(): Flow<List<SportCategory>> =
        observePublicCollection("sport_categories", SportCategory::class.java)
            .map { list -> list.filter { it.active }.sortedBy { it.sortOrder } }

    fun observeBroadcasts(): Flow<List<Broadcast>> =
        observePublicCollection("broadcasts", Broadcast::class.java)
            .map { list ->
                list.sortedWith(
                    compareByDescending<Broadcast> { it.status == "LIVE" }
                        .thenByDescending { it.featured }
                        .thenByDescending { it.viewerCount }
                )
            }

    fun observeMatches(): Flow<List<SportMatch>> =
        observePublicCollection("matches", SportMatch::class.java)
            .map { list ->
                list.sortedWith(
                    compareByDescending<SportMatch> { it.status == "LIVE" || it.status == "HALFTIME" }
                        .thenByDescending { it.featured }
                        .thenBy { it.status == "COMPLETED" }
                )
            }

    fun observeMatchEvents(matchId: String): Flow<List<MatchEvent>> = flow {
        val uid = requireUserId()
        val path = "matches/$matchId/events"
        db.collection("matches").document(matchId).collection("events")
            .whereEqualTo("visibility", "PUBLIC")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(MatchEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.minute }
            }
            .catch { error ->
                logIfActiveUser(error, OperationType.LIST, path, uid)
                throw error
            }
            .collect { emit(it) }
    }

    fun observeLeagues(): Flow<List<League>> =
        observePublicCollection("leagues", League::class.java)

    fun observeTeams(): Flow<List<Team>> =
        observePublicCollection("teams", Team::class.java)
            .map { list ->
                list.sortedWith(
                    compareByDescending<Team> { it.points }
                        .thenByDescending { it.goalDifference }
                        .thenByDescending { it.goalsFor }
                )
            }

    fun observeWrestlers(): Flow<List<Wrestler>> =
        observePublicCollection("wrestlers", Wrestler::class.java)
            .map { list ->
                list.sortedWith(
                    compareByDescending<Wrestler> { it.featured }
                        .thenByDescending { it.titlesCount }
                        .thenByDescending { it.wins }
                )
            }

    fun observeKokowaEvents(): Flow<List<KokowaEvent>> =
        observePublicCollection("kokowa_events", KokowaEvent::class.java)
            .map { list ->
                list.sortedWith(
                    compareByDescending<KokowaEvent> { it.status == "LIVE" }
                        .thenBy { it.status == "COMPLETED" }
                )
            }

    fun observeTournaments(): Flow<List<Tournament>> =
        observePublicCollection("tournaments", Tournament::class.java)
            .map { list ->
                list.sortedWith(
                    compareByDescending<Tournament> { it.popular }
                        .thenByDescending { it.status == "ONGOING" }
                )
            }

    fun observeNews(): Flow<List<NewsArticle>> =
        observePublicCollection("news", NewsArticle::class.java)
            .map { list ->
                list.sortedWith(
                    compareByDescending<NewsArticle> { it.breaking }
                        .thenByDescending { it.createdAt?.seconds ?: 0L }
                )
            }

    fun observeHighlights(): Flow<List<HighlightVideo>> =
        observePublicCollection("highlights", HighlightVideo::class.java)
            .map { list ->
                list.sortedWith(
                    compareByDescending<HighlightVideo> { it.featured }
                        .thenByDescending { it.viewCount }
                )
            }

    fun observeBanners(): Flow<List<SponsorBanner>> =
        observePublicCollection("banners", SponsorBanner::class.java)
            .map { list -> list.filter { it.active } }

    fun observeBroadcastComments(broadcastId: String): Flow<List<ChatComment>> = flow {
        val uid = requireUserId()
        val path = "broadcasts/$broadcastId/comments"
        db.collection("broadcasts").document(broadcastId).collection("comments")
            .whereEqualTo("deleted", false)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .catch { error ->
                logIfActiveUser(error, OperationType.LIST, path, uid)
                throw error
            }
            .collect { emit(it) }
    }

    fun observeCurrentUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        db.collection("users").document(uid)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                logIfActiveUser(error, OperationType.GET, path, uid)
                throw error
            }
            .collect { emit(it) }
    }

    fun observeCurrentStaffRole(uid: String): Flow<StaffRole> = flow {
        val user = auth.currentUser
        if (user != null && user.isEmailVerified && user.email == BOOTSTRAP_SUPER_ADMIN_EMAIL) {
            emit(StaffRole.SUPER_ADMIN)
            return@flow
        }
        val path = "staff/$uid"
        db.collection("staff").document(uid)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    val staff = snapshot.toObject(StaffMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (staff != null && staff.status == "ACTIVE") {
                        StaffRole.fromRaw(staff.role)
                    } else {
                        StaffRole.VIEWER
                    }
                } else {
                    StaffRole.VIEWER
                }
            }
            .catch { error ->
                logIfActiveUser(error, OperationType.GET, path, uid)
                emit(StaffRole.VIEWER)
            }
            .collect { emit(it) }
    }

    suspend fun ensureUserProfileExists(languageCode: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = (user.displayName ?: user.email?.substringBefore("@") ?: "Sport Niger Fan")
                    .take(80).ifBlank { "Sport Niger Fan" }
                val payload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to user.email?.take(160),
                    "photoUrl" to user.photoUrl?.toString()?.take(500),
                    "preferredLanguage" to languageCode.takeIf { it in listOf("en", "ha", "fr", "ar") }.orEmpty().ifBlank { "en" },
                    "favoriteTeamIds" to emptyList<String>(),
                    "favoriteWrestlerIds" to emptyList<String>(),
                    "favoriteTournamentIds" to emptyList<String>(),
                    "notifyMatchStart" to true,
                    "notifyGoals" to true,
                    "notifyKokowa" to true,
                    "notifyTournaments" to true,
                    "notifyNews" to false,
                    "chatBlocked" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserLanguage(languageCode: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "preferredLanguage" to languageCode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNotificationPreferences(
        matchStart: Boolean,
        goals: Boolean,
        kokowa: Boolean,
        tournaments: Boolean,
        news: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "notifyMatchStart" to matchStart,
                    "notifyGoals" to goals,
                    "notifyKokowa" to kokowa,
                    "notifyTournaments" to tournaments,
                    "notifyNews" to news,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleFavoriteId(field: String, itemId: String, currentIds: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        require(field in setOf("favoriteTeamIds", "favoriteWrestlerIds", "favoriteTournamentIds"))
        val path = "users/$uid"
        return try {
            val updated = if (itemId in currentIds) {
                currentIds - itemId
            } else {
                (currentIds + itemId).take(20)
            }
            db.collection("users").document(uid).update(
                mapOf(
                    field to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun postChatComment(broadcastId: String, rawMessage: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val message = rawMessage.trim().take(400)
        if (message.isBlank()) return Result.failure(IllegalArgumentException("Message cannot be empty"))
        val docRef = db.collection("broadcasts").document(broadcastId).collection("comments").document()
        return try {
            val userName = (user.displayName ?: user.email?.substringBefore("@") ?: "Viewer").take(80)
            val payload = mutableMapOf<String, Any?>(
                "broadcastId" to broadcastId,
                "userId" to user.uid,
                "userName" to userName,
                "userPhotoUrl" to user.photoUrl?.toString()?.take(500),
                "message" to message,
                "deleted" to false,
                "reported" to false,
                "reportCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun reportChatComment(broadcastId: String, comment: ChatComment): Result<Unit> {
        val path = "broadcasts/$broadcastId/comments/${comment.id}"
        return try {
            requireUserId()
            db.collection("broadcasts").document(broadcastId)
                .collection("comments").document(comment.id)
                .update(
                    mapOf(
                        "reported" to true,
                        "reportCount" to (comment.reportCount + 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
