package com.example.data

import android.content.Context
import com.example.R
import com.example.model.ModerationReport
import com.example.model.UserProfile
import com.example.model.VideoPost
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    suspend fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.email == BOOTSTRAP_ADMIN_EMAIL && user.isEmailVerified) {
            return true
        }
        return try {
            db.collection("admins").document(user.uid).get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    fun observeAllReports(): Flow<List<ModerationReport>> = flow {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        emitAll(
            db.collection("reports").snapshots()
                .map { snap ->
                    snap.toObjects(ModerationReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, "reports")
                    }
                    throw error
                }
        )
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        emitAll(
            db.collection("users").snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, "users")
                    }
                    throw error
                }
        )
    }

    fun observeAllVideos(): Flow<List<VideoPost>> = flow {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        emitAll(
            db.collection("videos").snapshots()
                .map { snap ->
                    snap.toObjects(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsError = extractFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsError ?: error, OperationType.LIST, "videos")
                    }
                    throw error
                }
        )
    }

    suspend fun updateReportStatus(reportId: String, status: String): Result<Unit> {
        val path = "reports/$reportId"
        return try {
            db.collection("reports").document(reportId)
                .update(mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setVideoModerationStatus(videoId: String, moderationStatus: String): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            db.collection("videos").document(videoId)
                .update(mapOf("moderationStatus" to moderationStatus, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setUserAccountStatus(userId: String, accountStatus: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            db.collection("users").document(userId)
                .update(mapOf("accountStatus" to accountStatus, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    companion object {
        private const val BOOTSTRAP_ADMIN_EMAIL = "salmankamboh605@gmail.com"
    }
}
