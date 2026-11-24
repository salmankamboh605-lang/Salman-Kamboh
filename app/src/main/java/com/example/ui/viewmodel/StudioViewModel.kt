package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SocialRepository
import com.example.data.VideoRepository
import com.example.media.VideoEditorEngine
import com.example.model.AudioTrackOption
import com.example.model.RoyaltyFreeAudioCatalog
import com.example.model.VideoFilterPreset
import com.example.model.VideoPrivacy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudioUiState(
    val videoUri: Uri? = null,
    val thumbnailUri: Uri? = null,
    val durationMs: Long = 15_000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 15_000L,
    val filterPreset: VideoFilterPreset = VideoFilterPreset.ORIGINAL,
    val overlayText: String = "",
    val volumeLevel: Float = 1.0f,
    val selectedAudioTrack: AudioTrackOption = RoyaltyFreeAudioCatalog.tracks.first(),
    val caption: String = "",
    val description: String = "",
    val hashtags: String = "salmanvideo viral",
    val privacy: VideoPrivacy = VideoPrivacy.PUBLIC,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val isPublishing: Boolean = false,
    val uploadProgress: Float = 0f,
    val statusMessage: String? = null,
    val publishCompleted: Boolean = false
)

class StudioViewModel(
    private val videoRepo: VideoRepository,
    private val socialRepo: SocialRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    fun setVideoSource(uri: Uri, durationMs: Long = 15_000L) {
        val safeDuration = durationMs.coerceAtLeast(1_000L)
        _uiState.update {
            it.copy(
                videoUri = uri,
                durationMs = safeDuration,
                trimStartMs = 0L,
                trimEndMs = safeDuration,
                publishCompleted = false,
                statusMessage = null
            )
        }
    }

    fun clearVideoSource() {
        _uiState.value = StudioUiState()
    }

    fun setThumbnailUri(uri: Uri?) {
        _uiState.update { it.copy(thumbnailUri = uri) }
    }

    fun setTrimRange(startMs: Long, endMs: Long) {
        _uiState.update { state ->
            val clampedStart = startMs.coerceIn(0L, (state.durationMs - 500L).coerceAtLeast(0L))
            val clampedEnd = endMs.coerceIn(clampedStart + 500L, state.durationMs.coerceAtLeast(clampedStart + 500L))
            state.copy(trimStartMs = clampedStart, trimEndMs = clampedEnd)
        }
    }

    fun setFilterPreset(preset: VideoFilterPreset) {
        _uiState.update { it.copy(filterPreset = preset) }
    }

    fun setOverlayText(text: String) {
        _uiState.update { it.copy(overlayText = text.take(80)) }
    }

    fun setVolumeLevel(volume: Float) {
        _uiState.update { it.copy(volumeLevel = volume.coerceIn(0f, 1f)) }
    }

    fun setAudioTrack(track: AudioTrackOption) {
        _uiState.update { it.copy(selectedAudioTrack = track) }
    }

    fun setCaption(caption: String) {
        _uiState.update { it.copy(caption = caption.take(200)) }
    }

    fun setDescription(description: String) {
        _uiState.update { it.copy(description = description.take(500)) }
    }

    fun setHashtags(hashtags: String) {
        _uiState.update { it.copy(hashtags = hashtags) }
    }

    fun setPrivacy(privacy: VideoPrivacy) {
        _uiState.update { it.copy(privacy = privacy) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(statusMessage = null, publishCompleted = false) }
    }

    fun publishEditedVideo(context: Context) {
        val state = _uiState.value
        val sourceUri = state.videoUri ?: run {
            _uiState.update { it.copy(statusMessage = "Select or record a video first.") }
            return
        }
        if (state.caption.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Please add a caption before publishing.") }
            return
        }
        if (state.isExporting || state.isPublishing) return

        viewModelScope.launch {
            val needsTransform = state.filterPreset != VideoFilterPreset.ORIGINAL ||
                state.overlayText.isNotBlank() ||
                state.trimStartMs > 0L ||
                state.trimEndMs < state.durationMs ||
                state.volumeLevel < 0.05f

            var finalUri = sourceUri
            if (needsTransform) {
                _uiState.update { it.copy(isExporting = true, exportProgress = 0.05f, statusMessage = null) }
                val exportResult = VideoEditorEngine.exportEditedVideo(
                    context = context.applicationContext,
                    inputUri = sourceUri,
                    trimStartMs = state.trimStartMs,
                    trimEndMs = state.trimEndMs,
                    preset = state.filterPreset,
                    overlayText = state.overlayText,
                    muteOriginalAudio = state.volumeLevel < 0.05f,
                    onProgress = { progress ->
                        _uiState.update { it.copy(exportProgress = progress) }
                    }
                )
                exportResult.onSuccess { exportedUri ->
                    finalUri = exportedUri
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            statusMessage = "Filter export bypassed (${err.localizedMessage ?: "codec fallback"}); publishing original clip."
                        )
                    }
                }
                _uiState.update { it.copy(isExporting = false) }
            }

            _uiState.update { it.copy(isPublishing = true, uploadProgress = 0.1f) }
            val profile = socialRepo.observeUserProfile(currentUserId).firstOrNull()
            val username = profile?.username ?: "creator"
            val avatarUrl = profile?.avatarUrl.orEmpty()

            val publishResult = videoRepo.publishVideo(
                localVideoUri = finalUri,
                thumbnailUri = state.thumbnailUri,
                creatorUsername = username,
                creatorAvatarUrl = avatarUrl,
                caption = state.caption,
                description = state.description,
                hashtagsRaw = state.hashtags,
                audioTrack = state.selectedAudioTrack,
                filterPreset = state.filterPreset,
                overlayText = state.overlayText,
                trimStartMs = state.trimStartMs,
                trimEndMs = state.trimEndMs,
                volumeLevel = state.volumeLevel,
                privacy = state.privacy,
                onUploadProgress = { progress ->
                    _uiState.update { it.copy(uploadProgress = progress) }
                }
            )

            publishResult.onSuccess { res ->
                _uiState.value = StudioUiState(
                    publishCompleted = true,
                    statusMessage = res.storageNote ?: "Video published to Salman Video!"
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isPublishing = false,
                        statusMessage = err.message ?: "Failed to publish video."
                    )
                }
            }
        }
    }
}
