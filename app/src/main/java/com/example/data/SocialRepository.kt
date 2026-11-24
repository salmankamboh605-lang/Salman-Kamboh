package com.example.data

import android.content.Context
import com.example.R
import com.example.model.BlockedUser
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.FollowEdge
import com.example.model.UserNotification
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SocialRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    suspend fun ensureUserProfile(user: FirebaseUser): Result<UserProfile> {
        val uid = user.uid
        val userPath = "users/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val snap = userRef.get().await()
            if (!snap.exists()) {
                val baseName = (user.displayName ?: user.email?.substringBefore("@") ?: "creator")
                    .lowercase()
                    .replace(Regex("[^a-z0-9_.]"), "")
                    .take(18)
                    .ifBlank { "creator" }
                val uniqueHandle = "${baseName}_${uid.take(4).lowercase()}"
                    .take(30)
                    .let { if (it.length < 3) "sv_$it" else it }
                val displayName = (user.displayName ?: "Salman Creator").trim().take(50).ifBlank { "Salman Creator" }
                val avatarUrl = user.photoUrl?.toString()?.take(1000).orEmpty()

                val profileMap = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "username" to uniqueHandle,
                    "displayName" to displayName,
                    "bio" to "Creating short videos on Salman Video",
                    "followersCount" to 0L,
                    "followingCount" to 0L,
                    "totalLikes" to 0L,
                    "privateAccount" to false,
                    "accountStatus" to "active",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (avatarUrl.isNotBlank()) {
                    profileMap["avatarUrl"] = avatarUrl
                }
                userRef.set(profileMap).await()
            }

            val privateRef = userRef.collection("private").document("info")
            val privateMap = mutableMapOf<String, Any>(
                "userId" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            user.email?.take(200)?.takeIf { it.isNotBlank() }?.let { privateMap["email"] = it }
            privateRef.set(privateMap, SetOptions.merge()).await()

            val loaded = userRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(userId = uid, username = "creator_${uid.take(4)}", displayName = "Salman Creator")
            Result.success(loaded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userPath)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank." }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(fsError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeActiveCreators(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection("users")
                .whereEqualTo("accountStatus", "active")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.followersCount }
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateProfile(
        userId: String,
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String?,
        privateAccount: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user profile update." }
            val cleanUsername = username.trim().lowercase().replace(Regex("[^a-z0-9_.]"), "").take(30)
            require(cleanUsername.length >= 3) { "Username must be at least 3 characters." }
            val cleanDisplay = displayName.trim().take(50)
            require(cleanDisplay.isNotEmpty()) { "Display name cannot be empty." }
            val cleanBio = bio.trim().take(200)

            val updates = mutableMapOf<String, Any>(
                "username" to cleanUsername,
                "displayName" to cleanDisplay,
                "bio" to cleanBio,
                "privateAccount" to privateAccount,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarUrl.isNullOrBlank()) {
                updates["avatarUrl"] = avatarUrl.trim().take(1000)
            }
            db.collection("users").document(userId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeFollowingIds(userId: String): Flow<Set<String>> = flow {
        val uid = requireUserId()
        val path = "follows"
        emitAll(
            db.collection("follows")
                .whereEqualTo("followerId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FollowEdge::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .map { it.followingId }
                        .toSet()
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun toggleFollow(targetUser: UserProfile, currentUsername: String): Result<Boolean> {
        val targetId = targetUser.userId
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in"))
        if (uid == targetId) return Result.failure(IllegalArgumentException("You cannot follow yourself."))
        val edgeId = "${uid}_$targetId"
        val path = "follows/$edgeId"
        return try {
            val edgeRef = db.collection("follows").document(edgeId)
            val exists = edgeRef.get().await().exists()
            val batch = db.batch()
            if (exists) {
                batch.delete(edgeRef)
                if (targetUser.followersCount > 0L) {
                    batch.update(
                        db.collection("users").document(targetId),
                        mapOf(
                            "followersCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.update(
                    db.collection("users").document(uid),
                    mapOf(
                        "followingCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
                Result.success(false)
            } else {
                val followPayload = mapOf(
                    "followerId" to uid,
                    "followingId" to targetId,
                    "followerUsername" to currentUsername.take(30).ifBlank { "creator" },
                    "followingUsername" to targetUser.username.take(30).ifBlank { "creator" },
                    "createdAt" to FieldValue.serverTimestamp()
                )
                batch.set(edgeRef, followPayload)
                batch.update(
                    db.collection("users").document(targetId),
                    mapOf(
                        "followersCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    db.collection("users").document(uid),
                    mapOf(
                        "followingCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val notifId = "follow_${uid}_${System.currentTimeMillis()}"
                batch.set(
                    db.collection("users").document(targetId).collection("notifications").document(notifId),
                    mapOf(
                        "recipientId" to targetId,
                        "actorId" to uid,
                        "actorUsername" to currentUsername.take(30).ifBlank { "creator" },
                        "type" to "FOLLOW",
                        "message" to "@${currentUsername.take(30)} started following you",
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeNotifications(userId: String): Flow<List<UserNotification>> = flow {
        val uid = requireUserId()
        val path = "users/$userId/notifications"
        emitAll(
            db.collection("users").document(userId).collection("notifications")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun markNotificationRead(userId: String, notificationId: String): Result<Unit> {
        val path = "users/$userId/notifications/$notificationId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("users").document(userId)
                .collection("notifications").document(notificationId)
                .update("read", true)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        val path = "conversations"
        emitAll(
            db.collection("conversations")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(conversationId: String, userId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations").document(conversationId).collection("messages")
                .whereArrayContains("participantIds", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendMessage(
        otherUser: UserProfile,
        currentUsername: String,
        text: String
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in"))
        val cleanText = text.trim().take(1000)
        if (cleanText.isEmpty()) return Result.failure(IllegalArgumentException("Message cannot be empty"))

        val sortedIds = listOf(uid, otherUser.userId).sorted()
        val conversationId = "${sortedIds[0]}_${sortedIds[1]}"
        val path = "conversations/$conversationId"
        return try {
            val convRef = db.collection("conversations").document(conversationId)
            val convSnap = convRef.get().await()
            val namesMap = mapOf(
                uid to currentUsername.take(30).ifBlank { "creator" },
                otherUser.userId to otherUser.username.take(30).ifBlank { "creator" }
            )
            if (!convSnap.exists()) {
                convRef.set(
                    mapOf(
                        "participantIds" to sortedIds,
                        "participantNames" to namesMap,
                        "lastMessage" to cleanText,
                        "lastSenderId" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                convRef.update(
                    mapOf(
                        "participantNames" to namesMap,
                        "lastMessage" to cleanText,
                        "lastSenderId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            convRef.collection("messages").document(msgId).set(
                mapOf(
                    "conversationId" to conversationId,
                    "senderId" to uid,
                    "participantIds" to sortedIds,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val notifId = "msg_${uid}_${System.currentTimeMillis()}"
            db.collection("users").document(otherUser.userId)
                .collection("notifications").document(notifId)
                .set(
                    mapOf(
                        "recipientId" to otherUser.userId,
                        "actorId" to uid,
                        "actorUsername" to currentUsername.take(30).ifBlank { "creator" },
                        "type" to "MESSAGE",
                        "message" to "@${currentUsername.take(30)}: ${cleanText.take(120)}",
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            Result.success(conversationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeBlockedUsers(userId: String): Flow<List<BlockedUser>> = flow {
        val uid = requireUserId()
        val path = "users/$userId/blockedUsers"
        emitAll(
            db.collection("users").document(userId).collection("blockedUsers")
                .snapshots()
                .map { snap ->
                    snap.toObjects(BlockedUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun blockUser(targetUserId: String, targetUsername: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in"))
        if (uid == targetUserId) return Result.failure(IllegalArgumentException("Cannot block yourself."))
        val path = "users/$uid/blockedUsers/$targetUserId"
        return try {
            db.collection("users").document(uid)
                .collection("blockedUsers").document(targetUserId)
                .set(
                    mapOf(
                        "userId" to uid,
                        "blockedUid" to targetUserId,
                        "blockedUsername" to targetUsername.take(30).ifBlank { "user" },
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in"))
        val path = "users/$uid/blockedUsers/$targetUserId"
        return try {
            db.collection("users").document(uid)
                .collection("blockedUsers").document(targetUserId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        reason: String,
        details: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in"))
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "reports/$reportId"
        return try {
            val payload = mutableMapOf<String, Any>(
                "reporterId" to uid,
                "targetType" to targetType,
                "targetId" to targetId.take(128),
                "reason" to reason.trim().take(200).ifBlank { "Inappropriate content" },
                "status" to "OPEN",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            details?.trim()?.take(500)?.takeIf { it.isNotBlank() }?.let {
                payload["details"] = it
            }
            db.collection("reports").document(reportId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
