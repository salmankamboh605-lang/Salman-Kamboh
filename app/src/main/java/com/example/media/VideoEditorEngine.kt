package com.example.media

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.model.VideoFilterPreset
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object VideoEditorEngine {

    @OptIn(UnstableApi::class)
    fun buildVideoEffects(
        preset: VideoFilterPreset,
        overlayText: String
    ): List<Effect> {
        val effects = mutableListOf<Effect>()
        when (preset) {
            VideoFilterPreset.ORIGINAL -> Unit
            VideoFilterPreset.NEON_CYBER -> {
                effects += RgbAdjustment.Builder()
                    .setRedScale(1.18f)
                    .setGreenScale(0.95f)
                    .setBlueScale(1.30f)
                    .build()
                effects += Contrast(0.22f)
            }
            VideoFilterPreset.NOIR_MONO -> {
                effects += RgbFilter.createGrayscaleFilter()
                effects += Contrast(0.28f)
            }
            VideoFilterPreset.WARM_FILM -> {
                effects += RgbAdjustment.Builder()
                    .setRedScale(1.22f)
                    .setGreenScale(1.06f)
                    .setBlueScale(0.84f)
                    .build()
            }
            VideoFilterPreset.VIVID_POP -> {
                effects += Contrast(0.32f)
                effects += RgbAdjustment.Builder()
                    .setRedScale(1.12f)
                    .setGreenScale(1.12f)
                    .setBlueScale(1.12f)
                    .build()
            }
        }
        val cleanText = overlayText.trim()
        if (cleanText.isNotEmpty()) {
            val spannable = SpannableString(cleanText).apply {
                setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    0,
                    cleanText.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            val overlaySettings = OverlaySettings.Builder()
                .setAlphaScale(0.92f)
                .build()
            val textOverlay = TextOverlay.createStaticTextOverlay(spannable, overlaySettings)
            effects += OverlayEffect(ImmutableList.of<TextureOverlay>(textOverlay))
        }
        return effects
    }

    @OptIn(UnstableApi::class)
    fun buildClippedMediaItem(
        uri: Uri,
        trimStartMs: Long,
        trimEndMs: Long
    ): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        if (trimStartMs > 0L || trimEndMs > trimStartMs) {
            val clipBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(trimStartMs.coerceAtLeast(0L))
            if (trimEndMs > trimStartMs) {
                clipBuilder.setEndPositionMs(trimEndMs)
            }
            builder.setClippingConfiguration(clipBuilder.build())
        }
        return builder.build()
    }

    @OptIn(UnstableApi::class)
    suspend fun exportEditedVideo(
        context: Context,
        inputUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        preset: VideoFilterPreset,
        overlayText: String,
        muteOriginalAudio: Boolean,
        onProgress: (Float) -> Unit
    ): Result<Uri> = suspendCancellableCoroutine { continuation ->
        val outputFile = File(context.cacheDir, "salman_export_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()

        val mediaItem = buildClippedMediaItem(inputUri, trimStartMs, trimEndMs)
        val videoEffects = buildVideoEffects(preset, overlayText)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(muteOriginalAudio)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val handler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()
        var transformer: Transformer? = null

        val progressRunnable = object : Runnable {
            override fun run() {
                val activeTransformer = transformer ?: return
                val state = activeTransformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress((progressHolder.progress / 100f).coerceIn(0f, 1f))
                }
                handler.postDelayed(this, 200L)
            }
        }

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                handler.removeCallbacks(progressRunnable)
                onProgress(1f)
                if (continuation.isActive) {
                    continuation.resume(Result.success(Uri.fromFile(outputFile)))
                }
            }

            override fun onError(
                composition: androidx.media3.transformer.Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                handler.removeCallbacks(progressRunnable)
                if (continuation.isActive) {
                    continuation.resume(Result.failure(exportException))
                }
            }
        }

        handler.post {
            try {
                transformer = Transformer.Builder(context)
                    .addListener(listener)
                    .build()
                transformer?.start(editedMediaItem, outputFile.absolutePath)
                handler.postDelayed(progressRunnable, 200L)
            } catch (e: Exception) {
                handler.removeCallbacks(progressRunnable)
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }

        continuation.invokeOnCancellation {
            handler.post {
                handler.removeCallbacks(progressRunnable)
                transformer?.cancel()
            }
        }
    }
}
