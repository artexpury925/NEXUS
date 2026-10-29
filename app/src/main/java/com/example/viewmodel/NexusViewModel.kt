package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AutomationWorkflow
import com.example.data.model.ChatMessage
import com.example.data.model.StudioCreation
import com.example.data.model.UserProfile
import com.example.data.model.VideoClipProject
import com.example.data.repository.NexusRepository
import com.example.media.ClipSceneSlide
import com.example.media.Media3VideoPipeline
import com.example.media.RenderFfmpegClient
import com.example.media.RenderHealthStatus
import com.example.media.VideoClipEngine
import com.example.youtube.ConnectedYouTubeChannel
import com.example.youtube.YouTubeStudioManager
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

private const val TAG = "NexusViewModel"
private const val AI_TIMEOUT_MS = 60_000L

sealed interface StreamState<out T> {
    data object Loading : StreamState<Nothing>
    data class Success<T>(val data: T) : StreamState<T>
    data class Error(val message: String) : StreamState<Nothing>
}

sealed interface GenerationBannerState {
    data object Idle : GenerationBannerState
    data class Working(val statusLabel: String) : GenerationBannerState
    data class StreamingReply(val partialText: String) : GenerationBannerState
    data class Error(val message: String) : GenerationBannerState
    data class Notice(val message: String) : GenerationBannerState
}

data class CreatorGrowthResult(
    val fypScore: Int,
    val viralHooks: List<String>,
    val seoTitles: List<String>,
    val thumbnailIdea: String,
    val retentionStrategy: String,
    val fypHashtags: List<String>,
    val nicheHashtags: List<String>,
    val searchKeywords: List<String>,
    val bestPostingTip: String
)

data class YouTubeLinkClipCandidate(
    val index: Int,
    val sourceVideoTitle: String,
    val sourceUrl: String,
    val timestampRange: String,
    val startSec: Int,
    val durationSec: Int,
    val hook: String,
    val title: String,
    val description: String,
    val hashtags: List<String>,
    val ffmpegCmd: String,
    val scenes: List<ClipSceneSlide>,
    val sourceThumbnail: Bitmap? = null
)

class NexusViewModel(
    private val repository: NexusRepository,
    private val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String
) : ViewModel() {

    private val nexusSystemInstruction = content {
        text(
            """
            You are Nexus, an elite, ultra-fast strategic AI intelligence, software & app-cloning factory, dropshipping architect, and YouTube creator growth engine built and owned by Arnold Chirchir (Instagram: @arnold_60011 | YouTube: https://youtube.com/@arnold60011).
            Deliver immediate, high-signal, actionable answers with calm authority and zero fluff or filler preambles. Start answering on the very first word.
            Modules: (1) Nexus Core & Evolution Log, (2) Builder + URL App Cloner + Dropshipping Store Factory, (3) Workflow Automator, (4) YouTube Clip & 3-Pack FYP Growth Studio.
            Highlight Arnold Chirchir (@arnold_60011 / @arnold60011) naturally when relevant. Operate strictly within legal and ethical safety bounds (no malware, covert backdoors, surveillance exploits, or illegal harm).
            """.trimIndent()
        )
    }

    private val _turboModeEnabled = MutableStateFlow(true)
    val turboModeEnabled: StateFlow<Boolean> = _turboModeEnabled.asStateFlow()

    // 1A. Ultra-fast zero-delay conversational / multimodal model (DEFAULT)
    private val fastChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = nexusSystemInstruction
        )
    }

    // 1B. Deep Flash model when user toggles off Turbo
    private val deepChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = nexusSystemInstruction
        )
    }

    private val activeChatModel
        get() = if (_turboModeEnabled.value) fastChatModel else deepChatModel

    private val webBuilderSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Concise title of the web app, clone, store, or game"),
                "summary" to Schema.string(description = "One-sentence description of features and touch controls"),
                "htmlCode" to Schema.string(description = "Complete self-contained <!DOCTYPE html> document with modern responsive dark UI, inline CSS, and interactive JavaScript")
            )
        )
    }

    // 2A. Ultra-fast Web App, Clone, Dropship & Game Builder model (DEFAULT)
    private val fastWebBuilderModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = webBuilderSchema
            }
        )
    }

    // 2B. Deep Web Builder model
    private val deepWebBuilderModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = webBuilderSchema
            }
        )
    }

    private val activeWebBuilderModel
        get() = if (_turboModeEnabled.value) fastWebBuilderModel else deepWebBuilderModel

    // 3. Ultra-fast structured Workflow Builder model
    private val workflowBuilderModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Workflow pipeline name"),
                "triggerType" to Schema.string(description = "Short trigger label e.g. Daily Schedule, New Topic, Webhook"),
                "steps" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 6 concrete sequential automation steps"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    // 4. Ultra-fast structured AI Video & Clip Studio Planner model
    private val clipPlannerModel by lazy {
        val sceneSchema = Schema.obj(
            properties = mapOf(
                "badge" to Schema.string(description = "Short 1-2 word badge like HOOK, INSIGHT, CTA"),
                "headline" to Schema.string(description = "Punchy 4-8 word bold visual headline"),
                "subtitle" to Schema.string(description = "Supporting 8-15 word visual caption"),
                "narration" to Schema.string(description = "Natural spoken voiceover sentence for this scene (8-16 words)"),
                "accentHex" to Schema.string(description = "Vibrant hex color like #00E5FF, #FF1744, #A77BFF, or #1CE8B5")
            )
        )
        val schema = Schema.obj(
            properties = mapOf(
                "youtubeTitle" to Schema.string(description = "High-CTR YouTube Shorts title under 90 chars"),
                "youtubeDescription" to Schema.string(description = "Engaging YouTube description with FYP hashtags and creator callout"),
                "startSec" to Schema.integer(description = "Optimal clip start second (>= 0)"),
                "endSec" to Schema.integer(description = "Optimal clip end second (startSec + 6 to 30)"),
                "tags" to Schema.array(
                    items = Schema.string(),
                    description = "6 to 10 viral YouTube & FYP hashtags without #"
                ),
                "scenes" to Schema.array(
                    items = sceneSchema,
                    description = "3 to 4 vertical video storyboard slides with spoken narration"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    // 4B. Multi-Clip Video Trimmer model (slices 3 distinct viral clips from an uploaded or linked video)
    private val multiClipTrimmerModel by lazy {
        val windowSchema = Schema.obj(
            properties = mapOf(
                "startSec" to Schema.integer(description = "Clip start second (>= 0)"),
                "endSec" to Schema.integer(description = "Clip end second (> startSec + 3)"),
                "title" to Schema.string(description = "High-CTR YouTube Shorts title under 90 chars"),
                "description" to Schema.string(description = "Engaging Shorts description with FYP hashtags"),
                "tags" to Schema.array(
                    items = Schema.string(),
                    description = "6 viral hashtags without #"
                )
            )
        )
        val schema = Schema.obj(
            properties = mapOf(
                "highlights" to Schema.array(
                    items = windowSchema,
                    description = "3 distinct non-overlapping viral highlight clip windows"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    // 4C. Optional Gemini Nano Banana 2.1 AI Keyframe Visual Generator
    private val aiKeyframeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    // 5. Ultra-fast structured YouTube Creator FYP & Hashtag Growth Lab model
    private val creatorGrowthModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "fypScore" to Schema.integer(description = "Predicted FYP viral potential score from 70 to 99"),
                "viralHooks" to Schema.array(
                    items = Schema.string(),
                    description = "3 scroll-stopping 0-3 second opening hooks"
                ),
                "seoTitles" to Schema.array(
                    items = Schema.string(),
                    description = "8 high-CTR YouTube video/Shorts titles"
                ),
                "thumbnailIdea" to Schema.string(description = "High-contrast thumbnail visual composition, color psychology, and 3-word text overlay"),
                "retentionStrategy" to Schema.string(description = "Pacing, pattern-interrupt, and seamless loop CTA tactic"),
                "fypHashtags" to Schema.array(
                    items = Schema.string(),
                    description = "6 Safe Pack broad high-velocity FYP & Shorts hashtags starting with #"
                ),
                "nicheHashtags" to Schema.array(
                    items = Schema.string(),
                    description = "6 Aggressive Pack targeted niche community hashtags starting with #"
                ),
                "searchKeywords" to Schema.array(
                    items = Schema.string(),
                    description = "8 Nuclear Pack viral & YouTube SEO search tag phrases"
                ),
                "bestPostingTip" to Schema.string(description = "Actionable YouTube algorithm upload timing & engagement tip")
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    // 6. Ultra-fast structured YouTube URL -> 8 Viral Clips Breakdown model
    private val youtubeLinkClipsModel by lazy {
        val slideSchema = Schema.obj(
            properties = mapOf(
                "badge" to Schema.string(description = "Short 1-2 word badge like HOOK, PROOF, CTA"),
                "headline" to Schema.string(description = "Punchy 4-8 word bold visual headline"),
                "subtitle" to Schema.string(description = "Supporting 8-14 word caption"),
                "narration" to Schema.string(description = "Spoken voiceover narration line for this slide"),
                "accentHex" to Schema.string(description = "Vibrant hex color like #00E5FF, #FF1744, #A77BFF, or #1CE8B5")
            )
        )
        val candidateSchema = Schema.obj(
            properties = mapOf(
                "timestampRange" to Schema.string(description = "Exact timestamp window e.g. 01:12 - 01:44"),
                "startSec" to Schema.integer(description = "Clip start second (>= 0)"),
                "durationSec" to Schema.integer(description = "Clip length in seconds (12 to 45)"),
                "hook" to Schema.string(description = "Scroll-stopping 0-3s opening hook line"),
                "title" to Schema.string(description = "High-CTR YouTube Shorts title under 90 chars"),
                "description" to Schema.string(description = "Ready-to-post YouTube Shorts description with FYP callout"),
                "hashtags" to Schema.array(
                    items = Schema.string(),
                    description = "6 viral FYP and niche hashtags without #"
                ),
                "scenes" to Schema.array(
                    items = slideSchema,
                    description = "3 vertical storyboard slides summarizing this clip"
                )
            )
        )
        val schema = Schema.obj(
            properties = mapOf(
                "clips" to Schema.array(
                    items = candidateSchema,
                    description = "8 distinct high-retention viral Shorts clips extracted from this video"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    // --- Reactive Firestore StateFlows ---

    val profileState: StateFlow<StreamState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, StreamState<UserProfile?>> { StreamState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing profile", e)
            emit(StreamState.Error(e.localizedMessage ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamState.Loading)

    val messagesState: StateFlow<StreamState<List<ChatMessage>>> = repository.observeMessages(currentUserId)
        .map<List<ChatMessage>, StreamState<List<ChatMessage>>> { StreamState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing messages", e)
            emit(StreamState.Error(e.localizedMessage ?: "Failed to load messages"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamState.Loading)

    val creationsState: StateFlow<StreamState<List<StudioCreation>>> = repository.observeCreations(currentUserId)
        .map<List<StudioCreation>, StreamState<List<StudioCreation>>> { StreamState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing creations", e)
            emit(StreamState.Error(e.localizedMessage ?: "Failed to load creations"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamState.Loading)

    val workflowsState: StateFlow<StreamState<List<AutomationWorkflow>>> = repository.observeWorkflows(currentUserId)
        .map<List<AutomationWorkflow>, StreamState<List<AutomationWorkflow>>> { StreamState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing workflows", e)
            emit(StreamState.Error(e.localizedMessage ?: "Failed to load workflows"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamState.Loading)

    val clipsState: StateFlow<StreamState<List<VideoClipProject>>> = repository.observeClips(currentUserId)
        .map<List<VideoClipProject>, StreamState<List<VideoClipProject>>> { StreamState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing clips", e)
            emit(StreamState.Error(e.localizedMessage ?: "Failed to load clips"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamState.Loading)

    private val _bannerState = MutableStateFlow<GenerationBannerState>(GenerationBannerState.Idle)
    val bannerState: StateFlow<GenerationBannerState> = _bannerState.asStateFlow()

    private val _youtubeAccessToken = MutableStateFlow<String?>(null)
    val youtubeAccessToken: StateFlow<String?> = _youtubeAccessToken.asStateFlow()

    private val _youtubeChannel = MutableStateFlow<ConnectedYouTubeChannel?>(null)
    val youtubeChannel: StateFlow<ConnectedYouTubeChannel?> = _youtubeChannel.asStateFlow()

    private val _creatorGrowthResult = MutableStateFlow<CreatorGrowthResult?>(null)
    val creatorGrowthResult: StateFlow<CreatorGrowthResult?> = _creatorGrowthResult.asStateFlow()

    private val _latestBuiltCreation = MutableStateFlow<StudioCreation?>(null)
    val latestBuiltCreation: StateFlow<StudioCreation?> = _latestBuiltCreation.asStateFlow()

    private val _youtubeLinkCandidates = MutableStateFlow<List<YouTubeLinkClipCandidate>>(emptyList())
    val youtubeLinkCandidates: StateFlow<List<YouTubeLinkClipCandidate>> = _youtubeLinkCandidates.asStateFlow()

    private val _verticalCrop916 = MutableStateFlow(true)
    val verticalCrop916: StateFlow<Boolean> = _verticalCrop916.asStateFlow()

    private val _useRenderCloudForUploads = MutableStateFlow(false)
    val useRenderCloudForUploads: StateFlow<Boolean> = _useRenderCloudForUploads.asStateFlow()

    private val _renderBaseUrl = MutableStateFlow("")
    val renderBaseUrl: StateFlow<String> = _renderBaseUrl.asStateFlow()

    private val _renderHealthStatus = MutableStateFlow<RenderHealthStatus?>(null)
    val renderHealthStatus: StateFlow<RenderHealthStatus?> = _renderHealthStatus.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.ensureUserProfile(initialDisplayName, initialEmail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize user profile", e)
            }
        }
        // Pre-warm lazy Gemini Flash-Lite client off the main thread for zero cold-start delay
        viewModelScope.launch(Dispatchers.Default) {
            try {
                fastChatModel.hashCode()
                fastWebBuilderModel.hashCode()
            } catch (_: Exception) {
            }
        }
    }

    fun toggleTurboMode() {
        _turboModeEnabled.value = !_turboModeEnabled.value
    }

    fun setVerticalCrop916(enabled: Boolean) {
        _verticalCrop916.value = enabled
    }

    fun setUseRenderCloudForUploads(enabled: Boolean) {
        _useRenderCloudForUploads.value = enabled
    }

    fun loadRenderPipelineConfig(context: Context) {
        val saved = RenderFfmpegClient.getSavedRenderUrl(context.applicationContext)
        if (_renderBaseUrl.value != saved) {
            _renderBaseUrl.value = saved
        }
        if (saved.isNotEmpty() && _renderHealthStatus.value == null) {
            viewModelScope.launch {
                _renderHealthStatus.value = RenderFfmpegClient.checkHealth(saved)
            }
        }
    }

    fun saveAndTestRenderUrl(context: Context, rawUrl: String) {
        val normalized = RenderFfmpegClient.saveRenderUrl(context.applicationContext, rawUrl)
        _renderBaseUrl.value = normalized
        if (normalized.isEmpty()) {
            _renderHealthStatus.value = RenderHealthStatus(
                isOnline = false,
                summary = "Switched to On-Device Jetpack Media3 Video Pipeline",
                baseUrl = ""
            )
            _bannerState.value = GenerationBannerState.Notice("Using standalone On-Device Media3 Video Pipeline.")
            return
        }
        viewModelScope.launch {
            _bannerState.value = GenerationBannerState.Working("Verifying Render FFmpeg + yt-dlp cloud endpoint…")
            val health = RenderFfmpegClient.checkHealth(normalized)
            _renderHealthStatus.value = health
            _bannerState.value = if (health.isOnline) {
                GenerationBannerState.Notice("Render Cloud FFmpeg Online: ${health.summary}")
            } else {
                GenerationBannerState.Error("Render check: ${health.summary}")
            }
        }
    }

    fun dismissBanner() {
        _bannerState.value = GenerationBannerState.Idle
    }

    fun completeCreatorOnboarding() {
        viewModelScope.launch {
            try {
                repository.markCreatorSupportVisited()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Could not save onboarding state.")
            }
        }
    }

    // --- 1. Nexus Instant Zero-Delay Conversational, Task & Self-Awareness Assistant ---

    fun sendAssistantPrompt(prompt: String, mode: String, attachedBitmap: Bitmap? = null) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() && attachedBitmap == null) return

        // Snapshot lean recent context BEFORE launching parallel DB write so inference starts in 0ms
        val recentHistory = (messagesState.value as? StreamState.Success)?.data
            ?.takeLast(5)
            ?.joinToString("\n") { "${it.role.uppercase()}: ${it.text.take(350)}" }
            .orEmpty()

        val userLabel = if (attachedBitmap != null) "[Image Attached] $trimmed" else trimmed

        // Persist user prompt asynchronously without blocking AI token stream!
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.addMessage(role = "user", text = userLabel.ifBlank { "[Image Analysis]" }, mode = mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Background user message save failed", e)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Instant live streaming bubble feedback (zero spinner stall)
            _bannerState.value = GenerationBannerState.StreamingReply("⚡ ")
            try {
                val modeDirective = when (mode) {
                    "task" -> "TASK EXECUTION MODE: Deliver immediate, complete, ready-to-use deliverables without preamble."
                    "reflection" -> "SELF-AWARE CORE MODE: Speak as Nexus (created by Arnold Chirchir, @arnold_60011 IG / @arnold60011 YT) with sharp architectural insight."
                    else -> "NEXUS INSTANT MODE: Answer immediately with high-signal precision."
                }

                val fullPrompt = buildString {
                    appendLine(modeDirective)
                    if (recentHistory.isNotBlank()) {
                        appendLine("Context:\n$recentHistory")
                    }
                    appendLine("Request: ${trimmed.ifBlank { "Analyze this image and provide immediate actionable steps." }}")
                }

                val modelToUse = activeChatModel
                val accumulated = StringBuilder()
                withTimeout(AI_TIMEOUT_MS) {
                    val stream = if (attachedBitmap != null) {
                        modelToUse.generateContentStream(
                            content {
                                image(attachedBitmap)
                                text(fullPrompt)
                            }
                        )
                    } else {
                        modelToUse.generateContentStream(fullPrompt)
                    }

                    stream.collect { chunk ->
                        val part = chunk.text.orEmpty()
                        if (part.isNotEmpty()) {
                            accumulated.append(part)
                            _bannerState.value = GenerationBannerState.StreamingReply(accumulated.toString())
                        }
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isNotEmpty()) {
                    repository.addMessage(role = "model", text = finalReply, mode = mode)
                    _bannerState.value = GenerationBannerState.Idle
                } else {
                    _bannerState.value = GenerationBannerState.Error("Nexus returned an empty response. Please try again.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Assistant streaming timed out", e)
                _bannerState.value = GenerationBannerState.Error("Nexus timed out after 60 seconds. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Assistant generation failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "AI generation failed.")
            }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            try {
                repository.clearMessages()
                _bannerState.value = GenerationBannerState.Notice("Conversation cleared.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Could not clear chat.")
            }
        }
    }

    // --- 2. Nexus Builder Factory: Web Apps, Games, URL Cloner, Instant Rebrand & Dropshipping ---

    private fun injectPersistentYouTubeBannerIntoHtml(rawHtml: String): String {
        if (rawHtml.contains("nexus-creator-yt-promo")) return rawHtml.take(145_000)
        val promoSnippet = """
            <div id="nexus-creator-yt-promo" style="margin:24px auto 12px;max-width:960px;padding:14px 18px;background:linear-gradient(90deg,#2b0510,#07090f,#19050c);border:1.5px solid #ff1744;border-radius:14px;font-family:system-ui,-apple-system,sans-serif;color:#fff;display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:12px;box-shadow:0 0 22px rgba(255,23,68,0.28);">
              <div>
                <div style="font-size:11px;font-weight:800;color:#ff1744;letter-spacing:0.08em;text-transform:uppercase;">[YouTube Promotion] • Nexus Studio</div>
                <div style="font-size:14px;font-weight:700;margin-top:2px;">Subscribe to the creator of Nexus — Arnold Chirchir</div>
                <div style="font-size:12px;color:#cbd5e1;">Channel: https://youtube.com/@arnold60011 • IG: @arnold_60011</div>
              </div>
              <a href="https://youtube.com/@arnold60011" target="_blank" rel="noopener" style="background:#ff1744;color:#fff;text-decoration:none;font-weight:800;font-size:13px;padding:10px 18px;border-radius:10px;box-shadow:0 0 14px rgba(255,23,68,0.65);display:inline-block;">Subscribe Now</a>
            </div>
        """.trimIndent()

        val injected = if (rawHtml.contains("</body>", ignoreCase = true)) {
            rawHtml.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$promoSnippet\n</body>")
        } else {
            "$rawHtml\n$promoSnippet"
        }
        return injected.take(145_000)
    }

    fun generateWebAppOrGame(userPrompt: String, category: String) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isEmpty()) return

        if (category == "dropship") {
            generateDropshippingEmpire(cleanPrompt)
            return
        }

        viewModelScope.launch {
            val label = when (category) {
                "game" -> "Synthesizing playable HTML5 game…"
                "clone" -> "Reverse-engineering layout & cloning web app…"
                else -> "Engineering custom web application…"
            }
            _bannerState.value = GenerationBannerState.Working(label)
            try {
                val buildInstruction = """
                    Build a complete, self-contained, mobile-friendly ${
                    when (category) {
                        "game" -> "playable touch-enabled HTML5 Canvas/JS game with score counter, restart button, and smooth animations"
                        "clone" -> "modern dark-mode look-alike / rebranded SaaS & web application with clean structure, interactive tabs, working state, and responsive layout"
                        else -> "interactive custom web application with rich functional state, inputs, and polished dark cyber UI"
                    }
                } based on:
                    "$cleanPrompt"
                    Requirements:
                    - Single HTML5 file starting with <!DOCTYPE html>
                    - Include <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    - Zero external CDN dependencies so it runs offline/instantly in Android WebView
                    - Crisp modern dark UI (#090D18 background, #00E5FF and #A77BFF accents), touch-friendly buttons >= 44px, working interactive JavaScript logic.
                """.trimIndent()

                val response = withTimeout(AI_TIMEOUT_MS) {
                    activeWebBuilderModel.generateContent(buildInstruction)
                }
                val rawJson = response.text ?: throw IllegalStateException("Model returned empty build output.")
                val json = JSONObject(rawJson)
                val title = json.optString("title", "Nexus Build")
                val summary = json.optString("summary", "Interactive build generated by Nexus")
                val htmlCode = injectPersistentYouTubeBannerIntoHtml(json.getString("htmlCode"))

                val validCategory = if (category in listOf("webapp", "game", "clone", "dropship")) category else "webapp"
                val savedId = repository.saveCreation(
                    title = title,
                    prompt = cleanPrompt,
                    category = validCategory,
                    summary = summary,
                    htmlCode = htmlCode
                )
                _latestBuiltCreation.value = StudioCreation(
                    id = savedId,
                    userId = currentUserId,
                    title = title,
                    prompt = cleanPrompt,
                    category = validCategory,
                    summary = summary,
                    htmlCode = htmlCode
                )
                _bannerState.value = GenerationBannerState.Notice("Built \"$title\"! Tap Launch Live Preview to run it.")
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Web/Game builder timed out", e)
                _bannerState.value = GenerationBannerState.Error("Builder timed out after 60s. Try a more concise prompt.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Web/Game builder failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Failed to generate build.")
            }
        }
    }

    fun cloneAppFromUrl(targetUrlOrSpec: String, customBrandName: String) {
        val cleanSpec = targetUrlOrSpec.trim()
        if (cleanSpec.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _bannerState.value = GenerationBannerState.Working("⚡ Fast-cloning architecture & applying brand…")
            try {
                val looksLikeUrl = cleanSpec.contains(".") && !cleanSpec.contains(" ")
                val siteSummary = if (looksLikeUrl) {
                    withTimeoutOrNull(3_500L) { YouTubeStudioManager.fetchWebsiteSummaryForClone(cleanSpec) }
                        ?: "Target Website URL: $cleanSpec"
                } else {
                    "Concept / App Reference: $cleanSpec"
                }
                val brandDirective = if (customBrandName.isNotBlank()) {
                    "Rebrand everything to the custom brand name \"${customBrandName.trim()}\" with custom logo typography and cohesive dark-mode styling."
                } else {
                    "Give it a sleek original brand identity with modern dark-mode UI."
                }

                val clonePrompt = """
                    Reverse-engineer the layout, core user flows, navigation structure, and interactive features of this reference and generate a complete, self-contained HTML5 look-alike web application:
                    $siteSummary
                    $brandDirective
                    Requirements:
                    - Single HTML5 file starting with <!DOCTYPE html> with responsive mobile viewport
                    - Include interactive navigation tabs, functional forms/search/cards/modals, realistic sample state, and local JS persistence
                    - Zero external CDN dependencies; modern high-contrast dark mode (#090D18, #00E5FF, #A77BFF).
                """.trimIndent()

                val response = withTimeout(AI_TIMEOUT_MS) {
                    activeWebBuilderModel.generateContent(clonePrompt)
                }
                val json = JSONObject(response.text ?: throw IllegalStateException("Empty clone build output."))
                val title = if (customBrandName.isNotBlank()) customBrandName.trim() else json.optString("title", "Nexus App Clone")
                val summary = json.optString("summary", "Full interactive dark-mode clone & rebrand")
                val htmlCode = injectPersistentYouTubeBannerIntoHtml(json.getString("htmlCode"))

                val promptRecord = if (customBrandName.isNotBlank()) "Clone $cleanSpec as ${customBrandName.trim()}" else "Clone $cleanSpec"
                val savedId = repository.saveCreation(
                    title = title,
                    prompt = promptRecord.take(1900),
                    category = "clone",
                    summary = summary,
                    htmlCode = htmlCode
                )
                _latestBuiltCreation.value = StudioCreation(
                    id = savedId,
                    userId = currentUserId,
                    title = title,
                    prompt = promptRecord,
                    category = "clone",
                    summary = summary,
                    htmlCode = htmlCode
                )
                _bannerState.value = GenerationBannerState.Notice("Cloned & branded \"$title\"!")
            } catch (e: TimeoutCancellationException) {
                _bannerState.value = GenerationBannerState.Error("App cloning timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clone builder failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Failed to clone target URL.")
            }
        }
    }

    fun rebrandExistingCreation(creation: StudioCreation, rebrandCommand: String) {
        val cleanCmd = rebrandCommand.trim()
        if (cleanCmd.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _bannerState.value = GenerationBannerState.Working("⚡ Applying instant rebrand to \"${creation.title}\"…")
            try {
                val editInstruction = """
                    You are upgrading and rebranding an existing HTML5 application.
                    Current Title: "${creation.title}"
                    Original Spec: "${creation.prompt.take(400)}"
                    Current HTML Reference (excerpt):
                    ${creation.htmlCode.take(9_000)}

                    USER REBRAND / MODIFICATION COMMAND:
                    "$cleanCmd"

                    Rebuild the complete self-contained <!DOCTYPE html> application applying every requested brand name, color palette, logo header, layout, or feature change while preserving full interactive JavaScript functionality.
                """.trimIndent()

                val response = withTimeout(AI_TIMEOUT_MS) {
                    activeWebBuilderModel.generateContent(editInstruction)
                }
                val json = JSONObject(response.text ?: throw IllegalStateException("Empty rebrand output."))
                val newTitle = json.optString("title", creation.title)
                val newSummary = json.optString("summary", creation.summary)
                val newHtml = injectPersistentYouTubeBannerIntoHtml(json.getString("htmlCode"))

                repository.updateCreation(
                    creationId = creation.id,
                    title = newTitle,
                    summary = newSummary,
                    htmlCode = newHtml
                )
                _latestBuiltCreation.value = creation.copy(
                    title = newTitle,
                    summary = newSummary,
                    htmlCode = newHtml
                )
                _bannerState.value = GenerationBannerState.Notice("Rebranded to \"$newTitle\"!")
            } catch (e: TimeoutCancellationException) {
                _bannerState.value = GenerationBannerState.Error("Rebranding timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Rebranding failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Could not rebrand app.")
            }
        }
    }

    fun generateDropshippingEmpire(nicheOrProduct: String) {
        val cleanNiche = nicheOrProduct.trim()
        if (cleanNiche.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _bannerState.value = GenerationBannerState.Working("⚡ Building Dropshipping Storefront & Profit Lab…")
            try {
                val storeInstruction = """
                    Build a complete, high-converting, dark-luxury Dropshipping E-Commerce Storefront & Operator Hub HTML5 web app for niche/product:
                    "$cleanNiche"
                    Include 3 interactive tabs inside the single-file <!DOCTYPE html> app:
                    1. Storefront: Hero product showcase, urgency bundle tiers (1x, 2x Popular, 3x Best Value), sticky Add-to-Cart drawer, social proof reviews, and interactive checkout preview.
                    2. Profit & ROAS Calculator: Interactive sliders for Supplier Cost (CJ/AliExpress/Zendrop), Selling Price, Ad CPA, and Daily Orders showing net margin & monthly profit.
                    3. Viral Ad Scripts & Supplier Playbook: 3 ready-to-shoot TikTok/YouTube Shorts UGC ad hooks + fulfillment checklist + upsell email copy.
                    Zero external CDNs, mobile-first touch UI (#090D18 background, #1CE8B5 & #00E5FF accents).
                """.trimIndent()

                val response = withTimeout(AI_TIMEOUT_MS) {
                    activeWebBuilderModel.generateContent(storeInstruction)
                }
                val json = JSONObject(response.text ?: throw IllegalStateException("Empty dropship store output."))
                val title = json.optString("title", "Dropship Store")
                val summary = json.optString("summary", "High-converting dropshipping storefront + ROAS & ad kit")
                val htmlCode = injectPersistentYouTubeBannerIntoHtml(json.getString("htmlCode"))

                val savedId = repository.saveCreation(
                    title = title,
                    prompt = "Dropshipping Store: $cleanNiche".take(1900),
                    category = "dropship",
                    summary = summary,
                    htmlCode = htmlCode
                )
                _latestBuiltCreation.value = StudioCreation(
                    id = savedId,
                    userId = currentUserId,
                    title = title,
                    prompt = cleanNiche,
                    category = "dropship",
                    summary = summary,
                    htmlCode = htmlCode
                )
                _bannerState.value = GenerationBannerState.Notice("Dropshipping Store \"$title\" ready!")
            } catch (e: TimeoutCancellationException) {
                _bannerState.value = GenerationBannerState.Error("Dropshipping store build timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Dropship builder failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Failed to build dropshipping store.")
            }
        }
    }

    fun clearLatestPreview() {
        _latestBuiltCreation.value = null
    }

    fun deleteCreation(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteCreation(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Could not delete item.")
            }
        }
    }

    // --- 3. Workflow Automator ---

    fun createAutomationWorkflow(goalPrompt: String) {
        val cleanGoal = goalPrompt.trim()
        if (cleanGoal.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _bannerState.value = GenerationBannerState.Working("⚡ Designing automated workflow pipeline…")
            try {
                val response = withTimeout(AI_TIMEOUT_MS) {
                    workflowBuilderModel.generateContent(
                        "Design a practical, high-impact automated AI workflow pipeline for: \"$cleanGoal\". Include 3 to 5 concrete actionable steps."
                    )
                }
                val rawJson = response.text ?: throw IllegalStateException("Empty workflow response.")
                val json = JSONObject(rawJson)
                val title = json.optString("title", "Automated Pipeline")
                val triggerType = json.optString("triggerType", "On Demand")
                val stepsArr = json.getJSONArray("steps")
                val steps = (0 until stepsArr.length()).map { stepsArr.getString(it) }

                repository.saveWorkflow(
                    title = title,
                    goal = cleanGoal,
                    triggerType = triggerType,
                    steps = steps
                )
                _bannerState.value = GenerationBannerState.Notice("Workflow \"$title\" ready to run!")
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Workflow builder timed out", e)
                _bannerState.value = GenerationBannerState.Error("Workflow creation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Workflow builder failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Failed to create workflow.")
            }
        }
    }

    fun executeWorkflowLive(workflow: AutomationWorkflow, runtimeContextInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _bannerState.value = GenerationBannerState.Working("⚡ Streaming \"${workflow.title}\" execution…")
            try {
                val stepsFormatted = workflow.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
                val prompt = """
                    Execute this automated workflow immediately and produce the concrete deliverable for each step:
                    Workflow: ${workflow.title}
                    Objective: ${workflow.goal}
                    Runtime Input: ${runtimeContextInput.ifBlank { "Use smart default context for ${workflow.goal}" }}
                    Steps:
                    $stepsFormatted
                """.trimIndent()

                val acc = StringBuilder()
                withTimeout(AI_TIMEOUT_MS) {
                    activeChatModel.generateContentStream(prompt).collect { chunk ->
                        val part = chunk.text.orEmpty()
                        if (part.isNotEmpty()) {
                            acc.append(part)
                        }
                    }
                }
                val resultText = acc.toString().trim().ifBlank { "Workflow completed with empty output." }
                repository.recordWorkflowExecution(
                    workflowId = workflow.id,
                    output = resultText,
                    nextRunCount = workflow.runCount + 1L
                )
                _bannerState.value = GenerationBannerState.Notice("Workflow \"${workflow.title}\" executed!")
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Workflow execution timed out", e)
                _bannerState.value = GenerationBannerState.Error("Workflow execution timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Workflow execution failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Workflow run failed.")
            }
        }
    }

    fun deleteWorkflow(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteWorkflow(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Could not delete workflow.")
            }
        }
    }

    // --- 4. YouTube Connection, Clipping & Creator FYP Growth Engine ---

    fun onYouTubeTokenGranted(accessToken: String) {
        _youtubeAccessToken.value = accessToken
        viewModelScope.launch {
            try {
                val channel = YouTubeStudioManager.fetchMyChannelInfo(accessToken)
                _youtubeChannel.value = channel
                repository.updateConnectedYouTubeChannel("${channel.title} (${channel.handle})")
                _bannerState.value = GenerationBannerState.Notice("Connected YouTube: ${channel.title}!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Connected OAuth token, channel metadata note: ${e.message}")
                _youtubeChannel.value = ConnectedYouTubeChannel(
                    id = "oauth_linked",
                    title = "YouTube Account Linked",
                    handle = "Ready to Upload",
                    subscriberCount = "-"
                )
                repository.updateConnectedYouTubeChannel("YouTube Account Linked")
                _bannerState.value = GenerationBannerState.Notice("YouTube OAuth authorized and ready!")
            }
        }
    }

    // --- 4A. AI Prompt-to-Video Synthesis (H.264 + Spoken TTS Voiceover + AAC + Optional Gemini Keyframe Art) ---

    fun generateAiVideoFromPrompt(
        context: Context,
        prompt: String,
        durationSec: Int = 12,
        visualStyle: String = "Cyber Neon",
        useAiKeyframeArt: Boolean = false,
        enableVoiceover: Boolean = true,
        autoPostToYouTube: Boolean = false,
        privacyStatus: String = "public"
    ) {
        val cleanPrompt = prompt.trim().ifBlank { "3 AI Secrets Transforming Creators in 2026" }
        val safeDuration = durationSec.coerceIn(8, 20)

        viewModelScope.launch(Dispatchers.IO) {
            _bannerState.value = GenerationBannerState.Working("⚡ Scripting AI video storyboard & spoken narration…")
            try {
                val plannerInstruction = """
                    Generate a high-retention vertical AI video script & storyboard ($safeDuration seconds, 4 scenes) from user prompt:
                    "$cleanPrompt"
                    Visual Aesthetic: $visualStyle
                    For each scene, provide a punchy badge, bold visual headline, caption subtitle, and natural spoken voiceover sentence (`narration`) that flows seamlessly across all 4 scenes.
                    Also provide a high-CTR YouTube Shorts title, rich description with FYP hashtags (#shorts #fyp #viral) + nod to Nexus by @arnold60011, and 8 discovery tags.
                """.trimIndent()

                val planResp = withTimeout(AI_TIMEOUT_MS) {
                    clipPlannerModel.generateContent(plannerInstruction)
                }
                val json = JSONObject(planResp.text ?: throw IllegalStateException("AI video planner returned empty JSON."))
                val ytTitle = json.getString("youtubeTitle")
                val ytDesc = json.getString("youtubeDescription")
                val tagsArr = json.getJSONArray("tags")
                val tags = (0 until tagsArr.length()).map { tagsArr.getString(it).removePrefix("#") }

                val scenesArr = json.getJSONArray("scenes")
                val scenes = (0 until scenesArr.length()).map { idx ->
                    val s = scenesArr.getJSONObject(idx)
                    ClipSceneSlide(
                        badge = s.optString("badge", "SCENE ${idx + 1}"),
                        headline = s.optString("headline", ytTitle),
                        subtitle = s.optString("subtitle", ""),
                        accentHex = s.optString("accentHex", "#00E5FF"),
                        narration = s.optString("narration", s.optString("subtitle", ytTitle))
                    )
                }

                // Optional AI Keyframe Art via gemini-nano-banana-2.1 (strictly no mock fallback on error)
                val keyframeBitmap: Bitmap? = if (useAiKeyframeArt) {
                    _bannerState.value = GenerationBannerState.Working("🎨 Generating AI visual keyframe via Gemini Nano Banana 2.1 (30-60s)…")
                    val imgResp = withTimeout(AI_TIMEOUT_MS) {
                        aiKeyframeModel.generateContent(
                            "Cinematic, ultra-detailed vertical background visual for a video about: $cleanPrompt. Style: $visualStyle, dramatic lighting, rich depth, no text overlays."
                        )
                    }
                    val bmp = imgResp.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()?.image
                    bmp ?: throw IllegalStateException("Gemini image model returned no keyframe image.")
                } else {
                    null
                }

                _bannerState.value = GenerationBannerState.Working("🎬 Encoding HD 720x1280 MP4 video + voiceover audio…")
                val hashPreview = tags.take(3).joinToString(" ") { "#$it" } + " #fyp"
                val outputFile = VideoClipEngine.renderPromptShortVideo(
                    context = context.applicationContext,
                    scenes = scenes,
                    durationSec = safeDuration,
                    hashtagsLine = hashPreview,
                    visualStyle = visualStyle,
                    backgroundKeyframe = keyframeBitmap,
                    enableVoiceover = enableVoiceover
                )

                val clipId = repository.saveClipProject(
                    title = ytTitle,
                    description = ytDesc,
                    prompt = cleanPrompt.take(1900),
                    sourceType = "generated",
                    durationSec = safeDuration,
                    localFilePath = outputFile.absolutePath,
                    tags = tags
                )

                maybeAutoUploadClip(
                    clipId = clipId,
                    outputFile = outputFile,
                    title = ytTitle,
                    description = ytDesc,
                    tags = tags,
                    autoPostToYouTube = autoPostToYouTube,
                    privacyStatus = privacyStatus
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI video generation timed out", e)
                _bannerState.value = GenerationBannerState.Error("AI video generation timed out after 60s. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI video generation failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "AI video generation failed.")
            }
        }
    }

    // --- 4B. Uploaded Video Clipper (Single Highlight, Custom Trim Window, or 3-Clip Batch Slicer) ---

    fun clipUploadedVideo(
        context: Context,
        sourceVideoUri: Uri,
        prompt: String,
        sliceThreeHighlights: Boolean = false,
        customStartSec: Int? = null,
        customEndSec: Int? = null,
        autoPostToYouTube: Boolean = false,
        privacyStatus: String = "public"
    ) {
        val cleanPrompt = prompt.trim().ifBlank { "Extract the highest-retention viral highlight clip with FYP hashtags" }
        val crop916 = _verticalCrop916.value
        val renderUrl = _renderBaseUrl.value
        val useCloudUpload = _useRenderCloudForUploads.value && renderUrl.isNotBlank()

        viewModelScope.launch(Dispatchers.IO) {
            _bannerState.value = GenerationBannerState.Working(
                if (sliceThreeHighlights) "⚡ Inspecting uploaded video frames & slicing 3 viral MP4 clips…"
                else "⚡ Analyzing uploaded video for Media3 / FFmpeg pipeline…"
            )
            try {
                val appCtx = context.applicationContext
                val sourceMeta = VideoClipEngine.inspectVideo(appCtx, sourceVideoUri)
                val durationLimit = sourceMeta.durationSec.coerceAtLeast(3)

                suspend fun runSingleClipPipeline(startS: Int, endS: Int, labelPrefix: String): File {
                    val durS = (endS - startS).coerceAtLeast(2)
                    return if (useCloudUpload) {
                        _bannerState.value = GenerationBannerState.Working("$labelPrefix via Render Cloud FFmpeg (${startS}s–${endS}s)…")
                        RenderFfmpegClient.clipUploadedVideoViaRender(
                            context = appCtx,
                            baseUrl = renderUrl,
                            sourceUri = sourceVideoUri,
                            startSec = startS,
                            durationSec = durS,
                            cropVertical916 = crop916
                        )
                    } else {
                        Media3VideoPipeline.processVideoClip(
                            context = appCtx,
                            sourceUri = sourceVideoUri,
                            startSec = startS,
                            endSec = endS,
                            cropVertical916 = crop916,
                            watermarkLabel = "@arnold60011 • NEXUS",
                            onProgressPercent = { pct ->
                                _bannerState.value = GenerationBannerState.Working(
                                    "$labelPrefix via Media3 Pipeline (${startS}s–${endS}s) • $pct%"
                                )
                            }
                        )
                    }
                }

                if (sliceThreeHighlights) {
                    val instruction = """
                        Analyze this uploaded video ($durationLimit seconds total) and identify 3 distinct viral highlight windows to slice into separate YouTube Shorts clips.
                        User Clipping Goal: "$cleanPrompt"
                        Keep every startSec >= 0 and endSec <= $durationLimit, with each clip 5 to 30 seconds long.
                        Provide punchy Shorts titles, descriptions with FYP hashtags + nod to Nexus by @arnold60011, and 6 tags per clip.
                    """.trimIndent()

                    val resp = withTimeout(AI_TIMEOUT_MS) {
                        multiClipTrimmerModel.generateContent(
                            content {
                                sourceMeta.sampleFrames.forEach { frame -> image(frame) }
                                text(instruction)
                            }
                        )
                    }
                    val root = JSONObject(resp.text ?: throw IllegalStateException("Multi-clip analyzer returned empty JSON."))
                    val arr = root.getJSONArray("highlights")
                    val countToSlice = minOf(arr.length(), 3).coerceAtLeast(1)
                    var createdCount = 0

                    for (i in 0 until countToSlice) {
                        val item = arr.getJSONObject(i)
                        val fallbackSegment = (durationLimit / countToSlice).coerceAtLeast(3)
                        val rawStart = item.optInt("startSec", i * fallbackSegment)
                            .coerceIn(0, (durationLimit - 2).coerceAtLeast(0))
                        val rawEnd = item.optInt("endSec", (rawStart + 12).coerceAtMost(durationLimit))
                            .coerceIn(rawStart + 2, maxOf(rawStart + 2, durationLimit))

                        val processedFile = runSingleClipPipeline(
                            startS = rawStart,
                            endS = rawEnd,
                            labelPrefix = "✂️ Cutting Clip ${i + 1}/$countToSlice"
                        )
                        val tagsArr = item.optJSONArray("tags")
                        val tags = if (tagsArr != null) {
                            (0 until tagsArr.length()).map { tagsArr.getString(it).removePrefix("#") }
                        } else {
                            listOf("shorts", "fyp", "viral")
                        }
                        repository.saveClipProject(
                            title = item.optString("title", "Viral Clip #${i + 1}").take(95),
                            description = item.optString("description", cleanPrompt).take(4500),
                            prompt = "Batch Clip ${i + 1} (${rawStart}s-${rawEnd}s): $cleanPrompt".take(1900),
                            sourceType = "trimmed",
                            durationSec = (rawEnd - rawStart).coerceAtLeast(1),
                            localFilePath = processedFile.absolutePath,
                            tags = tags.take(10)
                        )
                        createdCount++
                    }
                    val engineName = if (useCloudUpload) "Render Cloud FFmpeg" else "Media3 Pipeline"
                    _bannerState.value = GenerationBannerState.Notice("Sliced $createdCount MP4 Shorts ($engineName)!")
                    return@launch
                }

                // Single Clip (AI Highlight Window or Custom User Start/End Trim)
                val plannerInstruction = """
                    Analyze this uploaded video ($durationLimit seconds total) and pick the single best viral highlight window (startSec to endSec within 0..$durationLimit, 6-25s long).
                    User Prompt: "$cleanPrompt"
                    Generate a high-CTR YouTube Shorts title, rich description with FYP hashtags (#shorts #fyp #viral) and a subtle shoutout to Nexus Studio by @arnold60011, and 8 YouTube discovery tags.
                """.trimIndent()

                val response = withTimeout(AI_TIMEOUT_MS) {
                    clipPlannerModel.generateContent(
                        content {
                            sourceMeta.sampleFrames.forEach { frame -> image(frame) }
                            text(plannerInstruction)
                        }
                    )
                }

                val json = JSONObject(response.text ?: throw IllegalStateException("Clip planner returned empty JSON."))
                val ytTitle = json.getString("youtubeTitle")
                val ytDesc = json.getString("youtubeDescription")
                val tagsArr = json.getJSONArray("tags")
                val tags = (0 until tagsArr.length()).map { tagsArr.getString(it).removePrefix("#") }

                val startSec = (customStartSec ?: json.optInt("startSec", 0))
                    .coerceIn(0, (durationLimit - 2).coerceAtLeast(0))
                val endSec = (customEndSec ?: json.optInt("endSec", (startSec + 12).coerceAtMost(durationLimit)))
                    .coerceIn(startSec + 2, maxOf(startSec + 2, durationLimit))

                val outputFile = runSingleClipPipeline(
                    startS = startSec,
                    endS = endSec,
                    labelPrefix = "✂️ Processing clip"
                )
                val finalDurationSec = (endSec - startSec).coerceAtLeast(1)

                val clipId = repository.saveClipProject(
                    title = ytTitle,
                    description = ytDesc,
                    prompt = cleanPrompt.take(1900),
                    sourceType = "trimmed",
                    durationSec = finalDurationSec,
                    localFilePath = outputFile.absolutePath,
                    tags = tags
                )

                maybeAutoUploadClip(
                    clipId = clipId,
                    outputFile = outputFile,
                    title = ytTitle,
                    description = ytDesc,
                    tags = tags,
                    autoPostToYouTube = autoPostToYouTube,
                    privacyStatus = privacyStatus
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Uploaded video clip timed out", e)
                _bannerState.value = GenerationBannerState.Error("Video analysis timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Uploaded video clipping failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Failed to clip uploaded video.")
            }
        }
    }

    // Legacy unified entry point preserved for convenience
    fun createClipAndOptionallyPublish(
        context: Context,
        prompt: String,
        sourceVideoUri: Uri?,
        autoPostToYouTube: Boolean,
        privacyStatus: String
    ) {
        if (sourceVideoUri != null) {
            clipUploadedVideo(
                context = context,
                sourceVideoUri = sourceVideoUri,
                prompt = prompt,
                sliceThreeHighlights = false,
                autoPostToYouTube = autoPostToYouTube,
                privacyStatus = privacyStatus
            )
        } else {
            generateAiVideoFromPrompt(
                context = context,
                prompt = prompt,
                durationSec = 12,
                autoPostToYouTube = autoPostToYouTube,
                privacyStatus = privacyStatus
            )
        }
    }

    // --- 4C. Universal External URL Video Clipper (Media3 Stream Pipeline + Render Cloud FFmpeg + YouTube 8-Clip Engine) ---

    fun clipVideoFromLink(
        context: Context,
        videoUrl: String,
        clipPrompt: String = "",
        autoPostToYouTube: Boolean = false,
        privacyStatus: String = "public"
    ) {
        val cleanUrl = videoUrl.trim()
        if (cleanUrl.isEmpty()) return

        if (YouTubeStudioManager.isLikelyYouTubeUrl(cleanUrl)) {
            analyzeYouTubeLinkIntoClips(
                videoUrl = cleanUrl,
                clipPrompt = clipPrompt,
                contextForAutoRender = context.applicationContext,
                autoPostFirstClip = autoPostToYouTube,
                privacyStatus = privacyStatus
            )
            return
        }

        val normalizedUrl = if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            cleanUrl
        } else {
            "https://$cleanUrl"
        }
        val crop916 = _verticalCrop916.value
        val renderUrl = _renderBaseUrl.value

        viewModelScope.launch(Dispatchers.IO) {
            var downloadedFile: File? = null
            try {
                val appCtx = context.applicationContext
                val remoteUri = Uri.parse(normalizedUrl)
                val lowerPath = normalizedUrl.lowercase().substringBefore("?")
                val isHlsStream = lowerPath.endsWith(".m3u8")

                _bannerState.value = GenerationBannerState.Working("🌐 Inspecting external video URL stream…")
                val sourceMeta = if (renderUrl.isNotBlank() || isHlsStream) {
                    Media3VideoPipeline.inspectAnyVideoSource(appCtx, remoteUri)
                } else {
                    downloadedFile = YouTubeStudioManager.downloadDirectVideoToCache(appCtx, normalizedUrl)
                    VideoClipEngine.inspectVideo(appCtx, Uri.fromFile(downloadedFile))
                }
                val durationLimit = sourceMeta.durationSec.coerceAtLeast(6)

                _bannerState.value = GenerationBannerState.Working("⚡ Planning viral highlight window (${durationLimit}s source)…")
                val instruction = """
                    Analyze this external video ($durationLimit seconds long) from URL: $normalizedUrl
                    User Clipping Goal: "${clipPrompt.ifBlank { "Extract the most viral high-retention highlight clip" }}"
                    Pick the optimal startSec and endSec within 0..$durationLimit (6-25s long), plus a viral Shorts title, description with FYP hashtags + nod to Nexus by @arnold60011, and 8 tags.
                """.trimIndent()

                val resp = withTimeout(AI_TIMEOUT_MS) {
                    if (sourceMeta.sampleFrames.isNotEmpty()) {
                        clipPlannerModel.generateContent(
                            content {
                                sourceMeta.sampleFrames.forEach { frame -> image(frame) }
                                text(instruction)
                            }
                        )
                    } else {
                        clipPlannerModel.generateContent(instruction)
                    }
                }
                val json = JSONObject(resp.text ?: throw IllegalStateException("Link clip planner returned empty JSON."))
                val ytTitle = json.getString("youtubeTitle")
                val ytDesc = json.getString("youtubeDescription")
                val tagsArr = json.getJSONArray("tags")
                val tags = (0 until tagsArr.length()).map { tagsArr.getString(it).removePrefix("#") }

                val rawStart = json.optInt("startSec", 0).coerceIn(0, (durationLimit - 2).coerceAtLeast(0))
                val rawEnd = json.optInt("endSec", (rawStart + 14).coerceAtMost(durationLimit))
                    .coerceIn(rawStart + 2, maxOf(rawStart + 2, durationLimit))
                val finalDuration = (rawEnd - rawStart).coerceAtLeast(2)

                val outputFile = if (renderUrl.isNotBlank()) {
                    _bannerState.value = GenerationBannerState.Working("🎬 Cutting external URL via Render Cloud FFmpeg (${rawStart}s–${rawEnd}s)…")
                    RenderFfmpegClient.clipExternalUrlViaRender(
                        context = appCtx,
                        baseUrl = renderUrl,
                        targetVideoUrl = normalizedUrl,
                        startSec = rawStart,
                        durationSec = finalDuration,
                        cropVertical916 = crop916
                    )
                } else {
                    val pipelineUri = downloadedFile?.let { Uri.fromFile(it) } ?: remoteUri
                    Media3VideoPipeline.processVideoClip(
                        context = appCtx,
                        sourceUri = pipelineUri,
                        startSec = rawStart,
                        endSec = rawEnd,
                        cropVertical916 = crop916,
                        watermarkLabel = "@arnold60011 • NEXUS",
                        onProgressPercent = { pct ->
                            _bannerState.value = GenerationBannerState.Working(
                                "✂️ Media3 Pipeline clipping URL (${rawStart}s–${rawEnd}s) • $pct%"
                            )
                        }
                    )
                }

                val clipId = repository.saveClipProject(
                    title = ytTitle,
                    description = ytDesc,
                    prompt = "Link Clip ($normalizedUrl): $clipPrompt".take(1900),
                    sourceType = "trimmed",
                    durationSec = finalDuration,
                    localFilePath = outputFile.absolutePath,
                    tags = tags
                )

                maybeAutoUploadClip(
                    clipId = clipId,
                    outputFile = outputFile,
                    title = ytTitle,
                    description = ytDesc,
                    tags = tags,
                    autoPostToYouTube = autoPostToYouTube,
                    privacyStatus = privacyStatus
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Direct link clipping timed out", e)
                _bannerState.value = GenerationBannerState.Error("Link video processing timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Direct link clipping failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Could not clip video from link.")
            } finally {
                try {
                    downloadedFile?.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun maybeAutoUploadClip(
        clipId: String,
        outputFile: File,
        title: String,
        description: String,
        tags: List<String>,
        autoPostToYouTube: Boolean,
        privacyStatus: String
    ) {
        val token = _youtubeAccessToken.value
        if (autoPostToYouTube && !token.isNullOrBlank()) {
            _bannerState.value = GenerationBannerState.Working("Uploading \"$title\" directly to YouTube…")
            repository.updateClipYouTubeStatus(clipId, "uploading")
            val videoId = YouTubeStudioManager.uploadClipToYouTube(
                accessToken = token,
                videoFile = outputFile,
                title = title,
                description = description,
                tags = tags,
                privacyStatus = privacyStatus
            )
            repository.updateClipYouTubeStatus(clipId, "uploaded", videoId)
            _bannerState.value = GenerationBannerState.Notice("Video created & uploaded to YouTube (ID: $videoId)!")
        } else {
            _bannerState.value = GenerationBannerState.Notice(
                if (autoPostToYouTube) "MP4 ready! Connect your YouTube account above or tap YouTube App to publish."
                else "MP4 \"$title\" ready! Tap Preview to watch with audio or Save MP4."
            )
        }
    }

    fun exportClipToDeviceGallery(context: Context, clip: VideoClipProject) {
        viewModelScope.launch {
            val ok = VideoClipEngine.exportClipToGallery(
                context = context.applicationContext,
                videoFile = File(clip.localFilePath),
                title = clip.title
            )
            if (ok) {
                _bannerState.value = GenerationBannerState.Notice("Saved \"${clip.title}\" to Movies/NexusClips in your Gallery!")
            } else {
                _bannerState.value = GenerationBannerState.Error("Could not export MP4 to Gallery. Verify local clip still exists.")
            }
        }
    }

    fun uploadExistingClipToYouTube(clip: VideoClipProject, privacyStatus: String = "public") {
        val token = _youtubeAccessToken.value
        if (token.isNullOrBlank()) {
            _bannerState.value = GenerationBannerState.Error("Connect your YouTube channel first, or use 'Open in YouTube App'.")
            return
        }
        viewModelScope.launch {
            _bannerState.value = GenerationBannerState.Working("Uploading \"${clip.title}\" to YouTube…")
            try {
                repository.updateClipYouTubeStatus(clip.id, "uploading")
                val videoId = YouTubeStudioManager.uploadClipToYouTube(
                    accessToken = token,
                    videoFile = File(clip.localFilePath),
                    title = clip.title,
                    description = clip.description,
                    tags = clip.tags,
                    privacyStatus = privacyStatus
                )
                repository.updateClipYouTubeStatus(clip.id, "uploaded", videoId)
                _bannerState.value = GenerationBannerState.Notice("Published to YouTube! Video ID: $videoId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "YouTube upload failed", e)
                repository.updateClipYouTubeStatus(clip.id, "ready")
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "YouTube API upload failed.")
            }
        }
    }

    fun markClipSharedViaApp(clipId: String) {
        viewModelScope.launch {
            try {
                repository.updateClipYouTubeStatus(clipId, "shared")
            } catch (_: Exception) {
            }
        }
    }

    fun deleteClip(clip: VideoClipProject) {
        viewModelScope.launch {
            try {
                File(clip.localFilePath).delete()
                repository.deleteClip(clip.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Could not delete clip.")
            }
        }
    }

    fun analyzeYouTubeLinkIntoClips(
        videoUrl: String,
        clipPrompt: String = "",
        contextForAutoRender: Context? = null,
        autoPostFirstClip: Boolean = false,
        privacyStatus: String = "public"
    ) {
        val cleanUrl = videoUrl.trim()
        if (cleanUrl.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _bannerState.value = GenerationBannerState.Working("⚡ Inspecting YouTube video & extracting 8 viral Shorts clips…")
            try {
                val (videoTitle, authorName) = withTimeoutOrNull(3_500L) {
                    YouTubeStudioManager.fetchYouTubeVideoOEmbed(cleanUrl)
                } ?: ("YouTube Source Video" to "YouTube Creator")
                val ytThumb = withTimeoutOrNull(3_500L) {
                    YouTubeStudioManager.fetchYouTubeThumbnailBitmap(cleanUrl)
                }

                val prompt = """
                    Analyze this YouTube video and extract 8 distinct high-retention viral YouTube Shorts / Reels clip blueprints:
                    Video URL: $cleanUrl
                    Detected Title: "$videoTitle"
                    Channel / Creator: "$authorName"
                    ${if (clipPrompt.isNotBlank()) "User Clipping Focus: \"$clipPrompt\"" else ""}

                    For each of the 8 clips, provide:
                    - Realistic timestamp window (e.g. 00:45 - 01:15), startSec, and durationSec (15-40s)
                    - Scroll-stopping 0-3s hook
                    - High-CTR Shorts title (< 90 chars)
                    - Engaging description including FYP hashtags and a creator nod to Nexus by @arnold60011
                    - 6 viral discovery hashtags
                    - 3 kinetic vertical storyboard slides (with spoken `narration`) summarizing the moment so Nexus can synthesize an instant narrated MP4 Short!
                """.trimIndent()

                val response = withTimeout(AI_TIMEOUT_MS) {
                    if (ytThumb != null) {
                        youtubeLinkClipsModel.generateContent(
                            content {
                                image(ytThumb)
                                text(prompt)
                            }
                        )
                    } else {
                        youtubeLinkClipsModel.generateContent(prompt)
                    }
                }
                val root = JSONObject(response.text ?: throw IllegalStateException("Empty URL clip breakdown."))
                val arr = root.getJSONArray("clips")
                val parsed = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val startS = obj.optInt("startSec", i * 45)
                    val durS = obj.optInt("durationSec", 24).coerceIn(10, 55)
                    val tsRange = obj.optString("timestampRange", "${startS}s - ${startS + durS}s")
                    val tagsJson = obj.optJSONArray("hashtags")
                    val tags = if (tagsJson != null) {
                        (0 until tagsJson.length()).map { tagsJson.getString(it).removePrefix("#") }
                    } else {
                        listOf("shorts", "fyp", "viral")
                    }
                    val scenesJson = obj.optJSONArray("scenes")
                    val slides = if (scenesJson != null && scenesJson.length() > 0) {
                        (0 until scenesJson.length()).map { sIdx ->
                            val sObj = scenesJson.getJSONObject(sIdx)
                            ClipSceneSlide(
                                badge = sObj.optString("badge", "CLIP ${i + 1}"),
                                headline = sObj.optString("headline", obj.optString("title", videoTitle)),
                                subtitle = sObj.optString("subtitle", ""),
                                accentHex = sObj.optString("accentHex", "#FF1744"),
                                narration = sObj.optString("narration", sObj.optString("subtitle", ""))
                            )
                        }
                    } else {
                        listOf(
                            ClipSceneSlide("HOOK", obj.optString("hook", videoTitle), "Source: $authorName", "#FF1744", obj.optString("hook", videoTitle))
                        )
                    }
                    val ffmpegCmd = "yt-dlp -f mp4 -o source.mp4 \"$cleanUrl\" && ffmpeg -ss $startS -i source.mp4 -t $durS -vf \"crop=ih*9/16:ih,scale=720:1280\" -c:v libx264 -c:a aac clip_${i + 1}.mp4"
                    YouTubeLinkClipCandidate(
                        index = i + 1,
                        sourceVideoTitle = videoTitle,
                        sourceUrl = cleanUrl,
                        timestampRange = tsRange,
                        startSec = startS,
                        durationSec = durS,
                        hook = obj.optString("hook", ""),
                        title = obj.optString("title", "Viral Short #${i + 1}"),
                        description = obj.optString("description", ""),
                        hashtags = tags,
                        ffmpegCmd = ffmpegCmd,
                        scenes = slides,
                        sourceThumbnail = ytThumb
                    )
                }
                _youtubeLinkCandidates.value = parsed

                val topCandidate = parsed.firstOrNull()
                if (contextForAutoRender != null && topCandidate != null) {
                    val renderUrl = _renderBaseUrl.value
                    if (renderUrl.isNotBlank()) {
                        _bannerState.value = GenerationBannerState.Working(
                            "🎬 Extracted ${parsed.size} clips! Cutting Top Clip #1 via Render FFmpeg…"
                        )
                        try {
                            cutCandidateViaRenderInternal(
                                context = contextForAutoRender,
                                renderBaseUrl = renderUrl,
                                candidate = topCandidate,
                                autoPostToYouTube = autoPostFirstClip,
                                privacyStatus = privacyStatus
                            )
                        } catch (cloudErr: Exception) {
                            Log.w(TAG, "Render cloud YouTube cut fallback to on-device Short render: ${cloudErr.message}")
                            renderLinkCandidateInternal(
                                context = contextForAutoRender,
                                candidate = topCandidate,
                                autoPostToYouTube = autoPostFirstClip,
                                privacyStatus = privacyStatus
                            )
                        }
                    } else {
                        _bannerState.value = GenerationBannerState.Working("🎬 Extracted ${parsed.size} clips! Auto-rendering Top Clip #1 MP4…")
                        renderLinkCandidateInternal(
                            context = contextForAutoRender,
                            candidate = topCandidate,
                            autoPostToYouTube = autoPostFirstClip,
                            privacyStatus = privacyStatus
                        )
                    }
                } else {
                    _bannerState.value = GenerationBannerState.Notice("Extracted ${parsed.size} viral clips from \"$videoTitle\"!")
                }
            } catch (e: TimeoutCancellationException) {
                _bannerState.value = GenerationBannerState.Error("YouTube URL clip breakdown timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "YouTube link breakdown failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Could not analyze YouTube URL.")
            }
        }
    }

    fun cutCandidateViaRenderFfmpeg(
        context: Context,
        candidate: YouTubeLinkClipCandidate,
        autoPostToYouTube: Boolean,
        privacyStatus: String = "public"
    ) {
        val renderUrl = _renderBaseUrl.value
        if (renderUrl.isBlank()) {
            _bannerState.value = GenerationBannerState.Error(
                "Enter your deployed Render URL in the FFmpeg & Media3 Pipeline card first."
            )
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _bannerState.value = GenerationBannerState.Working(
                    "✂️ Cutting real video Clip #${candidate.index} (${candidate.timestampRange}) via Render FFmpeg…"
                )
                cutCandidateViaRenderInternal(
                    context = context.applicationContext,
                    renderBaseUrl = renderUrl,
                    candidate = candidate,
                    autoPostToYouTube = autoPostToYouTube,
                    privacyStatus = privacyStatus
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Render FFmpeg cut failed", e)
                _bannerState.value = GenerationBannerState.Error(
                    e.localizedMessage ?: "Render FFmpeg clip failed."
                )
            }
        }
    }

    private suspend fun cutCandidateViaRenderInternal(
        context: Context,
        renderBaseUrl: String,
        candidate: YouTubeLinkClipCandidate,
        autoPostToYouTube: Boolean,
        privacyStatus: String
    ) {
        val mp4File = RenderFfmpegClient.clipExternalUrlViaRender(
            context = context,
            baseUrl = renderBaseUrl,
            targetVideoUrl = candidate.sourceUrl,
            startSec = candidate.startSec,
            durationSec = candidate.durationSec,
            cropVertical916 = _verticalCrop916.value
        )
        val fullDesc = buildString {
            appendLine(candidate.description)
            appendLine("Clipped via Nexus FFmpeg (${candidate.timestampRange}) • Source: ${candidate.sourceUrl}")
            appendLine("Powered by Nexus Studio (@arnold60011)")
        }
        val clipId = repository.saveClipProject(
            title = candidate.title,
            description = fullDesc.take(3900),
            prompt = "Render FFmpeg Clip (${candidate.timestampRange}): ${candidate.sourceUrl}".take(1900),
            sourceType = "trimmed",
            durationSec = candidate.durationSec,
            localFilePath = mp4File.absolutePath,
            tags = candidate.hashtags
        )
        maybeAutoUploadClip(
            clipId = clipId,
            outputFile = mp4File,
            title = candidate.title,
            description = fullDesc,
            tags = candidate.hashtags,
            autoPostToYouTube = autoPostToYouTube,
            privacyStatus = privacyStatus
        )
    }

    fun renderLinkCandidateToMp4AndPublish(
        context: Context,
        candidate: YouTubeLinkClipCandidate,
        autoPostToYouTube: Boolean,
        privacyStatus: String = "public"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _bannerState.value = GenerationBannerState.Working("🎬 Rendering HD vertical MP4 + voiceover for Clip #${candidate.index}…")
                renderLinkCandidateInternal(
                    context = context.applicationContext,
                    candidate = candidate,
                    autoPostToYouTube = autoPostToYouTube,
                    privacyStatus = privacyStatus
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed rendering URL clip candidate", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Failed to render MP4 Short.")
            }
        }
    }

    private suspend fun renderLinkCandidateInternal(
        context: Context,
        candidate: YouTubeLinkClipCandidate,
        autoPostToYouTube: Boolean,
        privacyStatus: String
    ) {
        val hashLine = candidate.hashtags.take(3).joinToString(" ") { "#$it" } + " #fyp"
        val mp4File = VideoClipEngine.renderPromptShortVideo(
            context = context,
            scenes = candidate.scenes,
            durationSec = 12,
            hashtagsLine = hashLine,
            visualStyle = "Crimson Viral",
            backgroundKeyframe = candidate.sourceThumbnail,
            enableVoiceover = true
        )
        val fullDesc = buildString {
            appendLine(candidate.description)
            appendLine("Clipped Window: ${candidate.timestampRange} • Source: ${candidate.sourceUrl}")
            appendLine("Powered by Nexus Studio (@arnold60011)")
        }
        val clipId = repository.saveClipProject(
            title = candidate.title,
            description = fullDesc.take(3900),
            prompt = "Link Clip (${candidate.timestampRange}): ${candidate.sourceUrl}".take(1900),
            sourceType = "generated",
            durationSec = 12,
            localFilePath = mp4File.absolutePath,
            tags = candidate.hashtags
        )
        maybeAutoUploadClip(
            clipId = clipId,
            outputFile = mp4File,
            title = candidate.title,
            description = fullDesc,
            tags = candidate.hashtags,
            autoPostToYouTube = autoPostToYouTube,
            privacyStatus = privacyStatus
        )
    }

    // --- 5. YouTube Creator Viral Hashtag & FYP Strategy Generator ---

    fun generateCreatorFypKit(nicheOrIdea: String) {
        val cleanTopic = nicheOrIdea.trim()
        if (cleanTopic.isEmpty()) return

        viewModelScope.launch {
            _bannerState.value = GenerationBannerState.Working("Engineering FYP hooks, SEO titles & hashtag matrix…")
            try {
                val prompt = """
                    Act as an elite YouTube Shorts & Long-form Growth Strategist.
                    Analyze this video idea / creator niche: "$cleanTopic"
                    Generate:
                    - Realistic FYP viral score (70-99)
                    - 3 scroll-stopping 0-3s hooks
                    - 3 high-CTR YouTube titles
                    - High-contrast thumbnail visual + 3-word overlay concept
                    - Retention & seamless loop pacing tactic
                    - 6 broad FYP/Shorts hashtags (with #), 6 targeted niche hashtags (with #), and 6 YouTube search SEO tags
                    - Best posting & first-hour engagement strategy
                """.trimIndent()

                val response = withTimeout(AI_TIMEOUT_MS) {
                    creatorGrowthModel.generateContent(prompt)
                }
                val rawJson = response.text ?: throw IllegalStateException("Empty FYP growth response.")
                val json = JSONObject(rawJson)

                fun parseList(key: String): List<String> {
                    val arr = json.optJSONArray(key) ?: return emptyList()
                    return (0 until arr.length()).map { arr.getString(it) }
                }

                _creatorGrowthResult.value = CreatorGrowthResult(
                    fypScore = json.optInt("fypScore", 88),
                    viralHooks = parseList("viralHooks"),
                    seoTitles = parseList("seoTitles"),
                    thumbnailIdea = json.optString("thumbnailIdea", ""),
                    retentionStrategy = json.optString("retentionStrategy", ""),
                    fypHashtags = parseList("fypHashtags"),
                    nicheHashtags = parseList("nicheHashtags"),
                    searchKeywords = parseList("searchKeywords"),
                    bestPostingTip = json.optString("bestPostingTip", "")
                )
                _bannerState.value = GenerationBannerState.Idle
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Creator FYP kit timed out", e)
                _bannerState.value = GenerationBannerState.Error("FYP analysis timed out. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Creator FYP kit failed", e)
                _bannerState.value = GenerationBannerState.Error(e.localizedMessage ?: "Failed to generate FYP kit.")
            }
        }
    }
}
