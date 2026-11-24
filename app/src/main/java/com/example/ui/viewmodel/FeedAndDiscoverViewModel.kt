package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SocialRepository
import com.example.data.VideoRepository
import com.example.data.extractFirestoreException
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.VideoComment
import com.example.model.VideoPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FeedAndDiscoverViewModel(
    private val videoRepo: VideoRepository,
    private val socialRepo: SocialRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())
    val likedIds: StateFlow<Set<String>> = _likedIds.asStateFlow()

    private val _activeCommentsVideo = MutableStateFlow<VideoPost?>(null)
    val activeCommentsVideo: StateFlow<VideoPost?> = _activeCommentsVideo.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedHashtag = MutableStateFlow<String?>(null)
    val selectedHashtag: StateFlow<String?> = _selectedHashtag.asStateFlow()

    val currentUserProfile: StateFlow<UserProfile?> = socialRepo.observeUserProfile(currentUserId)
        .catch { error ->
            val fs = extractFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "Profile stream warning", fs ?: error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val followingIds: StateFlow<Set<String>> = socialRepo.observeFollowingIds(currentUserId)
        .catch { error ->
            val fs = extractFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "Following stream warning", fs ?: error)
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val blockedIds: StateFlow<Set<String>> = socialRepo.observeBlockedUsers(currentUserId)
        .map { list -> list.map { it.blockedUid }.toSet() }
        .catch { error ->
            val fs = extractFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "Blocked users warning", fs ?: error)
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val publicFeedState: StateFlow<UiState<List<VideoPost>>> = combine(
        videoRepo.observePublicFeed(30L),
        blockedIds
    ) { videos, blocked ->
        videos.filter { it.userId !in blocked }
    }
        .map<List<VideoPost>, UiState<List<VideoPost>>> { videos ->
            if (videos.isNotEmpty()) {
                refreshLikedIds(videos.map { it.id })
            }
            UiState.Success(videos)
        }
        .catch { error ->
            val fsError = extractFirestoreException(error)
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing public feed", root)
            emit(UiState.Error(root.message ?: "Failed to load video feed"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activeCreators: StateFlow<List<UserProfile>> = combine(
        socialRepo.observeActiveCreators(),
        blockedIds
    ) { creators, blocked ->
        creators.filter { it.userId !in blocked }
    }
        .catch { error ->
            val fs = extractFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "Creators stream warning", fs ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val commentsState: StateFlow<UiState<List<VideoComment>>> = _activeCommentsVideo
        .flatMapLatest { video ->
            if (video == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                videoRepo.observeComments(video.id)
                    .map<List<VideoComment>, UiState<List<VideoComment>>> { UiState.Success(it) }
                    .catch { error ->
                        val fs = extractFirestoreException(error)
                        if (fs == null && error is CancellationException) throw error
                        emit(UiState.Error((fs ?: error).message ?: "Failed to load comments"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    private fun refreshLikedIds(videoIds: List<String>) {
        viewModelScope.launch {
            val liked = videoRepo.getLikedVideoIds(videoIds, currentUserId)
            _likedIds.value = liked
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectHashtag(tag: String?) {
        _selectedHashtag.value = if (_selectedHashtag.value == tag) null else tag
    }

    fun openComments(video: VideoPost) {
        _activeCommentsVideo.value = video
    }

    fun closeComments() {
        _activeCommentsVideo.value = null
    }

    fun toggleLike(video: VideoPost) {
        val username = currentUserProfile.value?.username ?: "creator"
        val currentlyLiked = video.id in _likedIds.value
        _likedIds.value = if (currentlyLiked) _likedIds.value - video.id else _likedIds.value + video.id
        viewModelScope.launch {
            val result = videoRepo.toggleLike(video, username)
            result.onFailure {
                _likedIds.value = if (currentlyLiked) _likedIds.value + video.id else _likedIds.value - video.id
                _statusMessage.value = it.message ?: "Could not update like"
            }
        }
    }

    fun addComment(video: VideoPost, text: String, parentCommentId: String?) {
        val profile = currentUserProfile.value
        val username = profile?.username ?: "creator"
        val avatarUrl = profile?.avatarUrl.orEmpty()
        viewModelScope.launch {
            videoRepo.addComment(video, text, parentCommentId, username, avatarUrl)
                .onFailure { _statusMessage.value = it.message ?: "Could not post comment" }
        }
    }

    fun toggleFollowCreator(creatorId: String, creatorUsername: String) {
        if (creatorId == currentUserId) return
        val myUsername = currentUserProfile.value?.username ?: "creator"
        val target = activeCreators.value.firstOrNull { it.userId == creatorId }
            ?: UserProfile(userId = creatorId, username = creatorUsername, displayName = creatorUsername)
        viewModelScope.launch {
            socialRepo.toggleFollow(target, myUsername)
                .onSuccess { followed ->
                    _statusMessage.value = if (followed) "Following @$creatorUsername" else "Unfollowed @$creatorUsername"
                }
                .onFailure { _statusMessage.value = it.message ?: "Could not update follow status" }
        }
    }

    fun recordShare(videoId: String) {
        viewModelScope.launch {
            videoRepo.incrementShareCount(videoId)
        }
    }

    fun recordView(videoId: String) {
        viewModelScope.launch {
            videoRepo.incrementViewCount(videoId)
        }
    }

    fun blockUser(targetUserId: String, targetUsername: String) {
        viewModelScope.launch {
            socialRepo.blockUser(targetUserId, targetUsername)
                .onSuccess { _statusMessage.value = "Blocked @$targetUsername" }
                .onFailure { _statusMessage.value = it.message ?: "Could not block user" }
        }
    }

    fun submitReport(targetType: String, targetId: String, reason: String, details: String?) {
        viewModelScope.launch {
            socialRepo.submitReport(targetType, targetId, reason, details)
                .onSuccess { _statusMessage.value = "Report submitted for review. Thank you." }
                .onFailure { _statusMessage.value = it.message ?: "Could not submit report" }
        }
    }

    fun seedFeaturedReels() {
        val profile = currentUserProfile.value
        val username = profile?.username ?: "salman_creator"
        val avatar = profile?.avatarUrl.orEmpty()
        viewModelScope.launch {
            videoRepo.seedStarterReels(username, avatar)
                .onSuccess { count -> _statusMessage.value = "Loaded $count featured reels into Salman Video!" }
                .onFailure { _statusMessage.value = it.message ?: "Failed to load featured reels" }
        }
    }

    private companion object {
        const val TAG = "FeedDiscoverVM"
    }
}
