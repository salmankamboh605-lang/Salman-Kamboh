package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AdminRepository
import com.example.data.SocialRepository
import com.example.data.VideoRepository
import com.example.data.extractFirestoreException
import com.example.model.BlockedUser
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.ModerationReport
import com.example.model.UiState
import com.example.model.UserNotification
import com.example.model.UserProfile
import com.example.model.VideoPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SocialAndAdminViewModel(
    private val socialRepo: SocialRepository,
    private val videoRepo: VideoRepository,
    private val adminRepo: AdminRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

    private val _viewedCreatorId = MutableStateFlow<String?>(null)
    val viewedCreatorId: StateFlow<String?> = _viewedCreatorId.asStateFlow()

    init {
        viewModelScope.launch {
            _isAdmin.value = adminRepo.isCurrentUserAdmin()
        }
    }

    val currentUserProfile: StateFlow<UiState<UserProfile?>> = socialRepo.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fs = extractFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(UiState.Error((fs ?: error).message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val myVideos: StateFlow<List<VideoPost>> = videoRepo.observeCreatorVideos(currentUserId)
        .catch { error ->
            val fs = extractFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            Log.w(TAG, "My videos stream error", fs ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val viewedCreatorProfile: StateFlow<UserProfile?> = _viewedCreatorId
        .flatMapLatest { creatorId ->
            if (creatorId.isNullOrBlank()) flowOf(null)
            else socialRepo.observeUserProfile(creatorId).catch { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val viewedCreatorVideos: StateFlow<List<VideoPost>> = _viewedCreatorId
        .flatMapLatest { creatorId ->
            if (creatorId.isNullOrBlank()) flowOf(emptyList())
            else videoRepo.observeCreatorVideos(creatorId).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notificationsState: StateFlow<UiState<List<UserNotification>>> = socialRepo.observeNotifications(currentUserId)
        .map<List<UserNotification>, UiState<List<UserNotification>>> { UiState.Success(it) }
        .catch { error ->
            val fs = extractFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(UiState.Error((fs ?: error).message ?: "Failed to load notifications"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val conversationsState: StateFlow<UiState<List<Conversation>>> = socialRepo.observeConversations(currentUserId)
        .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
        .catch { error ->
            val fs = extractFirestoreException(error)
            if (fs == null && error is CancellationException) throw error
            emit(UiState.Error((fs ?: error).message ?: "Failed to load messages"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activeMessages: StateFlow<List<ChatMessage>> = _activeConversation
        .flatMapLatest { conv ->
            if (conv == null) flowOf(emptyList())
            else socialRepo.observeMessages(conv.id, currentUserId)
                .catch { error ->
                    val fs = extractFirestoreException(error)
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val blockedUsers: StateFlow<List<BlockedUser>> = socialRepo.observeBlockedUsers(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeCreators: StateFlow<List<UserProfile>> = socialRepo.observeActiveCreators()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val followingIds: StateFlow<Set<String>> = socialRepo.observeFollowingIds(currentUserId)
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val adminReports: StateFlow<List<ModerationReport>> = _isAdmin
        .flatMapLatest { allowed ->
            if (!allowed) flowOf(emptyList())
            else adminRepo.observeAllReports().catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminUsers: StateFlow<List<UserProfile>> = _isAdmin
        .flatMapLatest { allowed ->
            if (!allowed) flowOf(emptyList())
            else adminRepo.observeAllUsers().catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminVideos: StateFlow<List<VideoPost>> = _isAdmin
        .flatMapLatest { allowed ->
            if (!allowed) flowOf(emptyList())
            else adminRepo.observeAllVideos().catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun openCreatorProfile(userId: String) {
        _viewedCreatorId.value = userId
    }

    fun closeCreatorProfile() {
        _viewedCreatorId.value = null
    }

    fun openConversation(conversation: Conversation) {
        _activeConversation.value = conversation
    }

    fun startConversationWith(targetUser: UserProfile) {
        if (targetUser.userId == currentUserId) return
        val myProfile = (currentUserProfile.value as? UiState.Success)?.data
        val myName = myProfile?.username ?: "creator"
        val sortedIds = listOf(currentUserId, targetUser.userId).sorted()
        val convId = "${sortedIds[0]}_${sortedIds[1]}"
        val existing = (conversationsState.value as? UiState.Success)?.data?.firstOrNull { it.id == convId }
        _activeConversation.value = existing ?: Conversation(
            id = convId,
            participantIds = sortedIds,
            participantNames = mapOf(currentUserId to myName, targetUser.userId to targetUser.username)
        )
    }

    fun closeConversation() {
        _activeConversation.value = null
    }

    fun sendChatMessage(otherUser: UserProfile, text: String) {
        val myProfile = (currentUserProfile.value as? UiState.Success)?.data
        val myName = myProfile?.username ?: "creator"
        viewModelScope.launch {
            socialRepo.sendMessage(otherUser, myName, text)
                .onFailure { _statusMessage.value = it.message ?: "Failed to send message" }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            socialRepo.markNotificationRead(currentUserId, notificationId)
        }
    }

    fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String?,
        privateAccount: Boolean
    ) {
        viewModelScope.launch {
            socialRepo.updateProfile(currentUserId, username, displayName, bio, avatarUrl, privateAccount)
                .onSuccess { _statusMessage.value = "Profile updated!" }
                .onFailure { _statusMessage.value = it.message ?: "Failed to update profile" }
        }
    }

    fun deleteMyVideo(videoId: String) {
        viewModelScope.launch {
            videoRepo.deleteVideo(videoId)
                .onSuccess { _statusMessage.value = "Video deleted" }
                .onFailure { _statusMessage.value = it.message ?: "Failed to delete video" }
        }
    }

    fun toggleFollow(targetUser: UserProfile) {
        val myProfile = (currentUserProfile.value as? UiState.Success)?.data
        val myName = myProfile?.username ?: "creator"
        viewModelScope.launch {
            socialRepo.toggleFollow(targetUser, myName)
                .onFailure { _statusMessage.value = it.message ?: "Failed to update follow" }
        }
    }

    fun unblockUser(blockedUid: String) {
        viewModelScope.launch {
            socialRepo.unblockUser(blockedUid)
                .onSuccess { _statusMessage.value = "User unblocked" }
                .onFailure { _statusMessage.value = it.message ?: "Failed to unblock user" }
        }
    }

    fun updateReportStatus(reportId: String, status: String) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            adminRepo.updateReportStatus(reportId, status)
                .onSuccess { _statusMessage.value = "Report marked $status" }
                .onFailure { _statusMessage.value = it.message ?: "Admin update failed" }
        }
    }

    fun moderateVideo(videoId: String, status: String) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            adminRepo.setVideoModerationStatus(videoId, status)
                .onSuccess { _statusMessage.value = "Video set to $status" }
                .onFailure { _statusMessage.value = it.message ?: "Moderation update failed" }
        }
    }

    fun setUserAccountStatus(userId: String, status: String) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            adminRepo.setUserAccountStatus(userId, status)
                .onSuccess { _statusMessage.value = "Account status set to $status" }
                .onFailure { _statusMessage.value = it.message ?: "Account update failed" }
        }
    }

    private companion object {
        const val TAG = "SocialAdminVM"
    }
}
