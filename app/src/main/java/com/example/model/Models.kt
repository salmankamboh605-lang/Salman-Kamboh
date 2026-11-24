package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class VideoFilterPreset(val label: String) {
    ORIGINAL("Original"),
    NEON_CYBER("Neon Cyber"),
    NOIR_MONO("Noir Mono"),
    WARM_FILM("Warm Film"),
    VIVID_POP("Vivid Pop");

    companion object {
        fun fromRaw(raw: String?): VideoFilterPreset =
            entries.firstOrNull { it.name == raw } ?: ORIGINAL
    }
}

enum class VideoPrivacy(val label: String) {
    PUBLIC("Public"),
    FOLLOWERS("Followers Only"),
    PRIVATE("Private");

    companion object {
        fun fromRaw(raw: String?): VideoPrivacy =
            entries.firstOrNull { it.name == raw } ?: PUBLIC
    }
}

data class AudioTrackOption(
    val id: String,
    val title: String,
    val artist: String,
    val url: String,
    val durationLabel: String
)

object RoyaltyFreeAudioCatalog {
    val tracks: List<AudioTrackOption> = listOf(
        AudioTrackOption(
            id = "original_sound",
            title = "Original Video Sound",
            artist = "Creator Audio",
            url = "",
            durationLabel = "Original"
        ),
        AudioTrackOption(
            id = "midnight_pulse",
            title = "Midnight Pulse",
            artist = "SoundHelix Studio (CC)",
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            durationLabel = "0:30"
        ),
        AudioTrackOption(
            id = "neon_drift",
            title = "Neon Drift Synth",
            artist = "SoundHelix Studio (CC)",
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            durationLabel = "0:30"
        ),
        AudioTrackOption(
            id = "urban_velvet",
            title = "Urban Velvet Groove",
            artist = "SoundHelix Studio (CC)",
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            durationLabel = "0:30"
        ),
        AudioTrackOption(
            id = "cyber_horizon",
            title = "Cyber Horizon Beat",
            artist = "SoundHelix Studio (CC)",
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            durationLabel = "0:30"
        )
    )
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val totalLikes: Long = 0L,
    val privateAccount: Boolean = false,
    val accountStatus: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoPost(
    @DocumentId val id: String = "",
    val userId: String = "",
    val creatorUsername: String = "",
    val creatorAvatarUrl: String = "",
    val caption: String = "",
    val description: String = "",
    val hashtags: List<String> = emptyList(),
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val audioTrackTitle: String = "Original Sound",
    val audioArtist: String = "Creator Audio",
    val audioUrl: String = "",
    val filterPreset: String = VideoFilterPreset.ORIGINAL.name,
    val overlayText: String = "",
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val volumeLevel: Double = 1.0,
    val privacy: String = VideoPrivacy.PUBLIC.name,
    val moderationStatus: String = "APPROVED",
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val sharesCount: Long = 0L,
    val viewsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoComment(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val text: String = "",
    val parentCommentId: String = "",
    val likesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FollowEdge(
    @DocumentId val id: String = "",
    val followerId: String = "",
    val followingId: String = "",
    val followerUsername: String = "",
    val followingUsername: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastSenderId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val participantIds: List<String> = emptyList(),
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserNotification(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val actorId: String = "",
    val actorUsername: String = "",
    val type: String = "LIKE",
    val videoId: String = "",
    val message: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val blockedUid: String = "",
    val blockedUsername: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ModerationReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "VIDEO",
    val targetId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "OPEN",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
