package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.RoyaltyFreeAudioCatalog
import com.example.model.VideoFilterPreset
import com.example.model.VideoPrivacy
import com.example.ui.components.VideoPlayerBox
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOverlay
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.SalmanCrimson
import com.example.ui.theme.SalmanCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.StudioViewModel
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun StudioUploadScreen(
    viewModel: StudioViewModel,
    onPublishedNavigateHome: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.publishCompleted) {
        if (state.publishCompleted) {
            onPublishedNavigateHome()
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val duration = extractVideoDurationMs(context, it)
            viewModel.setVideoSource(it, duration)
        }
    }

    val thumbnailPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.setThumbnailUri(uri)
    }

    if (state.videoUri == null) {
        CameraCaptureStep(
            onVideoRecorded = { uri ->
                val duration = extractVideoDurationMs(context, uri)
                viewModel.setVideoSource(uri, duration)
            },
            onPickFromGallery = {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
        )
    } else {
        BackHandler { viewModel.clearVideoSource() }
        VideoEditorAndPublishStep(
            viewModel = viewModel,
            onBackToCapture = { viewModel.clearVideoSource() },
            onPickThumbnail = {
                thumbnailPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPublishClick = { viewModel.publishEditedVideo(context) }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun CameraCaptureStep(
    onVideoRecorded: (Uri) -> Unit,
    onPickFromGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var useFrontCamera by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermissions = grants[Manifest.permission.CAMERA] == true &&
            grants[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedSec = 0
            while (isRecording) {
                delay(1000L)
                elapsedSec += 1
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
    ) {
        if (hasPermissions) {
            val previewView = remember { PreviewView(context) }

            LaunchedEffect(useFrontCamera) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val recorder = Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.HD))
                            .build()
                        val capture = VideoCapture.withOutput(recorder)
                        videoCapture = capture

                        val selector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                        cameraError = null
                    } catch (e: Exception) {
                        Log.w("StudioCamera", "Camera bind failed", e)
                        cameraError = "Camera preview unavailable on this device. You can still upload from your gallery."
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            DisposableEffect(Unit) {
                onDispose {
                    activeRecording?.stop()
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp)
                    .background(ObsidianSurface, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = SalmanCyan,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Creator Camera Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Allow Camera and Microphone permissions to record vertical videos directly, or pick a video from your gallery.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SalmanCrimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_camera_permission_button")
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Camera & Mic")
                }
            }
        }

        if (cameraError != null) {
            Surface(
                color = ObsidianOverlay,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text(
                    text = cameraError!!,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = ObsidianOverlay, shape = RoundedCornerShape(16.dp)) {
                Text(
                    text = if (isRecording) "REC 00:${elapsedSec.toString().padStart(2, '0')}" else "SALMAN STUDIO",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isRecording) SalmanCrimson else SalmanCyan,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            if (hasPermissions && !isRecording) {
                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ObsidianOverlay)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cameraswitch,
                        contentDescription = stringResource(R.string.cd_flip_camera),
                        tint = TextPrimary
                    )
                }
            }
        }

        // Bottom Capture & Gallery Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !isRecording, onClick = onPickFromGallery)
                    .padding(10.dp)
                    .testTag("pick_gallery_video_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianOverlay)
                        .border(1.dp, SalmanCyan, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = stringResource(R.string.cd_pick_gallery),
                        tint = SalmanCyan
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Gallery", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            }

            // Record Button
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .border(4.dp, TextPrimary, CircleShape)
                    .padding(6.dp)
                    .clip(if (isRecording) RoundedCornerShape(12.dp) else CircleShape)
                    .background(SalmanCrimson)
                    .clickable(enabled = hasPermissions && videoCapture != null) {
                        if (isRecording) {
                            activeRecording?.stop()
                            activeRecording = null
                            isRecording = false
                        } else {
                            val capture = videoCapture ?: return@clickable
                            val outFile = File(context.cacheDir, "salman_rec_${System.currentTimeMillis()}.mp4")
                            val options = FileOutputOptions.Builder(outFile).build()
                            isRecording = true
                            activeRecording = capture.output
                                .prepareRecording(context, options)
                                .withAudioEnabled()
                                .start(ContextCompat.getMainExecutor(context)) { event ->
                                    if (event is VideoRecordEvent.Finalize) {
                                        isRecording = false
                                        if (!event.hasError()) {
                                            onVideoRecorded(event.outputResults.outputUri)
                                        }
                                    }
                                }
                        }
                    }
                    .testTag("record_video_button"),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop recording", tint = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.width(68.dp))
        }
    }
}

@Composable
private fun VideoEditorAndPublishStep(
    viewModel: StudioViewModel,
    onBackToCapture: () -> Unit,
    onPickThumbnail: () -> Unit,
    onPublishClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToCapture) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text("Edit & Publish", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            }
            OutlinedButton(onClick = onPickThumbnail) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp), tint = SalmanCyan)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (state.thumbnailUri != null) "Cover Set" else "Cover", color = SalmanCyan)
            }
        }

        // Live Media3 Editor Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 11f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, OutlineSubtle, RoundedCornerShape(20.dp))
        ) {
            VideoPlayerBox(
                videoUriString = state.videoUri.toString(),
                isActive = !state.isExporting && !state.isPublishing,
                filterPreset = state.filterPreset,
                overlayText = state.overlayText,
                trimStartMs = state.trimStartMs,
                trimEndMs = state.trimEndMs,
                volumeLevel = state.volumeLevel,
                audioTrackUrl = state.selectedAudioTrack.url,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Real-time Filter Presets
        Text("Visual Filters", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VideoFilterPreset.entries) { preset ->
                val selected = state.filterPreset == preset
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setFilterPreset(preset) },
                    label = { Text(preset.label) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = ObsidianSurface,
                        labelColor = TextSecondary,
                        selectedContainerColor = SalmanCrimson,
                        selectedLabelColor = TextPrimary
                    )
                )
            }
        }

        // Trim Video Range
        val maxSec = (state.durationMs / 1000f).coerceAtLeast(1f)
        val startSec = state.trimStartMs / 1000f
        val endSec = (state.trimEndMs / 1000f).coerceAtMost(maxSec)
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Trim Clip", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    text = "${startSec.toInt()}s – ${endSec.toInt()}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = SalmanCyan
                )
            }
            RangeSlider(
                value = startSec..endSec,
                onValueChange = { range ->
                    viewModel.setTrimRange((range.start * 1000L).toLong(), (range.endInclusive * 1000L).toLong())
                },
                valueRange = 0f..maxSec,
                colors = SliderDefaults.colors(
                    thumbColor = SalmanCrimson,
                    activeTrackColor = SalmanCrimson,
                    inactiveTrackColor = ObsidianSurfaceElevated
                )
            )
        }

        // Volume Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Original Audio Volume", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("${(state.volumeLevel * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = SalmanCyan)
            }
            Slider(
                value = state.volumeLevel,
                onValueChange = { viewModel.setVolumeLevel(it) },
                colors = SliderDefaults.colors(
                    thumbColor = SalmanCyan,
                    activeTrackColor = SalmanCyan,
                    inactiveTrackColor = ObsidianSurfaceElevated
                )
            )
        }

        // Add Music (Royalty-free Catalog)
        Text("Background Sound", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RoyaltyFreeAudioCatalog.tracks) { track ->
                val selected = state.selectedAudioTrack.id == track.id
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setAudioTrack(track) },
                    label = { Text("${track.title} (${track.durationLabel})") },
                    leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = ObsidianSurface,
                        labelColor = TextSecondary,
                        selectedContainerColor = ObsidianSurfaceElevated,
                        selectedLabelColor = SalmanCyan
                    )
                )
            }
        }

        // Text Overlay
        OutlinedTextField(
            value = state.overlayText,
            onValueChange = { viewModel.setOverlayText(it) },
            label = { Text("On-Video Text Overlay (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Metadata Form
        OutlinedTextField(
            value = state.caption,
            onValueChange = { viewModel.setCaption(it) },
            label = { Text("Caption *") },
            placeholder = { Text("Describe your moment...") },
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("publish_caption_input")
        )

        OutlinedTextField(
            value = state.hashtags,
            onValueChange = { viewModel.setHashtags(it) },
            label = { Text("Hashtags (space or comma separated)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = { viewModel.setDescription(it) },
            label = { Text("Extended Description (optional)") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        // Privacy Selector
        Text("Who can watch this video", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoPrivacy.entries.forEach { privacy ->
                FilterChip(
                    selected = state.privacy == privacy,
                    onClick = { viewModel.setPrivacy(privacy) },
                    label = { Text(privacy.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = ObsidianSurface,
                        labelColor = TextSecondary,
                        selectedContainerColor = SalmanCrimson,
                        selectedLabelColor = TextPrimary
                    )
                )
            }
        }

        if (state.isExporting || state.isPublishing) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val label = if (state.isExporting) {
                    "Rendering Media3 filters & trim (${(state.exportProgress * 100).toInt()}%)..."
                } else {
                    "Uploading & publishing (${(state.uploadProgress * 100).toInt()}%)..."
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = SalmanCyan)
                LinearProgressIndicator(
                    progress = { if (state.isExporting) state.exportProgress else state.uploadProgress },
                    color = SalmanCrimson,
                    trackColor = ObsidianSurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }

        if (!state.statusMessage.isNullOrBlank()) {
            Text(
                text = state.statusMessage!!,
                color = SalmanCrimson,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = onPublishClick,
            enabled = !state.isExporting && !state.isPublishing,
            colors = ButtonDefaults.buttonColors(containerColor = SalmanCrimson),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("publish_video_button")
        ) {
            Icon(Icons.Filled.Publish, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_publish),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun extractVideoDurationMs(context: Context, uri: Uri): Long {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        raw?.toLongOrNull()?.coerceAtLeast(1_000L) ?: 15_000L
    } catch (_: Exception) {
        15_000L
    }
}
