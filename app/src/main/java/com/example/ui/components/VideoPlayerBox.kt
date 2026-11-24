package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.media.VideoEditorEngine
import com.example.model.VideoFilterPreset
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOverlay
import com.example.ui.theme.SalmanCrimson
import com.example.ui.theme.SalmanCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerBox(
    videoUriString: String,
    isActive: Boolean,
    filterPreset: VideoFilterPreset = VideoFilterPreset.ORIGINAL,
    overlayText: String = "",
    trimStartMs: Long = 0L,
    trimEndMs: Long = 0L,
    volumeLevel: Float = 1f,
    audioTrackUrl: String = "",
    modifier: Modifier = Modifier,
    onDoubleTapLike: () -> Unit = {},
    onPlaybackStarted: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isUserPaused by remember(videoUriString) { mutableStateOf(false) }
    var isBuffering by remember(videoUriString) { mutableStateOf(true) }
    var errorMessage by remember(videoUriString) { mutableStateOf<String?>(null) }
    var showHeartBurst by remember { mutableStateOf(false) }
    var hasReportedView by remember(videoUriString) { mutableStateOf(false) }

    val exoPlayer = remember(videoUriString) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    val audioPlayer = remember(audioTrackUrl) {
        if (audioTrackUrl.isNotBlank()) {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(audioTrackUrl))
                prepare()
            }
        } else null
    }

    LaunchedEffect(videoUriString, trimStartMs, trimEndMs, filterPreset, overlayText) {
        errorMessage = null
        isBuffering = true
        val parsedUri = Uri.parse(videoUriString)
        val item = VideoEditorEngine.buildClippedMediaItem(parsedUri, trimStartMs, trimEndMs)
        exoPlayer.setMediaItem(item)
        exoPlayer.setVideoEffects(VideoEditorEngine.buildVideoEffects(filterPreset, overlayText))
        exoPlayer.prepare()
    }

    LaunchedEffect(volumeLevel) {
        exoPlayer.volume = volumeLevel.coerceIn(0f, 1f)
    }

    LaunchedEffect(isActive, isUserPaused) {
        val shouldPlay = isActive && !isUserPaused
        exoPlayer.playWhenReady = shouldPlay
        audioPlayer?.playWhenReady = shouldPlay
    }

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(650L)
            showHeartBurst = false
        }
    }

    DisposableEffect(exoPlayer, audioPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY && !hasReportedView && isActive) {
                    hasReportedView = true
                    onPlaybackStarted()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                errorMessage = error.localizedMessage ?: "Unable to stream this video."
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                    audioPlayer?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isActive && !isUserPaused) {
                        exoPlayer.play()
                        audioPlayer?.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            audioPlayer?.release()
        }
    }

    val heartScale by animateFloatAsState(
        targetValue = if (showHeartBurst) 1.25f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heart_burst"
    )

    Box(
        modifier = modifier
            .background(ObsidianBg)
            .pointerInput(videoUriString) {
                detectTapGestures(
                    onTap = {
                        if (errorMessage == null) {
                            isUserPaused = !isUserPaused
                        }
                    },
                    onDoubleTap = {
                        showHeartBurst = true
                        onDoubleTapLike()
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(
                color = SalmanCyan,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
            )
        }

        AnimatedVisibility(
            visible = isUserPaused && errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = ObsidianOverlay,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Paused",
                    tint = TextPrimary,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                )
            }
        }

        AnimatedVisibility(
            visible = showHeartBurst,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = SalmanCrimson,
                modifier = Modifier
                    .size(104.dp)
                    .scale(heartScale)
            )
        }

        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(ObsidianOverlay, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = SalmanCrimson,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Playback Interrupted",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage ?: "Network or media format error",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        errorMessage = null
                        isBuffering = true
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SalmanCrimson)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Retry")
                }
            }
        }
    }
}
