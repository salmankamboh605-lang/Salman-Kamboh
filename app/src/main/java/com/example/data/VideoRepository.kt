package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.model.AudioTrackOption
import com.example.model.VideoComment
import com.example.model.VideoFilterPreset
import com.example.model.VideoPost
import com.example.model.VideoPrivacy
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class PublishVideoResult(
    val videoId: String,
    val uploadedToCloudStorage: Boolean,
    val storageNote: String? = null
)

class VideoRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observePublicFeed(limitCount: Long = 25L): Flow<List<VideoPost>> = flow {
        val uid = requireUserId()
        val path = "videos"
        emitAll(
            db.collection("videos")
                .whereEqualTo("privacy", "PUBLIC")
                .whereEqualTo("moderationStatus", "APPROVED")
                .limit(limitCount)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    fun observeCreatorVideos(creatorId: String): Flow<List<VideoPost>> = flow {
        val uid = requireUserId()
        val path = "videos"
        val query = if (creatorId == uid) {
            db.collection("videos").whereEqualTo("userId", creatorId)
        } else {
            db.collection("videos")
                .whereEqualTo("userId", creatorId)
                .whereEqualTo("privacy", "PUBLIC")
                .whereEqualTo("moderationStatus", "APPROVED")
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun getLikedVideoIds(videoIds: List<String>, userId: String): Set<String> {
        if (videoIds.isEmpty() || userId.isBlank()) return emptySet()
        val liked = mutableSetOf<String>()
        for (vid in videoIds.take(30)) {
            try {
                val doc = db.collection("videos").document(vid)
                    .collection("likes").document(userId)
                    .get().await()
                if (doc.exists()) liked.add(vid)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore individual check error
            }
        }
        return liked
    }

    fun observeComments(videoId: String): Flow<List<VideoComment>> = flow {
        val uid = requireUserId()
        val path = "videos/$videoId/comments"
        emitAll(
            db.collection("videos").document(videoId).collection("comments")
                .whereEqualTo("videoId", videoId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VideoComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun toggleLike(video: VideoPost, currentUsername: String): Result<Boolean> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in"))
        val videoId = video.id
        val path = "videos/$videoId/likes/$uid"
        return try {
            val likeRef = db.collection("videos").document(videoId).collection("likes").document(uid)
            val videoRef = db.collection("videos").document(videoId)
            val alreadyLiked = likeRef.get().await().exists()
            val batch = db.batch()
            if (alreadyLiked) {
                batch.delete(likeRef)
                if (video.likesCount > 0L) {
                    batch.update(
                        videoRef,
                        mapOf(
                            "likesCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
                Result.success(false)
            } else {
                batch.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "videoId" to videoId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    videoRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (video.userId != uid) {
                    batch.update(
                        db.collection("users").document(video.userId),
                        mapOf(
                            "totalLikes" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    val notifId = "like_${uid}_${videoId}"
                    batch.set(
                        db.collection("users").document(video.userId)
                            .collection("notifications").document(notifId),
                        mapOf(
                            "recipientId" to video.userId,
                            "actorId" to uid,
                            "actorUsername" to currentUsername.take(30).ifBlank { "creator" },
                            "type" to "LIKE",
                            "videoId" to videoId,
                            "message" to "@${currentUsername.take(30)} liked your video \"${video.caption.take(50)}\"",
                            "read" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
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

    suspend fun addComment(
        video: VideoPost,
        text: String,
        parentCommentId: String?,
        currentUsername: String,
        currentAvatarUrl: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in"))
        val cleanText = text.trim().take(300)
        if (cleanText.isEmpty()) return Result.failure(IllegalArgumentException("Comment cannot be empty"))

        val commentId = "cmt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "videos/${video.id}/comments/$commentId"
        return try {
            val batch = db.batch()
            val commentRef = db.collection("videos").document(video.id)
                .collection("comments").document(commentId)
            val commentPayload = mutableMapOf<String, Any>(
                "videoId" to video.id,
                "userId" to uid,
                "username" to currentUsername.take(30).ifBlank { "creator" },
                "text" to cleanText,
                "likesCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (currentAvatarUrl.isNotBlank()) {
                commentPayload["avatarUrl"] = currentAvatarUrl.take(1000)
            }
            if (!parentCommentId.isNullOrBlank()) {
                commentPayload["parentCommentId"] = parentCommentId.take(128)
            }
            batch.set(commentRef, commentPayload)
            batch.update(
                db.collection("videos").document(video.id),
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (video.userId != uid) {
                val notifId = "cmt_${uid}_${System.currentTimeMillis()}"
                batch.set(
                    db.collection("users").document(video.userId)
                        .collection("notifications").document(notifId),
                    mapOf(
                        "recipientId" to video.userId,
                        "actorId" to uid,
                        "actorUsername" to currentUsername.take(30).ifBlank { "creator" },
                        "type" to "COMMENT",
                        "videoId" to video.id,
                        "message" to "@${currentUsername.take(30)} commented: ${cleanText.take(80)}",
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun incrementShareCount(videoId: String): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            requireUserId()
            db.collection("videos").document(videoId)
                .update(
                    mapOf(
                        "sharesCount" to FieldValue.increment(1L),
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

    suspend fun incrementViewCount(videoId: String): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            requireUserId()
            db.collection("videos").document(videoId)
                .update(
                    mapOf(
                        "viewsCount" to FieldValue.increment(1L),
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

    suspend fun publishVideo(
        localVideoUri: Uri,
        thumbnailUri: Uri?,
        creatorUsername: String,
        creatorAvatarUrl: String,
        caption: String,
        description: String,
        hashtagsRaw: String,
        audioTrack: AudioTrackOption,
        filterPreset: VideoFilterPreset,
        overlayText: String,
        trimStartMs: Long,
        trimEndMs: Long,
        volumeLevel: Float,
        privacy: VideoPrivacy,
        onUploadProgress: (Float) -> Unit
    ): Result<PublishVideoResult> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in"))
        val cleanCaption = caption.trim().take(200)
        if (cleanCaption.isEmpty()) return Result.failure(IllegalArgumentException("Caption is required"))

        val videoId = "vid_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "videos/$videoId"

        return try {
            var resolvedVideoUrl = localVideoUri.toString().take(1500)
            var resolvedThumbUrl = thumbnailUri?.toString()?.take(1500).orEmpty()
            var uploadedCloud = false
            var storageNote: String? = null

            val isRemoteUrl = resolvedVideoUrl.startsWith("http://") || resolvedVideoUrl.startsWith("https://")
            if (!isRemoteUrl) {
                try {
                    val videoRef = storage.reference.child("videos/$uid/$videoId.mp4")
                    val uploadTask = videoRef.putFile(localVideoUri)
                    uploadTask.addOnProgressListener { snap ->
                        if (snap.totalByteCount > 0) {
                            onUploadProgress((snap.bytesTransferred.toFloat() / snap.totalByteCount).coerceIn(0f, 0.9f))
                        }
                    }
                    uploadTask.await()
                    resolvedVideoUrl = videoRef.downloadUrl.await().toString().take(1500)
                    uploadedCloud = true

                    if (thumbnailUri != null) {
                        val thumbRef = storage.reference.child("thumbnails/$uid/$videoId.jpg")
                        thumbRef.putFile(thumbnailUri).await()
                        resolvedThumbUrl = thumbRef.downloadUrl.await().toString().take(1500)
                    }
                } catch (storageError: Exception) {
                    if (storageError is CancellationException) throw storageError
                    Log.w("VideoRepository", "Firebase Storage upload skipped: ${storageError.message}")
                    storageNote = "Published with local media URI (enable Firebase Storage bucket in Firebase Console for cloud media hosting)."
                }
            } else {
                uploadedCloud = true
            }

            onUploadProgress(0.95f)
            val hashtags = hashtagsRaw
                .split(Regex("[\\s,]+"))
                .map { it.trim().removePrefix("#").lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(10)
                .map { it.take(40) }

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "creatorUsername" to creatorUsername.take(30).ifBlank { "creator" },
                "caption" to cleanCaption,
                "hashtags" to hashtags,
                "videoUrl" to resolvedVideoUrl,
                "audioTrackTitle" to audioTrack.title.take(100).ifBlank { "Original Sound" },
                "audioArtist" to audioTrack.artist.take(80).ifBlank { "Creator Audio" },
                "filterPreset" to filterPreset.name,
                "trimStartMs" to trimStartMs.coerceAtLeast(0L),
                "trimEndMs" to trimEndMs.coerceAtLeast(0L),
                "volumeLevel" to volumeLevel.coerceIn(0f, 1f).toDouble(),
                "privacy" to privacy.name,
                "moderationStatus" to "APPROVED",
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "sharesCount" to 0L,
                "viewsCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (creatorAvatarUrl.isNotBlank()) payload["creatorAvatarUrl"] = creatorAvatarUrl.take(1000)
            if (description.isNotBlank()) payload["description"] = description.trim().take(500)
            if (resolvedThumbUrl.isNotBlank()) payload["thumbnailUrl"] = resolvedThumbUrl
            if (audioTrack.url.isNotBlank()) payload["audioUrl"] = audioTrack.url.take(1000)
            if (overlayText.isNotBlank()) payload["overlayText"] = overlayText.trim().take(80)

            db.collection("videos").document(videoId).set(payload).await()
            onUploadProgress(1f)
            Result.success(PublishVideoResult(videoId, uploadedCloud, storageNote))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVideo(videoId: String): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            requireUserId()
            db.collection("videos").document(videoId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun seedStarterReels(creatorUsername: String, creatorAvatarUrl: String): Result<Int> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in"))
        val samples = listOf(
            Triple(
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                "Neon cinema vibes on Salman Video",
                listOf("cinema", "neon", "salmanvideo")
            ),
            Triple(
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                "Golden hour mountain escape reel",
                listOf("travel", "goldenhour", "explore")
            ),
            Triple(
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoy.mp4",
                "High-energy street studio cut",
                listOf("streetstyle", "edit", "trending")
            )
        )
        return try {
            val batch = db.batch()
            samples.forEachIndexed { idx, (url, caption, tags) ->
                val vidId = "reel_${uid.take(6)}_${System.currentTimeMillis()}_$idx"
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "creatorUsername" to creatorUsername.take(30).ifBlank { "salman_creator" },
                    "caption" to caption,
                    "description" to "Featured HD reel powered by Media3 ExoPlayer & Cloud Firestore.",
                    "hashtags" to tags,
                    "videoUrl" to url,
                    "audioTrackTitle" to if (idx == 0) "Midnight Pulse" else "Neon Drift Synth",
                    "audioArtist" to "SoundHelix Studio (CC)",
                    "filterPreset" to if (idx == 0) VideoFilterPreset.NEON_CYBER.name else VideoFilterPreset.VIVID_POP.name,
                    "trimStartMs" to 0L,
                    "trimEndMs" to 0L,
                    "volumeLevel" to 1.0,
                    "privacy" to VideoPrivacy.PUBLIC.name,
                    "moderationStatus" to "APPROVED",
                    "likesCount" to 0L,
                    "commentsCount" to 0L,
                    "sharesCount" to 0L,
                    "viewsCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (creatorAvatarUrl.isNotBlank()) payload["creatorAvatarUrl"] = creatorAvatarUrl.take(1000)
                batch.set(db.collection("videos").document(vidId), payload)
            }
            batch.commit().await()
            Result.success(samples.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "videos")
            Result.failure(e)
        }
    }
}
