package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val TAG = "VideoClipEngine"

data class ClipSceneSlide(
    val badge: String,
    val headline: String,
    val subtitle: String,
    val accentHex: String,
    val narration: String = ""
)

data class VideoSourceMetadata(
    val durationSec: Int,
    val width: Int = 720,
    val height: Int = 1280,
    val thumbnail: Bitmap? = null,
    val sampleFrames: List<Bitmap> = emptyList()
)

private data class EncodedMediaPacket(
    val isAudio: Boolean,
    val bytes: ByteArray,
    val presentationTimeUs: Long,
    val flags: Int
)

object VideoClipEngine {

    suspend fun inspectVideo(context: Context, uri: Uri): VideoSourceMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file" && uri.path != null) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15_000L
            val durationSec = (durationMs / 1000L).toInt().coerceAtLeast(1)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280

            val fractions = listOf(0.20, 0.50, 0.80)
            val frames = mutableListOf<Bitmap>()
            for (frac in fractions) {
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
            Log.e(TAG, "Failed to inspect source video", e)
            VideoSourceMetadata(durationSec = 15, thumbnail = null)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Losslessly trims an input video Uri or File between [startSec] and [endSec] into a real .mp4 file
     * using Android's native MediaExtractor and MediaMuxer.
     */
    suspend fun trimVideoClip(
        context: Context,
        sourceUri: Uri,
        startSec: Int,
        endSec: Int
    ): File = withContext(Dispatchers.IO) {
        val clipsDir = File(context.filesDir, "clips").apply { mkdirs() }
        val outputFile = File(clipsDir, "nexus_clip_${UUID.randomUUID().toString().take(8)}.mp4")

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            if (sourceUri.scheme == "file" && sourceUri.path != null) {
                extractor.setDataSource(sourceUri.path!!)
            } else {
                context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                } ?: throw IllegalStateException("Unable to open selected video file.")
            }

            val trackCount = extractor.trackCount
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val indexMap = HashMap<Int, Int>()
            var maxBufferSize = 1024 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    indexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxBufferSize = maxOf(maxBufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                }
            }

            if (indexMap.isEmpty()) {
                throw IllegalStateException("Selected media has no compatible MP4 video/audio tracks.")
            }

            val startUs = startSec.coerceAtLeast(0) * 1_000_000L
            val endUs = maxOf(startUs + 2_000_000L, endSec * 1_000_000L)

            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            muxer.start()

            var basePtsUs = -1L
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                val trackIndex = extractor.sampleTrackIndex
                val dstTrack = indexMap[trackIndex]
                if (dstTrack != null && sampleTimeUs >= 0L) {
                    if (basePtsUs < 0L) basePtsUs = sampleTimeUs
                    bufferInfo.presentationTimeUs = (sampleTimeUs - basePtsUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Video trimming failed", e)
            outputFile.delete()
            throw e
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Renders an AI-scripted vertical 720x1280 H.264 + AAC Audio MP4 video directly on-device
     * using exact monotonic YUV420 presentation timestamps (`frameIdx * 1_000_000L / fps`) and
     * timestamp-interleaved muxing so every generated video plays smoothly with full duration and
     * synchronized spoken TTS voiceover on all Android devices and emulators.
     */
    suspend fun renderPromptShortVideo(
        context: Context,
        scenes: List<ClipSceneSlide>,
        durationSec: Int,
        hashtagsLine: String,
        visualStyle: String = "Cyber Neon",
        backgroundKeyframe: Bitmap? = null,
        enableVoiceover: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        val width = 720
        val height = 1280
        val fps = 15
        val safeDurationSec = durationSec.coerceIn(6, 24)
        val totalFrames = safeDurationSec * fps

        val effectiveScenes = scenes.ifEmpty {
            listOf(
                ClipSceneSlide(
                    badge = "NEXUS AI VIDEO",
                    headline = "AI Creators Move 10x Faster",
                    subtitle = "Subscribe to @arnold60011 for exclusive AI builds",
                    accentHex = "#00E5FF",
                    narration = "Nexus AI Video Studio is live. Subscribe to Arnold Chirchir for more."
                )
            )
        }

        // 1. Synthesize full spoken voiceover + harmonic synth audio PCM (44.1kHz mono)
        val fullNarration = effectiveScenes.joinToString(". ") {
            it.narration.ifBlank { "${it.headline}. ${it.subtitle}" }
        }
        val pcmSamples = buildAudioTrackPcm(
            context = context,
            narrationText = fullNarration,
            durationSec = safeDurationSec,
            enableVoiceover = enableVoiceover
        )
        val encodedAudioResult = try {
            encodePcmToAac(pcmSamples, sampleRate = 44_100)
        } catch (e: Exception) {
            Log.w(TAG, "AAC encoding skipped, falling back to video-only mux: ${e.message}")
            null
        }

        // 2. Encode H.264 video frames with exact monotonic presentation timestamps (YUV420Flexible)
        val (videoOutputFormat, videoPackets) = encodeFramesToH264(
            width = width,
            height = height,
            fps = fps,
            totalFrames = totalFrames,
            scenes = effectiveScenes,
            pcmSamples = pcmSamples,
            visualStyle = visualStyle,
            backgroundKeyframe = backgroundKeyframe,
            hashtagsLine = hashtagsLine
        )

        // 3. Interleave Video + Audio packets in presentationTimeUs order into MP4 container
        val clipsDir = File(context.filesDir, "clips").apply { mkdirs() }
        val outputFile = File(clipsDir, "nexus_short_${UUID.randomUUID().toString().take(8)}.mp4")

        var muxer: MediaMuxer? = null
        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoTrackIndex = muxer.addTrack(videoOutputFormat)
            val audioTrackIndex = if (encodedAudioResult != null && encodedAudioResult.second.isNotEmpty()) {
                muxer.addTrack(encodedAudioResult.first)
            } else {
                -1
            }

            muxer.start()

            val combinedPackets = ArrayList<EncodedMediaPacket>(
                videoPackets.size + (encodedAudioResult?.second?.size ?: 0)
            )
            combinedPackets.addAll(videoPackets)
            if (audioTrackIndex >= 0 && encodedAudioResult != null) {
                combinedPackets.addAll(encodedAudioResult.second)
            }
            combinedPackets.sortBy { it.presentationTimeUs }

            val info = MediaCodec.BufferInfo()
            for (pkt in combinedPackets) {
                val trackIdx = if (pkt.isAudio) audioTrackIndex else videoTrackIndex
                if (trackIdx >= 0 && pkt.bytes.isNotEmpty()) {
                    val buf = ByteBuffer.wrap(pkt.bytes)
                    info.set(0, pkt.bytes.size, pkt.presentationTimeUs.coerceAtLeast(0L), pkt.flags)
                    muxer.writeSampleData(trackIdx, buf, info)
                }
            }

            muxer.stop()
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed muxing H.264 + AAC MP4", e)
            outputFile.delete()
            throw e
        } finally {
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun encodeFramesToH264(
        width: Int,
        height: Int,
        fps: Int,
        totalFrames: Int,
        scenes: List<ClipSceneSlide>,
        pcmSamples: ShortArray,
        visualStyle: String,
        backgroundKeyframe: Bitmap?,
        hashtagsLine: String
    ): Pair<MediaFormat, List<EncodedMediaPacket>> {
        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 3_200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val packets = ArrayList<EncodedMediaPacket>(totalFrames + 8)
        var outputFormat: MediaFormat? = null
        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frameBitmap)
        val argbPixels = IntArray(width * height)

        try {
            encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var frameIdx = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(15_000L)
                    if (inIdx >= 0) {
                        if (frameIdx >= totalFrames) {
                            val eosPtsUs = (frameIdx.toLong() * 1_000_000L) / fps.toLong()
                            encoder.queueInputBuffer(inIdx, 0, 0, eosPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val globalProgress = frameIdx.toFloat() / totalFrames.toFloat()
                            val exactPos = globalProgress * scenes.size
                            val sceneIdx = exactPos.toInt().coerceIn(0, scenes.lastIndex)
                            val localProgress = (exactPos - sceneIdx).coerceIn(0f, 1f)
                            val scene = scenes[sceneIdx]

                            val sampleStart = ((globalProgress * pcmSamples.size).toInt())
                                .coerceIn(0, (pcmSamples.size - 1).coerceAtLeast(0))
                            val sampleWindow = min(1024, pcmSamples.size - sampleStart)
                            var energySum = 0L
                            for (k in 0 until sampleWindow) {
                                energySum += abs(pcmSamples[sampleStart + k].toInt())
                            }
                            val audioLevel = if (sampleWindow > 0) {
                                ((energySum.toFloat() / sampleWindow.toFloat()) / 9000f).coerceIn(0.08f, 1f)
                            } else {
                                0.25f
                            }

                            drawShortSceneFrame(
                                canvas = frameCanvas,
                                width = width,
                                height = height,
                                scene = scene,
                                sceneIndex = sceneIdx,
                                sceneCount = scenes.size,
                                globalProgress = globalProgress,
                                localSceneProgress = localProgress,
                                audioLevel = audioLevel,
                                visualStyle = visualStyle,
                                backgroundBitmap = backgroundKeyframe,
                                hashtagsLine = hashtagsLine
                            )
                            frameBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)

                            val ptsUs = (frameIdx.toLong() * 1_000_000L) / fps.toLong()
                            val inputImage = try {
                                encoder.getInputImage(inIdx)
                            } catch (_: Exception) {
                                null
                            }

                            if (inputImage != null) {
                                fillYuv420ImageFromArgb(inputImage, argbPixels, width, height)
                                inputImage.close()
                                val byteSize = (width * height * 3) / 2
                                encoder.queueInputBuffer(inIdx, 0, byteSize, ptsUs, 0)
                            } else {
                                val inBuf = encoder.getInputBuffer(inIdx)!!
                                inBuf.clear()
                                val written = writeI420ToBuffer(inBuf, argbPixels, width, height)
                                encoder.queueInputBuffer(inIdx, 0, written, ptsUs, 0)
                            }
                            frameIdx++
                        }
                    }
                }

                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 15_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = encoder.outputFormat
                    }
                    outIdx >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0 &&
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val bytes = ByteArray(bufferInfo.size)
                            outBuf.get(bytes)
                            packets.add(
                                EncodedMediaPacket(
                                    isAudio = false,
                                    bytes = bytes,
                                    presentationTimeUs = bufferInfo.presentationTimeUs,
                                    flags = bufferInfo.flags
                                )
                            )
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val finalFmt = outputFormat ?: throw IllegalStateException("H.264 encoder did not produce output format.")
            return finalFmt to packets
        } finally {
            frameBitmap.recycle()
            try {
                encoder.stop()
            } catch (_: Exception) {
            }
            try {
                encoder.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun fillYuv420ImageFromArgb(image: Image, argb: IntArray, width: Int, height: Int) {
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val yCap = yBuf.capacity()
        val uCap = uBuf.capacity()
        val vCap = vBuf.capacity()

        for (y in 0 until height) {
            val argbRowOffset = y * width
            val yRowOffset = y * yRowStride
            val uvRow = y shr 1
            val uRowOffset = uvRow * uRowStride
            val vRowOffset = uvRow * vRowStride
            val isEvenRow = (y and 1) == 0

            for (x in 0 until width) {
                val c = argb[argbRowOffset + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val yVal = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                val yPos = yRowOffset + x * yPixelStride
                if (yPos < yCap) {
                    yBuf.put(yPos, yVal.toByte())
                }

                if (isEvenRow && (x and 1) == 0) {
                    val uvCol = x shr 1
                    val uVal = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val vVal = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val uPos = uRowOffset + uvCol * uPixelStride
                    val vPos = vRowOffset + uvCol * vPixelStride
                    if (uPos < uCap) uBuf.put(uPos, uVal.toByte())
                    if (vPos < vCap) vBuf.put(vPos, vVal.toByte())
                }
            }
        }
    }

    private fun writeI420ToBuffer(buf: ByteBuffer, argb: IntArray, width: Int, height: Int): Int {
        val frameSize = width * height
        val totalBytes = (frameSize * 3) / 2
        val yuv = ByteArray(totalBytes)
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + (frameSize / 4)

        for (j in 0 until height) {
            val rowOffset = j * width
            val isEvenRow = (j and 1) == 0
            for (i in 0 until width) {
                val c = argb[rowOffset + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val yVal = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                yuv[yIndex++] = yVal.toByte()

                if (isEvenRow && (i and 1) == 0) {
                    val uVal = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val vVal = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    yuv[uIndex++] = uVal.toByte()
                    yuv[vIndex++] = vVal.toByte()
                }
            }
        }
        val toWrite = min(totalBytes, buf.remaining())
        buf.put(yuv, 0, toWrite)
        return toWrite
    }

    /**
     * Exports a generated or clipped MP4 into the user's device Movies/NexusClips gallery
     * via MediaStore without requiring any storage permissions.
     */
    suspend fun exportClipToGallery(context: Context, videoFile: File, title: String): Boolean = withContext(Dispatchers.IO) {
        if (!videoFile.exists()) return@withContext false
        return@withContext try {
            val cleanName = title.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim().take(48).ifBlank { "Nexus_Video" }
            val fileName = "${cleanName.replace(' ', '_')}_${System.currentTimeMillis() % 10000}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/NexusClips")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val itemUri = resolver.insert(collection, values) ?: return@withContext false
            resolver.openOutputStream(itemUri)?.use { out ->
                videoFile.inputStream().use { inp ->
                    inp.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, doneValues, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting MP4 to gallery", e)
            false
        }
    }

    private suspend fun buildAudioTrackPcm(
        context: Context,
        narrationText: String,
        durationSec: Int,
        enableVoiceover: Boolean
    ): ShortArray {
        val sampleRate = 44_100
        val totalSamples = durationSec * sampleRate
        val output = ShortArray(totalSamples)

        val ttsPcm: ShortArray? = if (enableVoiceover && narrationText.isNotBlank()) {
            synthesizeTtsAt44k(context, narrationText.take(480))
        } else {
            null
        }

        val twoPi = 2.0 * Math.PI
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate.toDouble()
            val beatEnv = 0.55 + 0.45 * sin(twoPi * 2.0 * t)
            val bass = sin(twoPi * 65.41 * t) * 0.5 + sin(twoPi * 130.81 * t) * 0.3 + sin(twoPi * 196.0 * t) * 0.2
            val bedAmplitude = if (ttsPcm != null && i < ttsPcm.size) 950.0 else 2200.0
            val fade = when {
                t < 0.25 -> t / 0.25
                t > durationSec - 0.4 -> ((durationSec - t) / 0.4).coerceIn(0.0, 1.0)
                else -> 1.0
            }
            val ambientSample = (bass * beatEnv * bedAmplitude * fade).toInt()
            val voiceSample = if (ttsPcm != null && i < ttsPcm.size) (ttsPcm[i].toInt() * 0.92).toInt() else 0
            output[i] = (ambientSample + voiceSample).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return output
    }

    private suspend fun synthesizeTtsAt44k(context: Context, script: String): ShortArray? {
        val wavFile = File(context.cacheDir, "nexus_tts_${UUID.randomUUID().toString().take(8)}.wav")
        var ttsInstance: TextToSpeech? = null
        return try {
            val readyDeferred = CompletableDeferred<Boolean>()
            val doneDeferred = CompletableDeferred<Boolean>()
            ttsInstance = TextToSpeech(context.applicationContext) { status ->
                readyDeferred.complete(status == TextToSpeech.SUCCESS)
            }
            val isReady = withTimeoutOrNull(3_000L) { readyDeferred.await() } ?: false
            if (!isReady) return null

            ttsInstance.language = Locale.US
            ttsInstance.setSpeechRate(1.06f)
            val utteranceId = "nexus_vo_${System.currentTimeMillis()}"
            ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    doneDeferred.complete(true)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    doneDeferred.complete(false)
                }
                override fun onError(id: String?, errorCode: Int) {
                    doneDeferred.complete(false)
                }
            })

            val synthRes = ttsInstance.synthesizeToFile(script, Bundle(), wavFile, utteranceId)
            if (synthRes != TextToSpeech.SUCCESS) return null

            val finished = withTimeoutOrNull(5_500L) { doneDeferred.await() } ?: false
            if (!finished || !wavFile.exists() || wavFile.length() <= 44L) return null

            decodeWavAndResampleTo44kMono(wavFile)
        } catch (e: Exception) {
            Log.w(TAG, "TTS voiceover synthesis fallback: ${e.message}")
            null
        } finally {
            try {
                ttsInstance?.stop()
                ttsInstance?.shutdown()
            } catch (_: Exception) {
            }
            wavFile.delete()
        }
    }

    private fun decodeWavAndResampleTo44kMono(wavFile: File): ShortArray? {
        RandomAccessFile(wavFile, "r").use { raf ->
            if (raf.length() <= 44L) return null
            val header = ByteArray(44)
            raf.readFully(header)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val channels = bb.getShort(22).toInt().coerceIn(1, 2)
            val srcRate = bb.getInt(24).coerceIn(8_000, 48_000)
            val pcmByteLen = (raf.length() - 44L).toInt().coerceAtMost(12 * 1024 * 1024)
            val pcmBytes = ByteArray(pcmByteLen)
            raf.readFully(pcmBytes)

            val shortBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val frameCount = shortBuf.remaining() / channels
            if (frameCount <= 0) return null

            val monoSrc = ShortArray(frameCount)
            for (i in 0 until frameCount) {
                if (channels == 2) {
                    val l = shortBuf.get(i * 2).toInt()
                    val r = shortBuf.get(i * 2 + 1).toInt()
                    monoSrc[i] = ((l + r) / 2).toShort()
                } else {
                    monoSrc[i] = shortBuf.get(i)
                }
            }

            if (srcRate == 44_100) return monoSrc
            val dstLen = ((frameCount.toLong() * 44_100L) / srcRate.toLong()).toInt().coerceAtLeast(1)
            val resampled = ShortArray(dstLen)
            val ratio = srcRate.toDouble() / 44_100.0
            for (i in 0 until dstLen) {
                val srcPos = i * ratio
                val idx = srcPos.toInt().coerceIn(0, frameCount - 1)
                val nextIdx = (idx + 1).coerceAtMost(frameCount - 1)
                val frac = srcPos - idx
                val interp = monoSrc[idx] * (1.0 - frac) + monoSrc[nextIdx] * frac
                resampled[i] = interp.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            return resampled
        }
    }

    private fun encodePcmToAac(pcm: ShortArray, sampleRate: Int): Pair<MediaFormat, List<EncodedMediaPacket>> {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val packets = ArrayList<EncodedMediaPacket>()
        var outputFormat: MediaFormat? = null

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var sampleCursor = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val remainingSamples = pcm.size - sampleCursor
                        if (remainingSamples <= 0) {
                            val ptsUs = (sampleCursor.toLong() * 1_000_000L) / sampleRate.toLong()
                            codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val maxSamplesThisChunk = min(remainingSamples, inBuf.remaining() / 2)
                            for (i in 0 until maxSamplesThisChunk) {
                                val s = pcm[sampleCursor + i].toInt()
                                inBuf.put((s and 0xFF).toByte())
                                inBuf.put(((s shr 8) and 0xFF).toByte())
                            }
                            val ptsUs = (sampleCursor.toLong() * 1_000_000L) / sampleRate.toLong()
                            sampleCursor += maxSamplesThisChunk
                            codec.queueInputBuffer(inIdx, 0, maxSamplesThisChunk * 2, ptsUs, 0)
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = codec.outputFormat
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            outBuf.get(bytes)
                            packets.add(EncodedMediaPacket(true, bytes, info.presentationTimeUs, info.flags))
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            val finalFmt = outputFormat ?: throw IllegalStateException("AAC encoder did not emit output format.")
            return finalFmt to packets
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            try {
                codec.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun drawShortSceneFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: ClipSceneSlide,
        sceneIndex: Int,
        sceneCount: Int,
        globalProgress: Float,
        localSceneProgress: Float,
        audioLevel: Float,
        visualStyle: String,
        backgroundBitmap: Bitmap?,
        hashtagsLine: String
    ) {
        val accentColor = try {
            Color.parseColor(scene.accentHex)
        } catch (_: Exception) {
            Color.parseColor("#00E5FF")
        }

        if (backgroundBitmap != null && !backgroundBitmap.isRecycled) {
            val zoom = 1.05f + 0.14f * localSceneProgress
            val baseScale = maxOf(
                width.toFloat() / backgroundBitmap.width.toFloat(),
                height.toFloat() / backgroundBitmap.height.toFloat()
            ) * zoom
            val scaledW = backgroundBitmap.width * baseScale
            val scaledH = backgroundBitmap.height * baseScale
            val panOffset = sin(globalProgress * Math.PI * 2.0).toFloat() * 28f
            val dx = (width - scaledW) * 0.5f + panOffset
            val dy = (height - scaledH) * 0.45f
            val matrix = Matrix().apply {
                postScale(baseScale, baseScale)
                postTranslate(dx, dy)
            }
            canvas.drawBitmap(backgroundBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

            val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(
                        Color.argb(180, 6, 9, 18),
                        Color.argb(145, 9, 14, 28),
                        Color.argb(235, 5, 8, 16)
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        } else {
            val palette = when (visualStyle) {
                "Crimson Viral" -> intArrayOf(Color.parseColor("#1A040A"), Color.parseColor("#2B0714"), Color.parseColor("#080911"))
                "Cinematic Gold" -> intArrayOf(Color.parseColor("#141006"), Color.parseColor("#241C0C"), Color.parseColor("#090C14"))
                "Emerald Matrix" -> intArrayOf(Color.parseColor("#041612"), Color.parseColor("#0A2922"), Color.parseColor("#070B12"))
                else -> intArrayOf(Color.parseColor("#070A12"), Color.parseColor("#111B34"), Color.parseColor("#090D18"))
            }
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    palette, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                alpha = 46
            }
            val orb1Y = 300f + sin(globalProgress * Math.PI * 4.0).toFloat() * 85f
            val orb2X = width * 0.25f + cos(globalProgress * Math.PI * 3.0).toFloat() * 70f
            canvas.drawCircle(width * 0.78f, orb1Y, 240f, orbPaint)
            orbPaint.alpha = 28
            canvas.drawCircle(orb2X, height * 0.72f, 190f, orbPaint)

            val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 55
            }
            for (p in 0 until 14) {
                val px = ((p * 97 + globalProgress * 260f) % width)
                val py = ((p * 151 - globalProgress * 420f + height * 2) % height)
                canvas.drawCircle(px, py, (2.5f + (p % 4)), particlePaint)
            }
        }

        // Top progress bar
        val barTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E3B59") }
        val barFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRoundRect(RectF(48f, 62f, width - 48f, 74f), 6f, 6f, barTrack)
        canvas.drawRoundRect(RectF(48f, 62f, 48f + (width - 96f) * globalProgress, 74f), 6f, 6f, barFill)

        // Scene badge pill
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 48
        }
        canvas.drawRoundRect(RectF(56f, 122f, 380f, 178f), 28f, 28f, badgePaint)

        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 23f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText(
            "${scene.badge.uppercase().take(14)} • ${sceneIndex + 1}/$sceneCount",
            78f,
            159f,
            badgeTextPaint
        )

        // Kinetic headline
        val popScale = (0.92f + 0.08f * min(1f, localSceneProgress * 4.5f))
        val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(12f, 0f, 4f, Color.argb(170, 0, 0, 0))
        }
        val headlineLayout = StaticLayout.Builder
            .obtain(scene.headline, 0, scene.headline.length, headlinePaint, width - 112)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1.05f)
            .build()

        canvas.save()
        canvas.translate(56f, 300f)
        canvas.scale(popScale, popScale, (width - 112) / 2f, headlineLayout.height / 2f)
        headlineLayout.draw(canvas)
        canvas.restore()

        // Dynamic word-by-word caption card
        val spokenLine = scene.narration.ifBlank { scene.subtitle }
        val words = spokenLine.split(Regex("\\s+")).filter { it.isNotBlank() }
        val activeWordIdx = if (words.isNotEmpty()) {
            (localSceneProgress * words.size).toInt().coerceIn(0, words.lastIndex)
        } else {
            0
        }
        val captionDisplay = if (words.isNotEmpty()) {
            words.mapIndexed { idx, w -> if (idx == activeWordIdx) "[${w.uppercase()}]" else w }.joinToString(" ")
        } else {
            scene.subtitle
        }

        val cardTop = 340f + headlineLayout.height + 36f
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#141D32")
            alpha = 228
        }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 110
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EAF2FF")
            textSize = 32f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subLayout = StaticLayout.Builder
            .obtain(captionDisplay, 0, captionDisplay.length, subtitlePaint, width - 176)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1.16f)
            .build()

        val cardRect = RectF(56f, cardTop, width - 56f, cardTop + subLayout.height + 64f)
        canvas.drawRoundRect(cardRect, 28f, 28f, cardPaint)
        canvas.drawRoundRect(cardRect, 28f, 28f, cardStroke)
        canvas.save()
        canvas.translate(88f, cardTop + 32f)
        subLayout.draw(canvas)
        canvas.restore()

        // Audio-reactive equalizer bars
        val eqBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 210
        }
        val barCount = 18
        val startX = 56f
        val barW = 10f
        val gap = 8f
        val baseLineY = height - 180f
        for (b in 0 until barCount) {
            val wave = abs(sin((globalProgress * 22f + b * 0.55f).toDouble())).toFloat()
            val barH = (10f + 56f * audioLevel * wave).coerceIn(8f, 64f)
            canvas.drawRoundRect(
                RectF(startX + b * (barW + gap), baseLineY - barH, startX + b * (barW + gap) + barW, baseLineY),
                5f, 5f, eqBarPaint
            )
        }

        // Footer FYP hashtags & creator watermark
        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(hashtagsLine.take(44), 56f, height - 122f, tagPaint)

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF1744")
            textSize = 23f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("SUBSCRIBE • youtube.com/@arnold60011", 56f, height - 76f, footerPaint)
    }
}
