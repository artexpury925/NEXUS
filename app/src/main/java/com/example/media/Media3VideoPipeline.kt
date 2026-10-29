package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "Media3VideoPipeline"

/**
 * Hardware-accelerated Android Video Processing Pipeline built on Jetpack Media3 Transformer
 * (the official Android equivalent to FFmpeg for trimming, 9:16 vertical Shorts cropping,
 * scaling, watermark overlay compositing, and H.264/AAC MP4 transcoding from both uploaded
 * device files and external HTTP/HTTPS/HLS URLs).
 */
object Media3VideoPipeline {

    suspend fun inspectAnyVideoSource(
        context: Context,
        sourceUri: Uri
    ): VideoSourceMetadata = withContext(Dispatchers.IO) {
        val scheme = sourceUri.scheme?.lowercase().orEmpty()
        if (scheme != "http" && scheme != "https") {
            return@withContext VideoClipEngine.inspectVideo(context, sourceUri)
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(
                sourceUri.toString(),
                mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 14; NexusPipeline/3.0)")
            )
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 20_000L
            val durationSec = (durationMs / 1000L).toInt().coerceAtLeast(1)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1280
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 720

            val frames = mutableListOf<Bitmap>()
            for (frac in listOf(0.20, 0.55)) {
                val timeUs = (durationMs * 1000.0 * frac).toLong().coerceAtLeast(0L)
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null) {
                    val maxDim = 640
                    val ratio = minOf(maxDim.toFloat() / raw.width, maxDim.toFloat() / raw.height, 1f)
                    val scaled = Bitmap.createScaledBitmap(
                        raw,
                        (raw.width * ratio).toInt().coerceAtLeast(1),
                        (raw.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                    frames.add(scaled)
                }
            }
            VideoSourceMetadata(
                durationSec = durationSec,
                width = width,
                height = height,
                thumbnail = frames.firstOrNull(),
                sampleFrames = frames
            )
        } catch (e: Exception) {
            Log.w(TAG, "Remote metadata inspection fallback for $sourceUri: ${e.message}")
            VideoSourceMetadata(durationSec = 20, width = 1280, height = 720)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Clips, crops, overlays, and transcodes an uploaded local Uri (`content://` / `file://`)
     * OR an external video stream Uri (`http://` / `https://`, including `.mp4`, `.mov`, `.webm`, `.m3u8`)
     * into a ready-to-upload H.264 + AAC `.mp4` clip.
     */
    @OptIn(UnstableApi::class)
    suspend fun processVideoClip(
        context: Context,
        sourceUri: Uri,
        startSec: Int,
        endSec: Int,
        cropVertical916: Boolean = true,
        watermarkLabel: String? = "@arnold60011 • NEXUS",
        onProgressPercent: ((Int) -> Unit)? = null
    ): File {
        val appContext = context.applicationContext
        val clipsDir = File(appContext.filesDir, "clips").apply { mkdirs() }
        val outputFile = File(clipsDir, "nexus_m3_${UUID.randomUUID().toString().take(8)}.mp4")

        val safeStartMs = startSec.coerceAtLeast(0) * 1_000L
        val safeEndMs = maxOf(safeStartMs + 2_000L, endSec * 1_000L)

        try {
            return runMedia3Export(
                appContext = appContext,
                sourceUri = sourceUri,
                startMs = safeStartMs,
                endMs = safeEndMs,
                cropVertical916 = cropVertical916,
                watermarkLabel = watermarkLabel,
                applyEffects = true,
                outputFile = outputFile,
                onProgressPercent = onProgressPercent
            )
        } catch (glError: Exception) {
            Log.w(TAG, "Media3 GL effect export fallback to direct clip export: ${glError.message}")
            outputFile.delete()
        }

        val directOutput = File(clipsDir, "nexus_m3_trim_${UUID.randomUUID().toString().take(8)}.mp4")
        try {
            return runMedia3Export(
                appContext = appContext,
                sourceUri = sourceUri,
                startMs = safeStartMs,
                endMs = safeEndMs,
                cropVertical916 = false,
                watermarkLabel = null,
                applyEffects = false,
                outputFile = directOutput,
                onProgressPercent = onProgressPercent
            )
        } catch (trimError: Exception) {
            Log.w(TAG, "Media3 direct trim fallback to native MediaExtractor/MediaMuxer: ${trimError.message}")
            directOutput.delete()
            val scheme = sourceUri.scheme?.lowercase().orEmpty()
            if (scheme == "content" || scheme == "file") {
                return VideoClipEngine.trimVideoClip(appContext, sourceUri, startSec, endSec)
            }
            throw trimError
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun runMedia3Export(
        appContext: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        cropVertical916: Boolean,
        watermarkLabel: String?,
        applyEffects: Boolean,
        outputFile: File,
        onProgressPercent: ((Int) -> Unit)?
    ): File = withContext(Dispatchers.Main) {
        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val videoEffects = mutableListOf<Effect>()
        var overlayBitmap: Bitmap? = null

        if (applyEffects) {
            if (cropVertical916) {
                videoEffects.add(
                    Presentation.createForWidthAndHeight(
                        720,
                        1280,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                    )
                )
            } else {
                videoEffects.add(Presentation.createForHeight(720))
            }

            if (!watermarkLabel.isNullOrBlank()) {
                val targetW = if (cropVertical916) 720 else 1280
                val targetH = if (cropVertical916) 1280 else 720
                val bmp = createWatermarkOverlayBitmap(targetW, targetH, watermarkLabel)
                overlayBitmap = bmp
                val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(bmp)
                videoEffects.add(OverlayEffect(listOf<TextureOverlay>(bitmapOverlay)))
            }
        }

        val editedMediaItemBuilder = EditedMediaItem.Builder(mediaItem)
        if (videoEffects.isNotEmpty()) {
            editedMediaItemBuilder.setEffects(
                Effects(
                    /* audioProcessors = */ emptyList(),
                    /* videoEffects = */ videoEffects
                )
            )
        }
        val editedMediaItem = editedMediaItemBuilder.build()

        val mainHandler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()

        suspendCancellableCoroutine { cont ->
            var progressRunnable: Runnable? = null

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    progressRunnable?.let { mainHandler.removeCallbacks(it) }
                    overlayBitmap?.recycle()
                    if (outputFile.exists() && outputFile.length() > 512L) {
                        onProgressPercent?.invoke(100)
                        if (cont.isActive) cont.resume(outputFile)
                    } else {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException("Media3 pipeline produced an empty MP4 output.")
                            )
                        }
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    progressRunnable?.let { mainHandler.removeCallbacks(it) }
                    overlayBitmap?.recycle()
                    outputFile.delete()
                    if (cont.isActive) {
                        cont.resumeWithException(exportException)
                    }
                }
            }

            val transformerBuilder = Transformer.Builder(appContext)
                .addListener(listener)
            if (applyEffects) {
                transformerBuilder
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
            }
            val transformer = transformerBuilder.build()

            if (onProgressPercent != null) {
                val poll = object : Runnable {
                    override fun run() {
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgressPercent(progressHolder.progress.coerceIn(0, 99))
                        }
                        if (state != Transformer.PROGRESS_STATE_NOT_STARTED && cont.isActive) {
                            mainHandler.postDelayed(this, 400L)
                        }
                    }
                }
                progressRunnable = poll
                mainHandler.postDelayed(poll, 350L)
            }

            cont.invokeOnCancellation {
                progressRunnable?.let { mainHandler.removeCallbacks(it) }
                try {
                    transformer.cancel()
                } catch (_: Exception) {
                }
                overlayBitmap?.recycle()
                outputFile.delete()
            }

            transformer.start(editedMediaItem, outputFile.absolutePath)
        }
    }

    private fun createWatermarkOverlayBitmap(width: Int, height: Int, label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 11, 15, 28)
        }
        val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF1744")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val textWidth = textPaint.measureText(label)
        val padH = 22f
        val pillW = textWidth + padH * 2f
        val pillH = 44f
        val left = width - pillW - 28f
        val top = height - pillH - 36f
        val rect = RectF(left, top, left + pillW, top + pillH)

        canvas.drawRoundRect(rect, 22f, 22f, pillBg)
        canvas.drawRoundRect(rect, 22f, 22f, pillStroke)
        canvas.drawText(label, left + padH, top + 29f, textPaint)
        return bitmap
    }
}
