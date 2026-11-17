package com.example.data.repository

import com.example.data.model.AuditLog
import com.example.data.model.BroadcastSecret
import com.example.data.model.StaffMember
import com.example.data.model.StaffRole
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

class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireActor(): Pair<String, String> {
        val user = auth.currentUser ?: throw IllegalStateException("Staff authentication required")
        return user.uid to (user.email ?: "staff@sportniger.ne")
    }

    private suspend fun recordAudit(
        actorRole: StaffRole,
        action: String,
        targetType: String,
        targetId: String,
        summary: String
    ) {
        val (uid, email) = requireActor()
        val ref = db.collection("audit_logs").document()
        runCatching {
            ref.set(
                mapOf(
                    "actorUid" to uid,
                    "actorEmail" to email.take(160),
                    "actorRole" to actorRole.name,
                    "action" to action.take(80),
                    "targetType" to targetType.take(60),
                    "targetId" to targetId.take(128),
                    "summary" to summary.take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeStaffDirectory(): Flow<List<StaffMember>> = flow {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
        db.collection("staff")
            .snapshots()
            .map { snap ->
                snap.toObjects(StaffMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "staff")
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> = flow {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
        db.collection("audit_logs")
            .snapshots()
            .map { snap ->
                snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    .take(40)
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "audit_logs")
                }
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun getBroadcastSecret(broadcastId: String): Result<BroadcastSecret?> {
        val path = "broadcast_secrets/$broadcastId"
        return try {
            val snap = db.collection("broadcast_secrets").document(broadcastId).get().await()
            Result.success(
                if (snap.exists()) snap.toObject(BroadcastSecret::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                else null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createBroadcast(
        actorRole: StaffRole,
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
    ): Result<Unit> {
        val docRef = db.collection("broadcasts").document()
        return try {
            val (uid, _) = requireActor()
            val payload = mutableMapOf<String, Any?>(
                "title" to title.trim().take(120),
                "description" to description.trim().take(1000).ifBlank { "Live SPORT NIGER coverage" },
                "sportCategory" to sportCategory.trim().uppercase().take(40),
                "streamType" to streamType,
                "playbackUrl" to playbackUrl.trim().take(600),
                "youtubeVideoId" to youtubeVideoId?.trim()?.takeIf { it.isNotBlank() }?.take(64),
                "ingestProtocol" to ingestProtocol,
                "ingestEndpoint" to ingestEndpoint.trim().take(300).ifBlank { "rtmp://ingest.sportniger.ne/live" },
                "streamKeyConfigured" to true,
                "status" to status,
                "viewerCount" to if (status == "LIVE") 120L else 0L,
                "chatEnabled" to chatEnabled,
                "featured" to featured,
                "scheduledLabel" to if (status == "LIVE") "LIVE NOW" else "SCHEDULED",
                "visibility" to "PUBLIC",
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()

            db.collection("broadcast_secrets").document(docRef.id).set(
                mapOf(
                    "broadcastId" to docRef.id,
                    "rtmpServerUrl" to ingestEndpoint.trim().take(300).ifBlank { "rtmp://ingest.sportniger.ne/live" },
                    "streamKeyMasked" to rtmpKeyMasked.trim().take(120).ifBlank { "sn_live_••••_secure" },
                    "obsProfileNote" to obsProfileNote.trim().take(400).ifBlank { "1080p60 H.264 CBR 4500kbps" },
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            recordAudit(actorRole, "CREATE_BROADCAST", "broadcast", docRef.id, "Created broadcast: $title")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateBroadcastStatus(
        actorRole: StaffRole,
        broadcastId: String,
        newStatus: String
    ): Result<Unit> {
        val path = "broadcasts/$broadcastId"
        return try {
            db.collection("broadcasts").document(broadcastId).update(
                mapOf(
                    "status" to newStatus,
                    "scheduledLabel" to when (newStatus) {
                        "LIVE" -> "LIVE NOW"
                        "ENDED" -> "ENDED"
                        "OFFLINE" -> "OFFLINE"
                        else -> "UPCOMING"
                    },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordAudit(actorRole, "UPDATE_BROADCAST_STATUS", "broadcast", broadcastId, "Set stream status -> $newStatus")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleBroadcastChat(
        actorRole: StaffRole,
        broadcastId: String,
        enabled: Boolean
    ): Result<Unit> {
        val path = "broadcasts/$broadcastId"
        return try {
            db.collection("broadcasts").document(broadcastId).update(
                mapOf("chatEnabled" to enabled, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            recordAudit(actorRole, "TOGGLE_CHAT", "broadcast", broadcastId, "Chat enabled=$enabled")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun moderateDeleteComment(
        actorRole: StaffRole,
        broadcastId: String,
        commentId: String
    ): Result<Unit> {
        val path = "broadcasts/$broadcastId/comments/$commentId"
        return try {
            db.collection("broadcasts").document(broadcastId)
                .collection("comments").document(commentId)
                .update(
                    mapOf("deleted" to true, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            recordAudit(actorRole, "DELETE_COMMENT", "comment", commentId, "Moderated chat comment")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun blockUserFromChat(
        actorRole: StaffRole,
        targetUserId: String
    ): Result<Unit> {
        val path = "users/$targetUserId"
        return try {
            db.collection("users").document(targetUserId).update(
                mapOf("chatBlocked" to true, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            recordAudit(actorRole, "BLOCK_CHAT_USER", "user", targetUserId, "Blocked user from live chat")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMatchScore(
        actorRole: StaffRole,
        matchId: String,
        homeScore: Long,
        awayScore: Long,
        status: String,
        minute: String
    ): Result<Unit> {
        val path = "matches/$matchId"
        return try {
            db.collection("matches").document(matchId).update(
                mapOf(
                    "homeScore" to homeScore.coerceIn(0L, 99L),
                    "awayScore" to awayScore.coerceIn(0L, 99L),
                    "status" to status,
                    "matchMinute" to minute.trim().take(30).ifBlank { status },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordAudit(actorRole, "UPDATE_SCORE", "match", matchId, "Score $homeScore-$awayScore ($status $minute)")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addMatchEvent(
        actorRole: StaffRole,
        matchId: String,
        minute: Long,
        type: String,
        teamSide: String,
        playerName: String,
        detail: String?
    ): Result<Unit> {
        val docRef = db.collection("matches").document(matchId).collection("events").document()
        return try {
            val payload = mutableMapOf<String, Any?>(
                "matchId" to matchId,
                "minute" to minute.coerceIn(0L, 150L),
                "type" to type,
                "teamSide" to teamSide,
                "playerName" to playerName.trim().take(80),
                "detail" to detail?.trim()?.takeIf { it.isNotBlank() }?.take(120),
                "visibility" to "PUBLIC",
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            recordAudit(actorRole, "ADD_MATCH_EVENT", "match", matchId, "$type ${minute}' $playerName")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addWrestler(
        actorRole: StaffRole,
        name: String,
        nickname: String,
        village: String,
        region: String,
        weightKg: Long,
        bio: String
    ): Result<Unit> {
        val docRef = db.collection("wrestlers").document()
        return try {
            docRef.set(
                mapOf(
                    "name" to name.trim().take(80),
                    "nickname" to nickname.trim().take(60),
                    "village" to village.trim().take(80),
                    "region" to region.trim().take(60),
                    "weightKg" to weightKg.coerceIn(50L, 220L),
                    "wins" to 0L,
                    "losses" to 0L,
                    "draws" to 0L,
                    "titlesCount" to 0L,
                    "bio" to bio.trim().take(800).ifBlank { "Kokowa competitor representing $region." },
                    "featured" to true,
                    "visibility" to "PUBLIC",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordAudit(actorRole, "ADD_WRESTLER", "wrestler", docRef.id, "Added Kokowa wrestler $name ($region)")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createKokowaEvent(
        actorRole: StaffRole,
        title: String,
        location: String,
        region: String,
        wrestlerAName: String,
        wrestlerBName: String,
        roundLabel: String,
        scheduledLabel: String,
        status: String,
        winnerName: String?
    ): Result<Unit> {
        val docRef = db.collection("kokowa_events").document()
        return try {
            val payload = mutableMapOf<String, Any?>(
                "title" to title.trim().take(120),
                "location" to location.trim().take(100),
                "region" to region.trim().take(60),
                "status" to status,
                "wrestlerAId" to "w_custom_a",
                "wrestlerAName" to wrestlerAName.trim().take(80),
                "wrestlerBId" to "w_custom_b",
                "wrestlerBName" to wrestlerBName.trim().take(80),
                "winnerName" to winnerName?.trim()?.takeIf { it.isNotBlank() }?.take(80),
                "roundLabel" to roundLabel.trim().take(80),
                "scheduledLabel" to scheduledLabel.trim().take(80),
                "visibility" to "PUBLIC",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            recordAudit(actorRole, "CREATE_KOKOWA_EVENT", "kokowa_event", docRef.id, "Kokowa bout: $wrestlerAName vs $wrestlerBName")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createSportCategory(
        actorRole: StaffRole,
        slug: String,
        nameEn: String,
        nameHa: String,
        nameFr: String,
        nameAr: String,
        traditional: Boolean
    ): Result<Unit> {
        val safeSlug = slug.trim().uppercase().replace(" ", "_").take(40)
        val docId = "cat_${safeSlug.lowercase()}"
        val docRef = db.collection("sport_categories").document(docId)
        return try {
            val (uid, _) = requireActor()
            docRef.set(
                mapOf(
                    "slug" to safeSlug,
                    "nameEn" to nameEn.trim().take(60),
                    "nameHa" to nameHa.trim().take(60).ifBlank { nameEn.trim().take(60) },
                    "nameFr" to nameFr.trim().take(60).ifBlank { nameEn.trim().take(60) },
                    "nameAr" to nameAr.trim().take(60).ifBlank { nameEn.trim().take(60) },
                    "iconKey" to if (traditional) "TRADITIONAL" else "SPORTS",
                    "traditional" to traditional,
                    "active" to true,
                    "sortOrder" to 50L,
                    "visibility" to "PUBLIC",
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordAudit(actorRole, "ADD_CATEGORY", "sport_category", docId, "Added category $nameEn")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createTournament(
        actorRole: StaffRole,
        name: String,
        sportCategory: String,
        season: String,
        location: String,
        participantsCsv: String,
        announcement: String?
    ): Result<Unit> {
        val docRef = db.collection("tournaments").document()
        return try {
            val participants = participantsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(32)
            val payload = mutableMapOf<String, Any?>(
                "name" to name.trim().take(120),
                "sportCategory" to sportCategory.trim().uppercase().take(40),
                "season" to season.trim().take(40),
                "location" to location.trim().take(100),
                "status" to "ONGOING",
                "participantNames" to participants,
                "knockoutBracketSummary" to listOf("QF|Stage Pending", "SF|Stage Pending", "FINAL|Schedule Pending"),
                "announcement" to announcement?.trim()?.takeIf { it.isNotBlank() }?.take(400),
                "popular" to true,
                "visibility" to "PUBLIC",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            recordAudit(actorRole, "CREATE_TOURNAMENT", "tournament", docRef.id, "Created tournament: $name")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun publishNews(
        actorRole: StaffRole,
        title: String,
        summary: String,
        body: String,
        sportCategory: String,
        breaking: Boolean
    ): Result<Unit> {
        val docRef = db.collection("news").document()
        return try {
            val author = auth.currentUser?.displayName ?: "SPORT NIGER Editorial"
            docRef.set(
                mapOf(
                    "title" to title.trim().take(160),
                    "summary" to summary.trim().take(300),
                    "body" to body.trim().take(4000).ifBlank { summary.trim().take(300) },
                    "sportCategory" to sportCategory.trim().uppercase().take(40),
                    "authorName" to author.take(80),
                    "breaking" to breaking,
                    "visibility" to "PUBLIC",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordAudit(actorRole, "PUBLISH_NEWS", "news", docRef.id, "Published news: $title")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun assignStaffMember(
        actorRole: StaffRole,
        targetUid: String,
        email: String,
        displayName: String,
        role: StaffRole
    ): Result<Unit> {
        val cleanUid = targetUid.trim()
        val path = "staff/$cleanUid"
        return try {
            val (actorUid, _) = requireActor()
            db.collection("staff").document(cleanUid).set(
                mapOf(
                    "userId" to cleanUid,
                    "email" to email.trim().take(160),
                    "displayName" to displayName.trim().take(80),
                    "role" to role.name,
                    "status" to "ACTIVE",
                    "permissions" to listOf(role.name),
                    "assignedBy" to actorUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordAudit(actorRole, "ASSIGN_STAFF_ROLE", "staff", cleanUid, "Assigned ${role.name} to $email")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun setStaffStatus(
        actorRole: StaffRole,
        staffUid: String,
        status: String
    ): Result<Unit> {
        val path = "staff/$staffUid"
        return try {
            db.collection("staff").document(staffUid).update(
                mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            recordAudit(actorRole, "UPDATE_STAFF_STATUS", "staff", staffUid, "Staff status -> $status")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun seedOfficialCatalog(actorRole: StaffRole): Result<Unit> {
        return try {
            val (uid, _) = requireActor()
            val batch = db.batch()
            val ts = FieldValue.serverTimestamp()

            // Categories
            val categories = listOf(
                Triple("FOOTBALL", "Football" to "Kwallon Ƙafa", "Football" to "كرة القدم"),
                Triple("KOKOWA", "Kokowa Wrestling" to "Kokowa", "Lutte Kokowa" to "مصارعة كوكوا"),
                Triple("DAMBE", "Dambe Boxing" to "Dambe", "Boxe Dambe" to "ملاكمة دامبي"),
                Triple("CAMEL_RACING", "Camel Racing" to "Tseren Raƙuma", "Course de Chameaux" to "سباق الهجن")
            )
            categories.forEachIndexed { idx, (slug, enHa, frAr) ->
                val ref = db.collection("sport_categories").document("cat_${slug.lowercase()}")
                batch.set(
                    ref,
                    mapOf(
                        "slug" to slug,
                        "nameEn" to enHa.first,
                        "nameHa" to enHa.second,
                        "nameFr" to frAr.first,
                        "nameAr" to frAr.second,
                        "iconKey" to if (slug == "FOOTBALL") "FOOTBALL" else "TRADITIONAL",
                        "traditional" to (slug != "FOOTBALL"),
                        "active" to true,
                        "sortOrder" to idx.toLong(),
                        "visibility" to "PUBLIC",
                        "createdBy" to uid,
                        "createdAt" to ts,
                        "updatedAt" to ts
                    )
                )
            }

            // League & Teams (Super Ligue Niger)
            batch.set(
                db.collection("leagues").document("super_ligue_niger"),
                mapOf(
                    "name" to "Super Ligue Niger",
                    "country" to "Niger",
                    "season" to "2026/27",
                    "sportCategory" to "FOOTBALL",
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )
            val teams = listOf(
                listOf("team_as_fan", "AS FAN Niamey", "FAN", "Niamey", "#1565FF", 14L, 10L, 3L, 1L, 26L, 9L, 33L),
                listOf("team_usgn", "USGN Niamey", "USGN", "Niamey", "#0DB02B", 14L, 9L, 3L, 2L, 22L, 10L, 30L),
                listOf("team_sahel", "Sahel SC", "SAH", "Niamey", "#FF8C1A", 14L, 8L, 4L, 2L, 21L, 12L, 28L),
                listOf("team_nigelec", "ASN Nigelec", "NIG", "Niamey", "#00B4FF", 14L, 7L, 3L, 4L, 18L, 14L, 24L),
                listOf("team_douanes", "AS Douanes", "DOU", "Niamey", "#D9381E", 14L, 6L, 4L, 4L, 16L, 13L, 22L),
                listOf("team_janger", "Janghorzo FC", "JAN", "Maradi", "#8E44AD", 14L, 5L, 3L, 6L, 14L, 17L, 18L)
            )
            teams.forEach { t ->
                batch.set(
                    db.collection("teams").document(t[0] as String),
                    mapOf(
                        "name" to t[1],
                        "shortName" to t[2],
                        "city" to t[3],
                        "leagueId" to "super_ligue_niger",
                        "badgeColorHex" to t[4],
                        "played" to t[5],
                        "won" to t[6],
                        "drawn" to t[7],
                        "lost" to t[8],
                        "goalsFor" to t[9],
                        "goalsAgainst" to t[10],
                        "points" to t[11],
                        "visibility" to "PUBLIC",
                        "createdAt" to ts,
                        "updatedAt" to ts
                    )
                )
            }

            // Broadcasts (Public HLS demo streams + YouTube Live + OBS RTMP ingest metadata)
            batch.set(
                db.collection("broadcasts").document("bc_super_ligue_live"),
                mapOf(
                    "title" to "AS FAN vs USGN — Super Ligue Derby",
                    "description" to "Live broadcast from Stade Général Seyni Kountché in Niamey with multi-bitrate HLS delivery.",
                    "sportCategory" to "FOOTBALL",
                    "streamType" to "HLS",
                    "playbackUrl" to "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    "ingestProtocol" to "OBS_RTMP",
                    "ingestEndpoint" to "rtmp://ingest.sportniger.ne/live",
                    "streamKeyConfigured" to true,
                    "status" to "LIVE",
                    "viewerCount" to 6420L,
                    "chatEnabled" to true,
                    "featured" to true,
                    "matchId" to "match_fan_usgn",
                    "scheduledLabel" to "LIVE NOW",
                    "visibility" to "PUBLIC",
                    "createdBy" to uid,
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )
            batch.set(
                db.collection("broadcasts").document("bc_kokowa_sabre"),
                mapOf(
                    "title" to "Sabre National Kokowa — Maradi Arena",
                    "description" to "Traditional Nigerien wrestling championship live stream from Arène de Lutte Traditionnelle, Maradi.",
                    "sportCategory" to "KOKOWA",
                    "streamType" to "HLS",
                    "playbackUrl" to "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                    "ingestProtocol" to "MOBILE_LIVE",
                    "ingestEndpoint" to "rtmp://ingest.sportniger.ne/kokowa",
                    "streamKeyConfigured" to true,
                    "status" to "LIVE",
                    "viewerCount" to 9150L,
                    "chatEnabled" to true,
                    "featured" to true,
                    "scheduledLabel" to "LIVE NOW",
                    "visibility" to "PUBLIC",
                    "createdBy" to uid,
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )
            batch.set(
                db.collection("broadcasts").document("bc_mena_upcoming"),
                mapOf(
                    "title" to "Mena Niger vs Mali — Pre-Match Studio",
                    "description" to "Tactical preview and warm-up coverage on SPORT NIGER YouTube Live & HLS.",
                    "sportCategory" to "FOOTBALL",
                    "streamType" to "YOUTUBE_LIVE",
                    "playbackUrl" to "https://www.youtube.com/watch?v=jfKfPfyJRdk",
                    "youtubeVideoId" to "jfKfPfyJRdk",
                    "ingestProtocol" to "YOUTUBE",
                    "ingestEndpoint" to "rtmp://a.rtmp.youtube.com/live2",
                    "streamKeyConfigured" to true,
                    "status" to "UPCOMING",
                    "viewerCount" to 480L,
                    "chatEnabled" to true,
                    "featured" to false,
                    "scheduledLabel" to "Tonight 20:00 WAT",
                    "visibility" to "PUBLIC",
                    "createdBy" to uid,
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )

            // Matches + Events
            batch.set(
                db.collection("matches").document("match_fan_usgn"),
                mapOf(
                    "leagueId" to "super_ligue_niger",
                    "leagueName" to "Super Ligue Niger",
                    "sportCategory" to "FOOTBALL",
                    "homeTeamId" to "team_as_fan",
                    "homeTeamName" to "AS FAN Niamey",
                    "awayTeamId" to "team_usgn",
                    "awayTeamName" to "USGN Niamey",
                    "homeScore" to 2L,
                    "awayScore" to 1L,
                    "status" to "LIVE",
                    "matchMinute" to "67'",
                    "venue" to "Stade Général Seyni Kountché, Niamey",
                    "sourceType" to "MANUAL",
                    "broadcastId" to "bc_super_ligue_live",
                    "possessionHome" to 54L,
                    "shotsHome" to 11L,
                    "shotsAway" to 7L,
                    "cornersHome" to 6L,
                    "cornersAway" to 3L,
                    "featured" to true,
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )
            batch.set(
                db.collection("matches").document("match_fan_usgn").collection("events").document("ev_1"),
                mapOf("matchId" to "match_fan_usgn", "minute" to 19L, "type" to "GOAL", "teamSide" to "HOME", "playerName" to "Moussa Soumana", "detail" to "Header from corner", "visibility" to "PUBLIC", "createdAt" to ts)
            )
            batch.set(
                db.collection("matches").document("match_fan_usgn").collection("events").document("ev_2"),
                mapOf("matchId" to "match_fan_usgn", "minute" to 38L, "type" to "GOAL", "teamSide" to "AWAY", "playerName" to "Amadou Wonkoye", "detail" to "Penalty kick", "visibility" to "PUBLIC", "createdAt" to ts)
            )
            batch.set(
                db.collection("matches").document("match_fan_usgn").collection("events").document("ev_3"),
                mapOf("matchId" to "match_fan_usgn", "minute" to 52L, "type" to "YELLOW_CARD", "teamSide" to "AWAY", "playerName" to "Zakari Saley", "detail" to "Tactical foul", "visibility" to "PUBLIC", "createdAt" to ts)
            )
            batch.set(
                db.collection("matches").document("match_fan_usgn").collection("events").document("ev_4"),
                mapOf("matchId" to "match_fan_usgn", "minute" to 61L, "type" to "GOAL", "teamSide" to "HOME", "playerName" to "Harouna Garba", "detail" to "Low drive", "visibility" to "PUBLIC", "createdAt" to ts)
            )

            batch.set(
                db.collection("matches").document("match_sahel_nigelec"),
                mapOf(
                    "leagueId" to "super_ligue_niger",
                    "leagueName" to "Super Ligue Niger",
                    "sportCategory" to "FOOTBALL",
                    "homeTeamId" to "team_sahel",
                    "homeTeamName" to "Sahel SC",
                    "awayTeamId" to "team_nigelec",
                    "awayTeamName" to "ASN Nigelec",
                    "homeScore" to 0L,
                    "awayScore" to 0L,
                    "status" to "UPCOMING",
                    "matchMinute" to "17:00 WAT",
                    "venue" to "Stade Municipal, Niamey",
                    "sourceType" to "MANUAL",
                    "possessionHome" to 50L,
                    "shotsHome" to 0L,
                    "shotsAway" to 0L,
                    "cornersHome" to 0L,
                    "cornersAway" to 0L,
                    "featured" to false,
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )
            batch.set(
                db.collection("matches").document("match_douanes_janger"),
                mapOf(
                    "leagueId" to "super_ligue_niger",
                    "leagueName" to "Super Ligue Niger",
                    "sportCategory" to "FOOTBALL",
                    "homeTeamId" to "team_douanes",
                    "homeTeamName" to "AS Douanes",
                    "awayTeamId" to "team_janger",
                    "awayTeamName" to "Janghorzo FC",
                    "homeScore" to 3L,
                    "awayScore" to 1L,
                    "status" to "COMPLETED",
                    "matchMinute" to "FT",
                    "venue" to "Stade de Maradi",
                    "sourceType" to "MANUAL",
                    "possessionHome" to 58L,
                    "shotsHome" to 14L,
                    "shotsAway" to 6L,
                    "cornersHome" to 7L,
                    "cornersAway" to 2L,
                    "featured" to false,
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )

            // Kokowa Wrestlers
            val wrestlers = listOf(
                listOf("w_issaka", "Issaka Issaka", "El Hadji", "Dogondoutchi", "Dosso", 112L, 42L, 3L, 2L, 6L, "Six-time Sabre National champion renowned for explosive hip throws and tactical balance."),
                listOf("w_kadri", "Kadri Abdou", "Issa", "Konni", "Tahoua", 108L, 36L, 5L, 1L, 3L, "Former Sabre National winner from Tahoua celebrated for speed and endurance in marathon bouts."),
                listOf("w_aibo", "Aibo Hassan", "Zaki", "Tessaoua", "Maradi", 115L, 29L, 4L, 2L, 2L, "Powerhouse of the Maradi arena with undefeated streaks in regional championships."),
                listOf("w_abdou", "Abdou Salam", "Damagaram", "Mirriah", "Zinder", 104L, 25L, 6L, 3L, 1L, "Technical specialist from Zinder known for rapid leg sweeps and defensive counters.")
            )
            wrestlers.forEach { w ->
                batch.set(
                    db.collection("wrestlers").document(w[0] as String),
                    mapOf(
                        "name" to w[1],
                        "nickname" to w[2],
                        "village" to w[3],
                        "region" to w[4],
                        "weightKg" to w[5],
                        "wins" to w[6],
                        "losses" to w[7],
                        "draws" to w[8],
                        "titlesCount" to w[9],
                        "bio" to w[10],
                        "featured" to true,
                        "visibility" to "PUBLIC",
                        "createdAt" to ts,
                        "updatedAt" to ts
                    )
                )
            }

            // Kokowa Events
            batch.set(
                db.collection("kokowa_events").document("ke_sabre_final"),
                mapOf(
                    "title" to "Sabre National 46e Édition — Grande Finale",
                    "location" to "Arène de Maradi",
                    "region" to "Maradi",
                    "status" to "LIVE",
                    "wrestlerAId" to "w_issaka",
                    "wrestlerAName" to "Issaka Issaka (Dosso)",
                    "wrestlerBId" to "w_aibo",
                    "wrestlerBName" to "Aibo Hassan (Maradi)",
                    "roundLabel" to "Final — Sabre National",
                    "broadcastId" to "bc_kokowa_sabre",
                    "scheduledLabel" to "LIVE NOW",
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )
            batch.set(
                db.collection("kokowa_events").document("ke_tahoua_cup"),
                mapOf(
                    "title" to "Coupe de l'Unité — Semi-Final",
                    "location" to "Arène de Tahoua",
                    "region" to "Tahoua",
                    "status" to "UPCOMING",
                    "wrestlerAId" to "w_kadri",
                    "wrestlerAName" to "Kadri Abdou (Tahoua)",
                    "wrestlerBId" to "w_abdou",
                    "wrestlerBName" to "Abdou Salam (Zinder)",
                    "roundLabel" to "Semi-Final",
                    "scheduledLabel" to "Friday 16:30 WAT",
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )

            // Tournaments
            batch.set(
                db.collection("tournaments").document("t_sabre_national"),
                mapOf(
                    "name" to "Sabre National de Kokowa",
                    "sportCategory" to "KOKOWA",
                    "season" to "2026",
                    "location" to "Maradi, Niger",
                    "status" to "ONGOING",
                    "participantNames" to listOf("Dosso", "Tahoua", "Maradi", "Zinder", "Niamey", "Agadez", "Tillabéri", "Diffa"),
                    "knockoutBracketSummary" to listOf(
                        "QF|Dosso def. Agadez",
                        "QF|Maradi def. Diffa",
                        "SF|Issaka Issaka (Dosso) def. Kadri Abdou (Tahoua)",
                        "SF|Aibo Hassan (Maradi) def. Abdou Salam (Zinder)",
                        "FINAL|Issaka Issaka vs Aibo Hassan — LIVE"
                    ),
                    "announcement" to "Official 8-region championship hosted in Maradi. Free live coverage on SPORT NIGER.",
                    "popular" to true,
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )
            batch.set(
                db.collection("tournaments").document("t_coupe_du_niger"),
                mapOf(
                    "name" to "Coupe Nationale du Niger",
                    "sportCategory" to "FOOTBALL",
                    "season" to "2026",
                    "location" to "Niamey, Niger",
                    "status" to "ONGOING",
                    "participantNames" to listOf("AS FAN", "USGN", "Sahel SC", "ASN Nigelec", "AS Douanes", "Janghorzo FC"),
                    "knockoutBracketSummary" to listOf(
                        "QF|AS FAN 2-0 ASN Nigelec",
                        "QF|USGN 1-0 AS Douanes",
                        "SF|AS FAN vs Sahel SC — Nov 24",
                        "SF|USGN vs Janghorzo FC — Nov 25"
                    ),
                    "announcement" to "Semi-finals take place at Stade Général Seyni Kountché.",
                    "popular" to true,
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )

            // News & Highlights & Sponsor Banner
            batch.set(
                db.collection("news").document("news_sabre_opening"),
                mapOf(
                    "title" to "Maradi Packed as Sabre National Reaches Historic Final",
                    "summary" to "Thousands gather at the traditional wrestling arena while viewers across Niger stream free on SPORT NIGER.",
                    "body" to "The 46th Sabre National brings together elite champions from all eight regions of Niger. Dosso's Issaka Issaka faces Maradi's Aibo Hassan in an electrifying final broadcast live in HD.",
                    "sportCategory" to "KOKOWA",
                    "authorName" to "SPORT NIGER Desk",
                    "breaking" to true,
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )
            batch.set(
                db.collection("news").document("news_super_ligue"),
                mapOf(
                    "title" to "AS FAN Leads Super Ligue Niger After Derby Thrills",
                    "summary" to "Niamey derby delivers goals and tactical intensity as race for the title heats up.",
                    "body" to "AS FAN Niamey maintained top spot with clinical finishing in front of home supporters, while USGN stays three points behind.",
                    "sportCategory" to "FOOTBALL",
                    "authorName" to "Amadou Bello",
                    "breaking" to false,
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )
            batch.set(
                db.collection("highlights").document("hl_kokowa_throw"),
                mapOf(
                    "title" to "Best Throws — Sabre National Semi-Finals",
                    "sportCategory" to "KOKOWA",
                    "durationLabel" to "04:18",
                    "videoUrl" to "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    "viewCount" to 28400L,
                    "featured" to true,
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )
            batch.set(
                db.collection("banners").document("banner_partner"),
                mapOf(
                    "title" to "Free Public Sports Coverage Across Niger",
                    "subtitle" to "Supported by community partners to keep football & Kokowa streaming free for everyone.",
                    "sponsorName" to "SPORT NIGER Partners",
                    "active" to true,
                    "visibility" to "PUBLIC",
                    "createdAt" to ts,
                    "updatedAt" to ts
                )
            )

            batch.commit().await()
            recordAudit(actorRole, "SEED_CATALOG", "system", "official_catalog", "Initialized official SPORT NIGER catalog")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "seed_catalog")
            Result.failure(e)
        }
    }
}
