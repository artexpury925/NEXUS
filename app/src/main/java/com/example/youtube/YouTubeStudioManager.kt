package com.example.youtube

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "YouTubeStudioManager"

data class ConnectedYouTubeChannel(
    val id: String,
    val title: String,
    val handle: String,
    val subscriberCount: String
)

object YouTubeStudioManager {

    private const val SCOPE_YOUTUBE_UPLOAD = "https://www.googleapis.com/auth/youtube.upload"
    private const val SCOPE_YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun requestYouTubeAuthorization(context: Context): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(SCOPE_YOUTUBE_UPLOAD),
                    Scope(SCOPE_YOUTUBE_READONLY)
                )
            )
            .build()
        return Identity.getAuthorizationClient(context).authorize(request).await()
    }

    fun extractAuthorizationFromIntent(context: Context, data: Intent?): AuthorizationResult {
        return Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
    }

    suspend fun fetchMyChannelInfo(accessToken: String): ConnectedYouTubeChannel = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.e(TAG, "YouTube channel fetch HTTP ${resp.code}: $bodyStr")
                throw IllegalStateException(parseYouTubeApiError(resp.code, bodyStr))
            }
            val root = JSONObject(bodyStr)
            val items = root.optJSONArray("items")
            if (items == null || items.length() == 0) {
                return@withContext ConnectedYouTubeChannel(
                    id = "linked",
                    title = "YouTube Account Connected",
                    handle = "@creator",
                    subscriberCount = "0"
                )
            }
            val item = items.getJSONObject(0)
            val snippet = item.optJSONObject("snippet")
            val stats = item.optJSONObject("statistics")
            ConnectedYouTubeChannel(
                id = item.optString("id", ""),
                title = snippet?.optString("title", "My YouTube Channel") ?: "My YouTube Channel",
                handle = snippet?.optString("customUrl", "@youtube") ?: "@youtube",
                subscriberCount = stats?.optString("subscriberCount", "0") ?: "0"
            )
        }
    }

    /**
     * Uploads an MP4 video directly to the user's connected YouTube channel using
     * YouTube Data API v3 Resumable Upload protocol.
     */
    suspend fun uploadClipToYouTube(
        accessToken: String,
        videoFile: File,
        title: String,
        description: String,
        tags: List<String>,
        privacyStatus: String = "public"
    ): String = withContext(Dispatchers.IO) {
        if (!videoFile.exists()) {
            throw IllegalStateException("Clip MP4 file not found on device. Please regenerate the clip.")
        }

        val fullDescription = buildString {
            append(description.trim())
            if (!description.contains("#shorts", ignoreCase = true)) {
                append("\n\n#shorts #fyp #viral")
            }
        }

        val metadataJson = JSONObject().apply {
            put(
                "snippet",
                JSONObject().apply {
                    put("title", title.take(95))
                    put("description", fullDescription.take(4900))
                    put("tags", JSONArray(tags.take(15)))
                    put("categoryId", "22") // People & Blogs / Creators
                }
            )
            put(
                "status",
                JSONObject().apply {
                    put("privacyStatus", privacyStatus)
                    put("selfDeclaredMadeForKids", false)
                }
            )
        }

        val initReq = Request.Builder()
            .url("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("X-Upload-Content-Length", videoFile.length().toString())
            .addHeader("X-Upload-Content-Type", "video/mp4")
            .post(metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val uploadUrl = httpClient.newCall(initReq).execute().use { initResp ->
            val bodyStr = initResp.body?.string().orEmpty()
            if (!initResp.isSuccessful) {
                Log.e(TAG, "YouTube resumable init HTTP ${initResp.code}: $bodyStr")
                throw IllegalStateException(parseYouTubeApiError(initResp.code, bodyStr))
            }
            initResp.header("Location")
                ?: throw IllegalStateException("YouTube upload session did not return a Location URL.")
        }

        val uploadReq = Request.Builder()
            .url(uploadUrl)
            .put(videoFile.asRequestBody("video/mp4".toMediaType()))
            .build()

        httpClient.newCall(uploadReq).execute().use { upResp ->
            val bodyStr = upResp.body?.string().orEmpty()
            if (!upResp.isSuccessful) {
                Log.e(TAG, "YouTube binary upload HTTP ${upResp.code}: $bodyStr")
                throw IllegalStateException(parseYouTubeApiError(upResp.code, bodyStr))
            }
            val uploadedJson = JSONObject(bodyStr)
            uploadedJson.optString("id").ifBlank {
                throw IllegalStateException("Upload succeeded without returning a YouTube video ID.")
            }
        }
    }

    /**
     * Shares the MP4 clip directly to the YouTube app (or Android share sheet) via FileProvider
     * and copies the SEO title + FYP hashtags to clipboard for instant pasting.
     */
    fun shareClipToYouTubeApp(
        context: Context,
        videoFile: File,
        title: String,
        description: String,
        tags: List<String>
    ): Boolean {
        if (!videoFile.exists()) return false
        return try {
            val hashtagStr = tags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" }
            val readyCaption = "$title\n\n$description\n\n$hashtagStr #shorts #fyp"
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("YouTube Clip Metadata", readyCaption))

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                videoFile
            )

            val ytIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, readyCaption)
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (ytIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(ytIntent)
            } else {
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TITLE, title)
                        putExtra(Intent.EXTRA_TEXT, readyCaption)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Post Clip to YouTube"
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share clip to YouTube app", e)
            false
        }
    }

    suspend fun fetchYouTubeVideoOEmbed(videoUrl: String): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(videoUrl.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                .header("User-Agent", "Mozilla/5.0 (Android; NexusStudio/2.0)")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext "YouTube Source Video" to "YouTube Creator"
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                val title = json.optString("title", "YouTube Source Video")
                val author = json.optString("author_name", "YouTube Creator")
                title to author
            }
        } catch (e: Exception) {
            Log.w(TAG, "oEmbed lookup fallback for $videoUrl: ${e.message}")
            "YouTube Video (${videoUrl.takeLast(14)})" to "YouTube Creator"
        }
    }

    suspend fun fetchWebsiteSummaryForClone(targetUrl: String): String = withContext(Dispatchers.IO) {
        val normalized = if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            targetUrl.trim()
        } else {
            "https://${targetUrl.trim()}"
        }
        try {
            val req = Request.Builder()
                .url(normalized)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val html = resp.body?.string().orEmpty().take(40_000)
                if (html.isBlank()) return@withContext "Target URL: $normalized"
                val titleMatch = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                val descMatch = Regex("<meta[^>]+name=[\"']description[\"'][^>]+content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                val headings = Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .findAll(html)
                    .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim() }
                    .filter { it.length in 3..90 }
                    .distinct()
                    .take(8)
                    .joinToString(" | ")
                buildString {
                    appendLine("URL: $normalized")
                    if (titleMatch.isNotBlank()) appendLine("Page Title: ${titleMatch.take(140)}")
                    if (descMatch.isNotBlank()) appendLine("Meta Description: ${descMatch.take(260)}")
                    if (headings.isNotBlank()) appendLine("Detected Sections/Headings: $headings")
                }.take(1400)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Website summary fallback for $normalized: ${e.message}")
            "Target Website URL: $normalized"
        }
    }

    fun isLikelyYouTubeUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.contains("youtube.com/") || lower.contains("youtu.be/")
    }

    fun extractYouTubeVideoId(url: String): String? {
        val trimmed = url.trim()
        val patterns = listOf(
            Regex("(?:v=|/shorts/|/embed/|youtu\\.be/)([A-Za-z0-9_-]{11})"),
            Regex("^([A-Za-z0-9_-]{11})$")
        )
        for (regex in patterns) {
            val id = regex.find(trimmed)?.groupValues?.getOrNull(1)
            if (!id.isNullOrBlank()) return id
        }
        return null
    }

    suspend fun fetchYouTubeThumbnailBitmap(videoUrl: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val videoId = extractYouTubeVideoId(videoUrl) ?: return@withContext null
        val candidates = listOf(
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            "https://img.youtube.com/vi/$videoId/0.jpg"
        )
        for (thumbUrl in candidates) {
            try {
                val req = Request.Builder()
                    .url(thumbUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android; NexusStudio/2.0)")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) return@withContext bmp
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Thumbnail fetch fallback for $thumbUrl: ${e.message}")
            }
        }
        null
    }

    /**
     * Downloads a direct video URL (.mp4, .mov, .webm, or HTTP video stream up to 75MB)
     * into app cache so Nexus can inspect real frames and losslessly trim clips.
     */
    suspend fun downloadDirectVideoToCache(context: Context, videoUrl: String): File = withContext(Dispatchers.IO) {
        val normalized = if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
            videoUrl.trim()
        } else {
            "https://${videoUrl.trim()}"
        }
        val req = Request.Builder()
            .url(normalized)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            .get()
            .build()

        val tempDir = File(context.cacheDir, "remote_videos").apply { mkdirs() }
        val targetFile = File(tempDir, "remote_${System.currentTimeMillis()}.mp4")
        val maxBytes = 75L * 1024L * 1024L

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Could not download video link (HTTP ${resp.code}). Make sure the link is publicly accessible.")
            }
            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            val lowerUrl = normalized.lowercase().substringBefore("?")
            val looksVideoExt = lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".mov") || lowerUrl.endsWith(".m4v") || lowerUrl.endsWith(".webm")
            if (contentType.contains("text/html") && !looksVideoExt) {
                throw IllegalStateException("This webpage does not expose a direct MP4 stream. Paste a YouTube URL or a direct .mp4 video link, or upload the video file from your device.")
            }
            val body = resp.body ?: throw IllegalStateException("Remote server returned an empty video stream.")
            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > maxBytes) {
                            targetFile.delete()
                            throw IllegalStateException("Remote video exceeds 75MB direct-clip limit. Upload locally or use a shorter source.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
        if (!targetFile.exists() || targetFile.length() < 1024L) {
            targetFile.delete()
            throw IllegalStateException("Downloaded file was empty or not a valid MP4 video.")
        }
        targetFile
    }

    private fun parseYouTubeApiError(code: Int, rawJson: String): String {
        return try {
            val msg = JSONObject(rawJson).optJSONObject("error")?.optString("message").orEmpty()
            if (msg.isNotBlank()) "YouTube API ($code): $msg" else "YouTube API HTTP $code"
        } catch (_: Exception) {
            "YouTube API HTTP $code"
        }
    }
}
