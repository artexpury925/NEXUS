package com.example.viewmodel

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

sealed interface ImageGenState {
    data object Idle : ImageGenState
    data object Loading : ImageGenState
    data class Success(val bitmap: Bitmap, val prompt: String) : ImageGenState
    data class Error(val message: String) : ImageGenState
}

data class TitleThumbLabResult(
    val topic: String,
    val titles: List<String>,
    val overlayText: String,
    val colorPsychology: String,
    val emotionCue: String,
    val visualPrompt: String
)

data class RetentionOptimizerResult(
    val predictedAvdBoost: String,
    val openingHook0to3s: String,
    val patternInterrupts: List<String>,
    val rewrittenScript: String,
    val seamlessLoopCta: String
)

data class FullUploadPackageResult(
    val title: String,
    val seoDescription: String,
    val studioTagsCsv: String,
    val safeHashtags: List<String>,
    val aggressiveHashtags: List<String>,
    val nuclearHashtags: List<String>,
    val infoCards: List<String>,
    val endScreenPlan: String,
    val pinnedComment: String,
    val communityPoll: String
)

data class ContentCalendarResult(
    val horizonLabel: String,
    val bestPostingWindows: String,
    val monetizationPlaybook: String,
    val scheduleItems: List<String>
)

data class CompetitorAuditResult(
    val targetName: String,
    val weaknessesFound: List<String>,
    val contentGaps: List<String>,
    val outlierVideoConcepts: List<String>
)

data class MassRepurposeResult(
    val sourceTopic: String,
    val youtubeShortsScripts: List<String>,
    val tiktokHooks: List<String>,
    val igReelsCaptions: List<String>,
    val xThreadPosts: List<String>,
    val linkedinPost: String,
    val threadsPost: String,
    val communityUpdate: String
)

data class ChannelHealthAndTrendResult(
    val healthScore: Int,
    val algorithmDiagnostics: List<String>,
    val recoveryProtocol: List<String>,
    val risingViralTrends: List<String>
)

data class BusinessEmpireResult(
    val mode: String,
    val headline: String,
    val coreDeliverables: List<String>,
    val supplierOrTechStack: List<String>,
    val adOrSalesScripts: List<String>,
    val scalingPlaybook: String
)

class NexusEmpireEngine {

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { idx ->
            optString(idx, "").trim().takeIf { it.isNotEmpty() }
        }
    }

    private val titleThumbModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "titles" to Schema.array(items = Schema.string(), description = "15 high-CTR YouTube titles"),
                "overlayText" to Schema.string(description = "Punchy 2 to 4 word thumbnail text overlay"),
                "colorPsychology" to Schema.string(description = "High-contrast color palette psychology explanation"),
                "emotionCue" to Schema.string(description = "Facial expression, focal subject, and composition cue"),
                "visualPrompt" to Schema.string(description = "Detailed visual prompt for generating a clean high-contrast YouTube thumbnail background without text")
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val thumbnailImageModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val retentionModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "predictedAvdBoost" to Schema.string(description = "Predicted retention target e.g. 88% - 108% AVD"),
                "openingHook0to3s" to Schema.string(description = "Scroll-stopping first 3 seconds spoken & visual hook"),
                "patternInterrupts" to Schema.array(items = Schema.string(), description = "4 pattern-interrupt cues placed every 7 seconds"),
                "rewrittenScript" to Schema.string(description = "Complete high-retention rewritten script with pacing markers"),
                "seamlessLoopCta" to Schema.string(description = "Final line that loops seamlessly back into the first word + @arnold60011 nod")
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val uploadPackageModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = " #1 highest-CTR YouTube title under 90 chars"),
                "seoDescription" to Schema.string(description = "Complete SEO description with timestamps, links, and creator shoutout to @arnold60011"),
                "studioTagsCsv" to Schema.string(description = "Comma-separated YouTube Studio tags under 480 chars"),
                "safeHashtags" to Schema.array(items = Schema.string(), description = "5 Safe Pack broad hashtags starting with #"),
                "aggressiveHashtags" to Schema.array(items = Schema.string(), description = "5 Aggressive Pack niche authority hashtags starting with #"),
                "nuclearHashtags" to Schema.array(items = Schema.string(), description = "5 Nuclear Pack high-velocity viral hashtags starting with #"),
                "infoCards" to Schema.array(items = Schema.string(), description = "3 timestamped YouTube Info Card placements"),
                "endScreenPlan" to Schema.string(description = "Exact End Screen element layout + verbal bridge"),
                "pinnedComment" to Schema.string(description = "High-engagement pinned comment prompt to spark replies"),
                "communityPoll" to Schema.string(description = "Companion YouTube Community Tab poll with 4 options")
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val calendarModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "horizonLabel" to Schema.string(description = "Roadmap horizon e.g. 30-Day Creator Domination Plan"),
                "bestPostingWindows" to Schema.string(description = "Optimal daily Shorts & Long-Form upload windows"),
                "monetizationPlaybook" to Schema.string(description = "AdSense, Super Thanks, Brand Deal, and funnel monetization milestones"),
                "scheduleItems" to Schema.array(items = Schema.string(), description = "10 structured phase/day content releases with exact titles and hooks")
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val competitorModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "weaknessesFound" to Schema.array(items = Schema.string(), description = "4 algorithmic or packaging weaknesses in competitor content"),
                "contentGaps" to Schema.array(items = Schema.string(), description = "4 high-demand underserved audience topics"),
                "outlierVideoConcepts" to Schema.array(items = Schema.string(), description = "5 outlier 10x CTR video titles + hook angles to outrank them")
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val customHooksModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "hooks" to Schema.array(items = Schema.string(), description = "12 scroll-stopping first-3-second viral hooks")
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val repurposeModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "youtubeShortsScripts" to Schema.array(items = Schema.string(), description = "4 high-retention YouTube Shorts hook + script outlines"),
                "tiktokHooks" to Schema.array(items = Schema.string(), description = "4 TikTok pattern-interrupt hooks + captions"),
                "igReelsCaptions" to Schema.array(items = Schema.string(), description = "3 Instagram Reels viral captions + CTA"),
                "xThreadPosts" to Schema.array(items = Schema.string(), description = "5-tweet high-authority X/Twitter thread"),
                "linkedinPost" to Schema.string(description = "Executive LinkedIn authority story post"),
                "threadsPost" to Schema.string(description = "Punchy conversational Threads post"),
                "communityUpdate" to Schema.string(description = "YouTube Community teaser + poll")
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val channelAuditModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "healthScore" to Schema.integer(description = "Algorithmic discovery readiness score from 72 to 99"),
                "algorithmDiagnostics" to Schema.array(items = Schema.string(), description = "4 shadowban / suppression / CTR & AVD diagnostic checks"),
                "recoveryProtocol" to Schema.array(items = Schema.string(), description = "4 concrete recovery & velocity reset actions"),
                "risingViralTrends" to Schema.array(items = Schema.string(), description = "5 early rising viral content trends to post right now")
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val businessEmpireModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "headline" to Schema.string(description = "Executive positioning headline for this store or SaaS venture"),
                "coreDeliverables" to Schema.array(items = Schema.string(), description = "4 winning products with target margins OR 4 core SaaS MVP modules & pricing tiers"),
                "supplierOrTechStack" to Schema.array(items = Schema.string(), description = "4 supplier sourcing strategies (AliExpress/CJ/Zendrop) OR full-stack architecture & payment stack"),
                "adOrSalesScripts" to Schema.array(items = Schema.string(), description = "3 TikTok/Shorts UGC ad scripts OR high-ticket outbound/closing scripts"),
                "scalingPlaybook" to Schema.string(description = "Step-by-step $0 to $10k/mo scaling & automation playbook")
            )
        )
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    suspend fun generateTitleAndThumbnailLab(topic: String): TitleThumbLabResult = withTimeout(TIMEOUT_MS) {
        val prompt = """
            Act as Nexus Title & Thumbnail Nuclear Lab (built by Arnold Chirchir, @arnold60011).
            Video Topic / Idea: "$topic"
            Generate 15 ultra-high-CTR YouTube titles (curiosity gap, authority, contrast, urgency), a 3-word thumbnail text overlay, color psychology strategy, facial expression/subject cue, and a detailed visual generation prompt (without text) for an eye-catching YouTube thumbnail.
        """.trimIndent()
        val raw = titleThumbModel.generateContent(prompt).text ?: throw IllegalStateException("Empty Title Lab output.")
        val json = JSONObject(raw)
        TitleThumbLabResult(
            topic = topic,
            titles = json.optJSONArray("titles").toStringList(),
            overlayText = json.optString("overlayText", "WATCH THIS NOW"),
            colorPsychology = json.optString("colorPsychology", ""),
            emotionCue = json.optString("emotionCue", ""),
            visualPrompt = json.optString("visualPrompt", "High-contrast cinematic YouTube thumbnail artwork for $topic, vibrant lighting, dramatic depth, no text")
        )
    }

    suspend fun generateThumbnailBitmap(visualPrompt: String): Bitmap = withTimeout(TIMEOUT_MS) {
        val resp = thumbnailImageModel.generateContent(
            "Ultra-crisp, high-CTR 16:9 YouTube thumbnail visual: $visualPrompt. High contrast, vibrant focal subject, dramatic rim lighting, studio quality, no text letters."
        )
        val bmp = resp.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()?.image
        bmp ?: throw IllegalStateException("Gemini Nano Banana 2.1 did not return an image.")
    }

    suspend fun optimizeScriptRetention(rawScriptOrIdea: String): RetentionOptimizerResult = withTimeout(TIMEOUT_MS) {
        val prompt = """
            Act as Nexus Retention Graph Optimizer.
            Rewrite and engineer this video script or topic for maximum Average View Duration (AVD) and 100%+ Shorts loop rate:
            "$rawScriptOrIdea"
            Include a razor-sharp 0-3s hook, 4 pattern interrupts every ~7 seconds, the complete high-retention script, and a seamless loop ending that bridges back to word #1.
        """.trimIndent()
        val raw = retentionModel.generateContent(prompt).text ?: throw IllegalStateException("Empty Retention Optimizer output.")
        val json = JSONObject(raw)
        RetentionOptimizerResult(
            predictedAvdBoost = json.optString("predictedAvdBoost", "92%+ Target AVD"),
            openingHook0to3s = json.optString("openingHook0to3s", ""),
            patternInterrupts = json.optJSONArray("patternInterrupts").toStringList(),
            rewrittenScript = json.optString("rewrittenScript", ""),
            seamlessLoopCta = json.optString("seamlessLoopCta", "")
        )
    }

    suspend fun generateFullUploadPackage(videoTopic: String): FullUploadPackageResult = withTimeout(TIMEOUT_MS) {
        val prompt = """
            Generate a complete, ready-to-paste YouTube Upload Domination Package for:
            "$videoTopic"
            Include #1 CTR title, SEO description (referencing Nexus Studio & @arnold60011), comma-separated Studio tags, Safe/Aggressive/Nuclear hashtag packs, Info Card timestamps, End Screen plan, Pinned Comment, and Community Poll.
        """.trimIndent()
        val raw = uploadPackageModel.generateContent(prompt).text ?: throw IllegalStateException("Empty Upload Package output.")
        val json = JSONObject(raw)
        FullUploadPackageResult(
            title = json.optString("title", videoTopic),
            seoDescription = json.optString("seoDescription", ""),
            studioTagsCsv = json.optString("studioTagsCsv", ""),
            safeHashtags = json.optJSONArray("safeHashtags").toStringList(),
            aggressiveHashtags = json.optJSONArray("aggressiveHashtags").toStringList(),
            nuclearHashtags = json.optJSONArray("nuclearHashtags").toStringList(),
            infoCards = json.optJSONArray("infoCards").toStringList(),
            endScreenPlan = json.optString("endScreenPlan", ""),
            pinnedComment = json.optString("pinnedComment", ""),
            communityPoll = json.optString("communityPoll", "")
        )
    }

    suspend fun generateContentCalendar(nicheOrChannel: String, days: Int): ContentCalendarResult = withTimeout(TIMEOUT_MS) {
        val safeDays = if (days in listOf(30, 60, 90)) days else 30
        val prompt = """
            Build an actionable $safeDays-Day YouTube Shorts & Long-Form Creator Domination Content Calendar for:
            "$nicheOrChannel"
            Provide exact daily upload windows, monetization & Super Thanks / Brand Deal milestones, and 10 high-impact phase deliverables with titles and hooks.
        """.trimIndent()
        val raw = calendarModel.generateContent(prompt).text ?: throw IllegalStateException("Empty Calendar output.")
        val json = JSONObject(raw)
        ContentCalendarResult(
            horizonLabel = json.optString("horizonLabel", "$safeDays-Day Creator Calendar"),
            bestPostingWindows = json.optString("bestPostingWindows", ""),
            monetizationPlaybook = json.optString("monetizationPlaybook", ""),
            scheduleItems = json.optJSONArray("scheduleItems").toStringList()
        )
    }

    suspend fun analyzeCompetitor(competitorOrNiche: String): CompetitorAuditResult = withTimeout(TIMEOUT_MS) {
        val prompt = """
            Act as Nexus Competitor Destruction & Gap Analyzer.
            Target Competitor Channel or Niche: "$competitorOrNiche"
            Identify 4 packaging/retention weaknesses in typical videos in this space, 4 high-demand content gaps, and 5 outlier video concepts with 10x CTR titles to overtake them.
        """.trimIndent()
        val raw = competitorModel.generateContent(prompt).text ?: throw IllegalStateException("Empty Competitor Audit output.")
        val json = JSONObject(raw)
        CompetitorAuditResult(
            targetName = competitorOrNiche,
            weaknessesFound = json.optJSONArray("weaknessesFound").toStringList(),
            contentGaps = json.optJSONArray("contentGaps").toStringList(),
            outlierVideoConcepts = json.optJSONArray("outlierVideoConcepts").toStringList()
        )
    }

    suspend fun generateViralHooks(topic: String): List<String> = withTimeout(TIMEOUT_MS) {
        val prompt = """
            Generate 12 scroll-stopping first-3-second viral hooks (pattern interrupt, contrarian, proof-first, curiosity gap) for: "$topic".
        """.trimIndent()
        val raw = customHooksModel.generateContent(prompt).text ?: throw IllegalStateException("Empty Hooks output.")
        JSONObject(raw).optJSONArray("hooks").toStringList()
    }

    suspend fun repurposeMassContent(sourceVideoOrTopic: String): MassRepurposeResult = withTimeout(TIMEOUT_MS) {
        val prompt = """
            Act as Nexus 1-to-50 Multi-Platform Content Empire Engine.
            Repurpose this core video/idea into high-converting native assets across YouTube Shorts, TikTok, Instagram Reels, X/Twitter Thread, LinkedIn, Threads, and YouTube Community:
            "$sourceVideoOrTopic"
        """.trimIndent()
        val raw = repurposeModel.generateContent(prompt).text ?: throw IllegalStateException("Empty Repurpose output.")
        val json = JSONObject(raw)
        MassRepurposeResult(
            sourceTopic = sourceVideoOrTopic,
            youtubeShortsScripts = json.optJSONArray("youtubeShortsScripts").toStringList(),
            tiktokHooks = json.optJSONArray("tiktokHooks").toStringList(),
            igReelsCaptions = json.optJSONArray("igReelsCaptions").toStringList(),
            xThreadPosts = json.optJSONArray("xThreadPosts").toStringList(),
            linkedinPost = json.optString("linkedinPost", ""),
            threadsPost = json.optString("threadsPost", ""),
            communityUpdate = json.optString("communityUpdate", "")
        )
    }

    suspend fun auditChannelAndTrends(nicheOrHandle: String): ChannelHealthAndTrendResult = withTimeout(TIMEOUT_MS) {
        val prompt = """
            Perform a YouTube/TikTok/Reels Algorithmic Health, Shadowban Recovery & Early Trend Radar audit for:
            "$nicheOrHandle"
            Provide a readiness score (72-99), 4 diagnostic checks, 4 velocity recovery steps, and 5 rising viral content trends right now.
        """.trimIndent()
        val raw = channelAuditModel.generateContent(prompt).text ?: throw IllegalStateException("Empty Channel Audit output.")
        val json = JSONObject(raw)
        ChannelHealthAndTrendResult(
            healthScore = json.optInt("healthScore", 91),
            algorithmDiagnostics = json.optJSONArray("algorithmDiagnostics").toStringList(),
            recoveryProtocol = json.optJSONArray("recoveryProtocol").toStringList(),
            risingViralTrends = json.optJSONArray("risingViralTrends").toStringList()
        )
    }

    suspend fun architectBusinessEmpire(mode: String, nicheOrIdea: String): BusinessEmpireResult = withTimeout(TIMEOUT_MS) {
        val isDropship = mode == "dropship"
        val prompt = if (isDropship) {
            """
                Act as Nexus Dropshipping & E-Commerce Empire Architect.
                Niche / Product Focus: "$nicheOrIdea"
                Deliver:
                - Brand positioning headline
                - 4 winning high-margin products with recommended retail price, landed supplier cost, and target CPA
                - 4 supplier & fulfillment playbooks (AliExpress, CJDropshipping, Zendrop, 3PL fast-line)
                - 3 ready-to-film TikTok / YouTube Shorts UGC ad creatives (hook + visual + CTA)
                - $0 to $10k/mo scaling & AOV bundle roadmap
            """.trimIndent()
        } else {
            """
                Act as Nexus SaaS MVP, Pitch & High-Ticket Funnel Architect.
                Venture / Idea: "$nicheOrIdea"
                Deliver:
                - Venture positioning headline
                - 4 core MVP product features + recurring pricing tiers
                - 4 technical architecture, auth, billing & Render deployment steps
                - 3 high-ticket outbound / VSL closing scripts
                - Customer acquisition & MRR scaling roadmap
            """.trimIndent()
        }
        val raw = businessEmpireModel.generateContent(prompt).text ?: throw IllegalStateException("Empty Business Empire output.")
        val json = JSONObject(raw)
        BusinessEmpireResult(
            mode = mode,
            headline = json.optString("headline", nicheOrIdea),
            coreDeliverables = json.optJSONArray("coreDeliverables").toStringList(),
            supplierOrTechStack = json.optJSONArray("supplierOrTechStack").toStringList(),
            adOrSalesScripts = json.optJSONArray("adOrSalesScripts").toStringList(),
            scalingPlaybook = json.optString("scalingPlaybook", "")
        )
    }
}
