package com.example.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "RenderFfmpegClient"
private const val PREFS_NAME = "nexus_render_pipeline_prefs"
private const val KEY_RENDER_BASE_URL = "render_base_url"

data class RenderHealthStatus(
    val isOnline: Boolean,
    val summary: String,
    val baseUrl: String
)

/**
 * Client for the Nexus Render Cloud FFmpeg + yt-dlp Video Processing Pipeline (`render-backend/`).
 * Supports clipping both external URLs (including YouTube & direct video streams) and uploaded
 * local video files using server-side FFmpeg when a Render deployment URL is configured.
 */
object RenderFfmpegClient {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .build()
    }

    fun getSavedRenderUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_RENDER_BASE_URL, null)?.trim().orEmpty()
        return normalizeBaseUrl(saved)
    }

    fun saveRenderUrl(context: Context, rawUrl: String): String {
        val normalized = normalizeBaseUrl(rawUrl)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RENDER_BASE_URL, normalized)
            .apply()
        return normalized
    }

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    suspend fun checkHealth(baseUrl: String): RenderHealthStatus = withContext(Dispatchers.IO) {
        val cleanBase = normalizeBaseUrl(baseUrl)
        if (cleanBase.isEmpty()) {
            return@withContext RenderHealthStatus(
                isOnline = false,
                summary = "On-Device Media3 Pipeline Active (No Render Cloud URL set)",
                baseUrl = ""
            )
        }
        try {
            val req = Request.Builder()
                .url("$cleanBase/api/health")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext RenderHealthStatus(
                        isOnline = false,
                        summary = "Render HTTP ${resp.code}: verify deployment finished waking up",
                        baseUrl = cleanBase
                    )
                }
                val json = JSONObject(body)
                val ffmpegVer = json.optString("ffmpeg", "FFmpeg Ready")
                val ytdlpVer = json.optString("ytdlp", "yt-dlp Ready")
                RenderHealthStatus(
                    isOnline = true,
                    summary = "Connected • $ffmpegVer • yt-dlp $ytdlpVer",
                    baseUrl = cleanBase
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Render health check failed for $cleanBase: ${e.message}")
            RenderHealthStatus(
                isOnline = false,
                summary = "Unreachable (${e.localizedMessage ?: "check Render URL"})",
                baseUrl = cleanBase
            )
        }
    }

    suspend fun clipExternalUrlViaRender(
        context: Context,
        baseUrl: String,
        targetVideoUrl: String,
        startSec: Int,
        durationSec: Int,
        cropVertical916: Boolean
    ): File = withContext(Dispatchers.IO) {
        val cleanBase = normalizeBaseUrl(baseUrl)
        require(cleanBase.isNotEmpty()) { "Render Cloud URL is not configured." }

        val payload = JSONObject().apply {
            put("url", targetVideoUrl.trim())
            put("startSec", startSec.coerceAtLeast(0))
            put("durationSec", durationSec.coerceIn(4, 60))
            put("cropVertical", cropVertical916)
        }

        val req = Request.Builder()
            .url("$cleanBase/api/clip-url")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val downloadEndpoint = client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val errMsg = try {
                    JSONObject(raw).optString("error").ifBlank { "Render FFmpeg HTTP ${resp.code}" }
                } catch (_: Exception) {
                    "Render FFmpeg HTTP ${resp.code}"
                }
                throw IllegalStateException(errMsg)
            }
            val json = JSONObject(raw)
            val clipPath = json.optString("downloadUrl").ifBlank { json.optString("clipUrl") }
            if (clipPath.startsWith("http://") || clipPath.startsWith("https://")) {
                clipPath
            } else {
                "$cleanBase/${clipPath.removePrefix("/")}"
            }
        }

        downloadClipMp4ToLocalDir(context, downloadEndpoint)
    }

    suspend fun clipUploadedVideoViaRender(
        context: Context,
        baseUrl: String,
        sourceUri: Uri,
        startSec: Int,
        durationSec: Int,
        cropVertical916: Boolean
    ): File = withContext(Dispatchers.IO) {
        val cleanBase = normalizeBaseUrl(baseUrl)
        require(cleanBase.isNotEmpty()) { "Render Cloud URL is not configured." }

        val streamBody = object : RequestBody() {
            override fun contentType() = "video/mp4".toMediaType()
            override fun writeTo(sink: BufferedSink) {
                val input = context.contentResolver.openInputStream(sourceUri)
                    ?: throw IllegalStateException("Cannot open local video stream for upload.")
                input.use { stream ->
                    sink.writeAll(stream.source())
                }
            }
        }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("startSec", startSec.coerceAtLeast(0).toString())
            .addFormDataPart("durationSec", durationSec.coerceIn(4, 60).toString())
            .addFormDataPart("cropVertical", cropVertical916.toString())
            .addFormDataPart("video", "upload_${System.currentTimeMillis()}.mp4", streamBody)
            .build()

        val req = Request.Builder()
            .url("$cleanBase/api/clip-upload")
            .post(multipart)
            .build()

        val downloadEndpoint = client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val errMsg = try {
                    JSONObject(raw).optString("error").ifBlank { "Render Upload FFmpeg HTTP ${resp.code}" }
                } catch (_: Exception) {
                    "Render Upload FFmpeg HTTP ${resp.code}"
                }
                throw IllegalStateException(errMsg)
            }
            val json = JSONObject(raw)
            val clipPath = json.optString("downloadUrl").ifBlank { json.optString("clipUrl") }
            if (clipPath.startsWith("http://") || clipPath.startsWith("https://")) {
                clipPath
            } else {
                "$cleanBase/${clipPath.removePrefix("/")}"
            }
        }

        downloadClipMp4ToLocalDir(context, downloadEndpoint)
    }

    private fun downloadClipMp4ToLocalDir(context: Context, mp4Url: String): File {
        val clipsDir = File(context.applicationContext.filesDir, "clips").apply { mkdirs() }
        val outputFile = File(clipsDir, "nexus_ffmpeg_${UUID.randomUUID().toString().take(8)}.mp4")
        val req = Request.Builder().url(mp4Url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Failed to fetch rendered FFmpeg clip (HTTP ${resp.code}).")
            }
            val body = resp.body ?: throw IllegalStateException("Empty MP4 stream from Render FFmpeg.")
            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        if (!outputFile.exists() || outputFile.length() < 512L) {
            outputFile.delete()
            throw IllegalStateException("Downloaded Render clip was invalid or empty.")
        }
        return outputFile
    }
}
