package com.example.data

import android.content.Context
import com.example.R
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ReadingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val circleId = AuthorizedReaders.SHARED_CIRCLE_ID

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    // --- REALTIME OBSERVERS ---

    fun observeUserAccount(userId: String): Flow<UserAccount?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeCircle(userId: String): Flow<ReadingCircle?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "circles/$circleId"
        emitAll(
            db.collection("circles").document(circleId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(ReadingCircle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    // Parent-gated single-document get observer (never uses list on readers)
    fun observeReaderProfile(userId: String, readerId: String): Flow<ReaderProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "circles/$circleId/readers/$readerId"
        emitAll(
            db.collection("circles").document(circleId)
                .collection("readers").document(readerId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(ReaderProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    // Rule-aligned list query using whereArrayContains("memberUids", userId)
    fun observeSessions(userId: String): Flow<List<ReadingSession>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "circles/$circleId/sessions"
        emitAll(
            db.collection("circles").document(circleId)
                .collection("sessions")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReadingSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- CIRCLE & IDENTITY BOOTSTRAP ---

    suspend fun ensureCircleMembership(): Result<List<String>> {
        val circlePath = "circles/$circleId"
        return try {
            val uid = requireUserId()
            val circleRef = db.collection("circles").document(circleId)
            val snap = circleRef.get().await()

            val memberUids: List<String> = if (!snap.exists()) {
                val initialMembers = listOf(uid)
                val circlePayload = mapOf(
                    "name" to AuthorizedReaders.SHARED_CIRCLE_NAME,
                    "ownerId" to uid,
                    "memberUids" to initialMembers,
                    "dailyGoalMinutes" to AuthorizedReaders.DAILY_GOAL_MINUTES,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                circleRef.set(circlePayload).await()
                initialMembers
            } else {
                val existingMembers = (snap.get("memberUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                if (uid in existingMembers) {
                    existingMembers
                } else {
                    require(existingMembers.size < 4) {
                        "This two-person reading circle has reached its device membership limit."
                    }
                    val updatedMembers = existingMembers + uid
                    circleRef.update(
                        mapOf(
                            "memberUids" to updatedMembers,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    updatedMembers
                }
            }

            // Ensure both authorized reader profile slots exist so partner card is immediately ready
            for (reader in AuthorizedReaders.ALL) {
                val readerRef = circleRef.collection("readers").document(reader.readerId)
                val readerSnap = readerRef.get().await()
                if (!readerSnap.exists()) {
                    val todayKey = StreakCalculator.todayDateKey()
                    val readerPayload = mapOf(
                        "readerId" to reader.readerId,
                        "displayName" to reader.displayName,
                        "claimedByUid" to uid,
                        "currentStreak" to 0L,
                        "longestStreak" to 0L,
                        "todayMinutes" to 0L,
                        "todayDateKey" to todayKey,
                        "weeklyMinutes" to 0L,
                        "totalMinutes" to 0L,
                        "dailyTotals" to emptyMap<String, Long>(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    readerRef.set(readerPayload).await()
                }
            }

            Result.success(memberUids)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, circlePath)
            Result.failure(e)
        }
    }

    suspend fun saveUserAccount(
        readerId: String,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val verifiedUid = requireUserId()
            require(AuthorizedReaders.findByReaderId(readerId) != null) { "Unauthorized reader ID." }
            require(reminderHour in 0..23 && reminderMinute in 0..59) { "Invalid reminder time." }

            val userRef = db.collection("users").document(verifiedUid)
            val snap = userRef.get().await()
            if (!snap.exists()) {
                val createPayload = mapOf(
                    "userId" to verifiedUid,
                    "activeReaderId" to readerId,
                    "reminderEnabled" to reminderEnabled,
                    "reminderHour" to reminderHour,
                    "reminderMinute" to reminderMinute,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "activeReaderId" to readerId,
                    "reminderEnabled" to reminderEnabled,
                    "reminderHour" to reminderHour,
                    "reminderMinute" to reminderMinute,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateReminderPreferences(
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val verifiedUid = requireUserId()
            require(reminderHour in 0..23 && reminderMinute in 0..59) { "Invalid reminder time." }
            db.collection("users").document(verifiedUid).update(
                mapOf(
                    "reminderEnabled" to reminderEnabled,
                    "reminderHour" to reminderHour,
                    "reminderMinute" to reminderMinute,
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

    // --- SESSION LOGGING & STREAK SYNC ---

    suspend fun logReadingSession(
        reader: AuthorizedReader,
        minutes: Int,
        bookTitle: String?,
        note: String?,
        circleMemberUids: List<String>,
        existingProfile: ReaderProfile?
    ): Result<ComputedReaderStats> {
        val sessionsPath = "circles/$circleId/sessions"
        return try {
            val uid = requireUserId()
            require(minutes in 1..720) { "Reading duration must be between 1 and 720 minutes." }
            val trimmedBook = bookTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(100)
            val trimmedNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(200)

            val nowMillis = System.currentTimeMillis()
            val dateKey = StreakCalculator.todayDateKey(nowMillis)
            val timeLabel = StreakCalculator.currentTimeLabel(nowMillis)
            val effectiveMembers = (circleMemberUids + uid).distinct().take(4)

            val sessionRef = db.collection("circles").document(circleId)
                .collection("sessions").document()

            val rawSessionPayload = mutableMapOf<String, Any?>(
                "circleId" to circleId,
                "readerId" to reader.readerId,
                "readerName" to reader.displayName,
                "authorUid" to uid,
                "memberUids" to effectiveMembers,
                "dateKey" to dateKey,
                "minutes" to minutes,
                "loggedTimeLabel" to timeLabel,
                "bookTitle" to trimmedBook,
                "note" to trimmedNote,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanSessionPayload = rawSessionPayload.filterValues { it != null }
            sessionRef.set(cleanSessionPayload).await()

            // Recompute cumulative daily total & streaks and sync reader profile
            val updatedTotals = (existingProfile?.dailyTotals ?: emptyMap()).toMutableMap()
            val newTodayTotal = ((updatedTotals[dateKey] ?: 0L) + minutes.toLong()).coerceAtMost(1440L)
            updatedTotals[dateKey] = newTodayTotal
            val prunedTotals = StreakCalculator.pruneDailyTotals(updatedTotals)

            val tempProfile = (existingProfile ?: ReaderProfile(
                readerId = reader.readerId,
                displayName = reader.displayName
            )).copy(
                dailyTotals = prunedTotals,
                totalMinutes = (existingProfile?.totalMinutes ?: 0L) + minutes.toLong(),
                lastBookTitle = trimmedBook ?: existingProfile?.lastBookTitle
            )
            val stats = StreakCalculator.computeStats(reader, tempProfile, nowMillis = nowMillis)

            val readerRef = db.collection("circles").document(circleId)
                .collection("readers").document(reader.readerId)
            val readerSnap = readerRef.get().await()

            val updateMap = mutableMapOf<String, Any?>(
                "claimedByUid" to uid,
                "currentStreak" to stats.currentStreak,
                "longestStreak" to stats.longestStreak,
                "todayMinutes" to stats.todayMinutes,
                "todayDateKey" to dateKey,
                "weeklyMinutes" to stats.weeklyMinutes,
                "totalMinutes" to stats.totalMinutes,
                "lastBookTitle" to (trimmedBook ?: existingProfile?.lastBookTitle),
                "dailyTotals" to prunedTotals,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            if (!readerSnap.exists()) {
                val createMap = updateMap + mapOf(
                    "readerId" to reader.readerId,
                    "displayName" to reader.displayName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                readerRef.set(createMap).await()
            } else {
                readerRef.update(updateMap).await()
            }

            Result.success(stats)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, sessionsPath)
            Result.failure(e)
        }
    }

    suspend fun deleteReadingSession(
        session: ReadingSession,
        reader: AuthorizedReader,
        existingProfile: ReaderProfile?
    ): Result<ComputedReaderStats> {
        val sessionPath = "circles/$circleId/sessions/${session.id}"
        return try {
            val uid = requireUserId()
            require(session.id.isNotBlank()) { "Session ID is missing." }
            require(session.authorUid == uid) { "You can only delete sessions you logged." }

            db.collection("circles").document(circleId)
                .collection("sessions").document(session.id)
                .delete()
                .await()

            val nowMillis = System.currentTimeMillis()
            val todayKey = StreakCalculator.todayDateKey(nowMillis)
            val updatedTotals = (existingProfile?.dailyTotals ?: emptyMap()).toMutableMap()
            val reduced = ((updatedTotals[session.dateKey] ?: 0L) - session.minutes).coerceAtLeast(0L)
            if (reduced == 0L) {
                updatedTotals.remove(session.dateKey)
            } else {
                updatedTotals[session.dateKey] = reduced
            }
            val prunedTotals = StreakCalculator.pruneDailyTotals(updatedTotals)
            val newTotal = ((existingProfile?.totalMinutes ?: 0L) - session.minutes).coerceAtLeast(0L)

            val tempProfile = (existingProfile ?: ReaderProfile(
                readerId = reader.readerId,
                displayName = reader.displayName
            )).copy(
                dailyTotals = prunedTotals,
                totalMinutes = newTotal
            )
            val stats = StreakCalculator.computeStats(reader, tempProfile, nowMillis = nowMillis)

            val readerRef = db.collection("circles").document(circleId)
                .collection("readers").document(reader.readerId)

            val updateMap = mutableMapOf<String, Any?>(
                "claimedByUid" to uid,
                "currentStreak" to stats.currentStreak,
                "longestStreak" to stats.longestStreak,
                "todayMinutes" to stats.todayMinutes,
                "todayDateKey" to todayKey,
                "weeklyMinutes" to stats.weeklyMinutes,
                "totalMinutes" to stats.totalMinutes,
                "dailyTotals" to prunedTotals,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            readerRef.update(updateMap).await()
            Result.success(stats)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, sessionPath)
            Result.failure(e)
        }
    }

    // Backfills newly joined partner UID into current user's existing sessions so partner sees full history
    suspend fun syncSessionMemberUids(
        sessions: List<ReadingSession>,
        circleMemberUids: List<String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (circleMemberUids.size <= 1) return Result.success(Unit)
        val path = "circles/$circleId/sessions"
        return try {
            val staleSessions = sessions.filter { s ->
                s.id.isNotBlank() &&
                    s.authorUid == uid &&
                    !s.memberUids.containsAll(circleMemberUids)
            }
            for (session in staleSessions.take(20)) {
                val mergedMembers = (session.memberUids + circleMemberUids).distinct().take(4)
                db.collection("circles").document(circleId)
                    .collection("sessions").document(session.id)
                    .update(
                        mapOf(
                            "memberUids" to mergedMembers,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
