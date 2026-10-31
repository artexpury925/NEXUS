package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.R
import com.example.data.model.VideoClipProject
import com.example.media.RenderHealthStatus
import com.example.media.VideoClipEngine
import com.example.media.VideoSourceMetadata
import com.example.util.openExternalUrl
import com.example.viewmodel.CompetitorAuditResult
import com.example.viewmodel.ContentCalendarResult
import com.example.viewmodel.CreatorGrowthResult
import com.example.viewmodel.FullUploadPackageResult
import com.example.viewmodel.GenerationBannerState
import com.example.viewmodel.ImageGenState
import com.example.viewmodel.RetentionOptimizerResult
import com.example.viewmodel.StreamState
import com.example.viewmodel.TitleThumbLabResult
import com.example.viewmodel.YouTubeLinkClipCandidate
import com.example.youtube.ConnectedYouTubeChannel
import com.example.youtube.YouTubeStudioManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ClipStudioScreen(
    clipsState: StreamState<List<VideoClipProject>>,
    youtubeLinkCandidates: List<YouTubeLinkClipCandidate>,
    youtubeChannel: ConnectedYouTubeChannel?,
    youtubeAccessToken: String?,
    creatorGrowthResult: CreatorGrowthResult?,
    bannerState: GenerationBannerState,
    initialStudioMode: Int = 0,
    verticalCrop916: Boolean = true,
    useRenderCloudForUploads: Boolean = false,
    renderBaseUrl: String = "",
    renderHealthStatus: RenderHealthStatus? = null,
    onToggleVerticalCrop: (Boolean) -> Unit = {},
    onToggleRenderUploads: (Boolean) -> Unit = {},
    onSaveAndTestRenderUrl: (Context, String) -> Unit = { _, _ -> },
    onYouTubeTokenGranted: (String) -> Unit,
    onGenerateAiVideo: (
        context: Context,
        prompt: String,
        durationSec: Int,
        visualStyle: String,
        useAiKeyframeArt: Boolean,
        enableVoiceover: Boolean,
        autoPost: Boolean,
        privacy: String
    ) -> Unit,
    onClipFromLink: (
        context: Context,
        videoUrl: String,
        clipPrompt: String,
        autoPost: Boolean,
        privacy: String
    ) -> Unit,
    onClipUploadedVideo: (
        context: Context,
        uri: Uri,
        prompt: String,
        sliceThreeHighlights: Boolean,
        customStartSec: Int?,
        customEndSec: Int?,
        autoPost: Boolean,
        privacy: String
    ) -> Unit,
    onRenderLinkCandidate: (Context, YouTubeLinkClipCandidate, Boolean, String) -> Unit,
    onCutCandidateViaRender: (Context, YouTubeLinkClipCandidate, Boolean, String) -> Unit = { _, _, _, _ -> },
    onBatchRenderTopCandidates: (Context, Boolean, String) -> Unit = { _, _, _ -> },
    latestReadyClip: VideoClipProject? = null,
    onClearLatestReadyClip: () -> Unit = {},
    titleThumbResult: TitleThumbLabResult? = null,
    thumbnailGenState: ImageGenState = ImageGenState.Idle,
    retentionResult: RetentionOptimizerResult? = null,
    uploadPackageResult: FullUploadPackageResult? = null,
    calendarResult: ContentCalendarResult? = null,
    competitorResult: CompetitorAuditResult? = null,
    customHooksResult: List<String> = emptyList(),
    onRunTitleThumbLab: (String) -> Unit = {},
    onGenerateThumbnailImage: (String) -> Unit = {},
    onRunRetentionOptimizer: (String) -> Unit = {},
    onRunUploadPackage: (String) -> Unit = {},
    onRunCalendar: (String, Int) -> Unit = { _, _ -> },
    onRunCompetitorAudit: (String) -> Unit = {},
    onGenerateCustomHooks: (String) -> Unit = {},
    onExportClipToGallery: (Context, VideoClipProject) -> Unit,
    onUploadClip: (VideoClipProject, String) -> Unit,
    onMarkSharedViaApp: (String) -> Unit,
    onDeleteClip: (VideoClipProject) -> Unit,
    onGenerateFypKit: (String) -> Unit,
    onQuickSendToAssistant: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var previewingClip by remember { mutableStateOf<VideoClipProject?>(null) }
    var authError by remember { mutableStateOf<String?>(null) }

    val authIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val authRes = YouTubeStudioManager.extractAuthorizationFromIntent(context, result.data)
                val token = authRes.accessToken
                if (!token.isNullOrBlank()) {
                    authError = null
                    onYouTubeTokenGranted(token)
                } else {
                    authError = "YouTube OAuth did not return an access token."
                }
            } catch (e: Exception) {
                authError = e.localizedMessage ?: "YouTube authorization failed."
            }
        }
    }

    if (previewingClip != null) {
        BackHandler { previewingClip = null }
        ClipPlayerScreen(
            clip = previewingClip!!,
            isYouTubeLinked = !youtubeAccessToken.isNullOrBlank(),
            onClose = { previewingClip = null },
            onSaveGallery = { onExportClipToGallery(context, previewingClip!!) },
            onUploadApi = { onUploadClip(previewingClip!!, "public") },
            onShareToYouTubeApp = {
                val ok = YouTubeStudioManager.shareClipToYouTubeApp(
                    context = context,
                    videoFile = File(previewingClip!!.localFilePath),
                    title = previewingClip!!.title,
                    description = previewingClip!!.description,
                    tags = previewingClip!!.tags
                )
                if (ok) onMarkSharedViaApp(previewingClip!!.id)
            }
        )
        return
    }

    val isBusy = bannerState is GenerationBannerState.Working

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "AI Video & Clip Studio",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Generate AI videos from prompts, clip videos via link or device upload & publish to YouTube",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("AI Video & Clipper") },
                icon = { Icon(Icons.Default.MovieCreation, contentDescription = "AI Video and Clipper") },
                modifier = Modifier.testTag("clip_publish_tab")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("YT Domination Suite") },
                icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "YouTube Domination Suite") },
                modifier = Modifier.testTag("fyp_hashtag_tab")
            )
        }

        if (selectedTab == 0) {
            ClipAndPublishPane(
                clips = (clipsState as? StreamState.Success)?.data.orEmpty(),
                latestReadyClip = latestReadyClip,
                onClearLatestReadyClip = onClearLatestReadyClip,
                youtubeLinkCandidates = youtubeLinkCandidates,
                youtubeChannel = youtubeChannel,
                isYouTubeConnected = !youtubeAccessToken.isNullOrBlank(),
                authError = authError,
                isBusy = isBusy,
                initialMode = initialStudioMode,
                verticalCrop916 = verticalCrop916,
                useRenderCloudForUploads = useRenderCloudForUploads,
                renderBaseUrl = renderBaseUrl,
                renderHealthStatus = renderHealthStatus,
                onToggleVerticalCrop = onToggleVerticalCrop,
                onToggleRenderUploads = onToggleRenderUploads,
                onSaveAndTestRenderUrl = { url -> onSaveAndTestRenderUrl(context, url) },
                onConnectYouTube = {
                    authError = null
                    scope.launch {
                        try {
                            val res = YouTubeStudioManager.requestYouTubeAuthorization(context)
                            if (res.hasResolution() && res.pendingIntent != null) {
                                authIntentLauncher.launch(
                                    IntentSenderRequest.Builder(res.pendingIntent!!.intentSender).build()
                                )
                            } else if (!res.accessToken.isNullOrBlank()) {
                                onYouTubeTokenGranted(res.accessToken!!)
                            }
                        } catch (e: Exception) {
                            authError = e.localizedMessage ?: "Could not launch YouTube OAuth."
                        }
                    }
                },
                onGenerateAiVideo = { prompt, dur, style, useArt, useVoice, autoPost, vis ->
                    onGenerateAiVideo(context, prompt, dur, style, useArt, useVoice, autoPost, vis)
                },
                onClipFromLink = { url, focusPrompt, autoPost, vis ->
                    onClipFromLink(context, url, focusPrompt, autoPost, vis)
                },
                onClipUploadedVideo = { uri, prompt, sliceThree, startS, endS, autoPost, vis ->
                    onClipUploadedVideo(context, uri, prompt, sliceThree, startS, endS, autoPost, vis)
                },
                onRenderLinkCandidate = { candidate, autoPost, privacy ->
                    onRenderLinkCandidate(context, candidate, autoPost, privacy)
                },
                onCutCandidateViaRender = { candidate, autoPost, privacy ->
                    onCutCandidateViaRender(context, candidate, autoPost, privacy)
                },
                onBatchRenderTopCandidates = { autoPost, privacy ->
                    onBatchRenderTopCandidates(context, autoPost, privacy)
                },
                onExportClipToGallery = { clip ->
                    onExportClipToGallery(context, clip)
                },
                onPreviewClip = { previewingClip = it },
                onUploadClip = onUploadClip,
                onShareViaApp = { clip ->
                    val ok = YouTubeStudioManager.shareClipToYouTubeApp(
                        context = context,
                        videoFile = File(clip.localFilePath),
                        title = clip.title,
                        description = clip.description,
                        tags = clip.tags
                    )
                    if (ok) onMarkSharedViaApp(clip.id)
                },
                onDeleteClip = onDeleteClip
            )
        } else {
            CreatorDominationPane(
                fypResult = creatorGrowthResult,
                titleThumbResult = titleThumbResult,
                thumbnailGenState = thumbnailGenState,
                retentionResult = retentionResult,
                uploadPackageResult = uploadPackageResult,
                calendarResult = calendarResult,
                competitorResult = competitorResult,
                customHooksResult = customHooksResult,
                isBusy = isBusy,
                onGenerateFypKit = onGenerateFypKit,
                onRunTitleThumbLab = onRunTitleThumbLab,
                onGenerateThumbnailImage = onGenerateThumbnailImage,
                onRunRetentionOptimizer = onRunRetentionOptimizer,
                onRunUploadPackage = onRunUploadPackage,
                onRunCalendar = onRunCalendar,
                onRunCompetitorAudit = onRunCompetitorAudit,
                onGenerateCustomHooks = onGenerateCustomHooks
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClipAndPublishPane(
    clips: List<VideoClipProject>,
    latestReadyClip: VideoClipProject?,
    onClearLatestReadyClip: () -> Unit,
    youtubeLinkCandidates: List<YouTubeLinkClipCandidate>,
    youtubeChannel: ConnectedYouTubeChannel?,
    isYouTubeConnected: Boolean,
    authError: String?,
    isBusy: Boolean,
    initialMode: Int,
    verticalCrop916: Boolean,
    useRenderCloudForUploads: Boolean,
    renderBaseUrl: String,
    renderHealthStatus: RenderHealthStatus?,
    onToggleVerticalCrop: (Boolean) -> Unit,
    onToggleRenderUploads: (Boolean) -> Unit,
    onSaveAndTestRenderUrl: (String) -> Unit,
    onConnectYouTube: () -> Unit,
    onGenerateAiVideo: (String, Int, String, Boolean, Boolean, Boolean, String) -> Unit,
    onClipFromLink: (String, String, Boolean, String) -> Unit,
    onClipUploadedVideo: (Uri, String, Boolean, Int?, Int?, Boolean, String) -> Unit,
    onRenderLinkCandidate: (YouTubeLinkClipCandidate, Boolean, String) -> Unit,
    onCutCandidateViaRender: (YouTubeLinkClipCandidate, Boolean, String) -> Unit,
    onBatchRenderTopCandidates: (Boolean, String) -> Unit,
    onExportClipToGallery: (VideoClipProject) -> Unit,
    onPreviewClip: (VideoClipProject) -> Unit,
    onUploadClip: (VideoClipProject, String) -> Unit,
    onShareViaApp: (VideoClipProject) -> Unit,
    onDeleteClip: (VideoClipProject) -> Unit
) {
    val context = LocalContext.current

    // 0 = AI Prompt -> Video | 1 = Clip from Link | 2 = Upload & Clip Video
    var studioMode by rememberSaveable(initialMode) { mutableIntStateOf(initialMode) }

    // Mode 0: AI Prompt-to-Video state
    var aiVideoPrompt by rememberSaveable { mutableStateOf("") }
    var aiVideoDurationSec by rememberSaveable { mutableIntStateOf(12) }
    var aiVisualStyle by rememberSaveable { mutableStateOf("Cyber Neon") }
    var enableVoiceover by rememberSaveable { mutableStateOf(true) }
    var useAiKeyframeArt by rememberSaveable { mutableStateOf(false) }

    // Mode 1: Link Clipper state
    var linkUrlInput by rememberSaveable { mutableStateOf("") }
    var linkFocusPrompt by rememberSaveable { mutableStateOf("") }

    // Mode 2: Uploaded Video Clipper state
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var uploadedMeta by remember { mutableStateOf<VideoSourceMetadata?>(null) }
    var uploadClipPrompt by rememberSaveable { mutableStateOf("") }
    var customStartInput by rememberSaveable { mutableStateOf("") }
    var customEndInput by rememberSaveable { mutableStateOf("") }

    // Shared publish preferences
    var autoPost by rememberSaveable { mutableStateOf(false) }
    var privacyStatus by rememberSaveable { mutableStateOf("public") }

    LaunchedEffect(selectedVideoUri) {
        val uri = selectedVideoUri
        uploadedMeta = if (uri != null) {
            VideoClipEngine.inspectVideo(context, uri)
        } else {
            null
        }
    }

    val galleryVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            studioMode = 2
        }
    }

    val documentVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            selectedVideoUri = uri
            studioMode = 2
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "creator_promo_clip_top") {
            PersistentYouTubePromoCard()
        }

        // High-visibility Latest Ready MP4 Spotlight Card so user immediately sees & plays newly rendered video!
        val spotlightClip = latestReadyClip ?: clips.firstOrNull()
        if (spotlightClip != null && File(spotlightClip.localFilePath).exists()) {
            item(key = "spotlight_ready_clip_${spotlightClip.id}") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🎬 LATEST READY HD MP4 VIDEO (${spotlightClip.durationSec}s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = spotlightClip.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onClearLatestReadyClip()
                                    onPreviewClip(spotlightClip)
                                },
                                modifier = Modifier.weight(1.2f).height(48.dp).testTag("spotlight_play_clip_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play Now")
                            }
                            OutlinedButton(
                                onClick = { onExportClipToGallery(spotlightClip) },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save MP4")
                            }
                        }
                    }
                }
            }
        }

        // 3-Mode Selector Bar: Prompt -> Video | Clip via Link | Upload Video
        item(key = "video_studio_mode_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = studioMode == 0,
                    onClick = { studioMode = 0 },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("1. AI Prompt -> Video") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.height(48.dp).testTag("video_mode_prompt_chip")
                )
                FilterChip(
                    selected = studioMode == 1,
                    onClick = { studioMode = 1 },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("2. Clip via Link") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.height(48.dp).testTag("video_mode_link_chip")
                )
                FilterChip(
                    selected = studioMode == 2,
                    onClick = { studioMode = 2 },
                    leadingIcon = { Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("3. Upload & Clip Video") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.height(48.dp).testTag("video_mode_upload_chip")
                )
            }
        }

        // MODE 0: AI PROMPT-TO-VIDEO GENERATOR CARD
        if (studioMode == 0) {
            item(key = "ai_prompt_to_video_card") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Generate AI Video from Prompt",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Describe any topic, story, or script. Nexus writes a 4-scene storyboard, synthesizes spoken AI voiceover + ambient audio, and encodes a vertical HD 720x1280 MP4.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "3 AI Tools Replacing Whole Teams in 2026",
                                "Dark Psychology Rules Every Creator Should Know",
                                "How Nexus Automates a Faceless YouTube Empire"
                            ).forEach { preset ->
                                AssistChip(
                                    onClick = { aiVideoPrompt = preset },
                                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = aiVideoPrompt,
                            onValueChange = { aiVideoPrompt = it },
                            placeholder = { Text("Describe the video you want Nexus AI to generate…") },
                            minLines = 3,
                            maxLines = 4,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_video_prompt_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Video Duration & Visual Style", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(8, 12, 18).forEach { sec ->
                                FilterChip(
                                    selected = aiVideoDurationSec == sec,
                                    onClick = { aiVideoDurationSec = sec },
                                    label = { Text("${sec}s HD") },
                                    modifier = Modifier.height(48.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Cyber Neon", "Crimson Viral", "Cinematic Gold", "Emerald Matrix").forEach { style ->
                                FilterChip(
                                    selected = aiVisualStyle == style,
                                    onClick = { aiVisualStyle = style },
                                    label = { Text(style) },
                                    modifier = Modifier.height(48.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AI Spoken Voiceover + Audio Track", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Synthesizes narrated speech + AAC pulse into MP4",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = enableVoiceover,
                                onCheckedChange = { enableVoiceover = it },
                                modifier = Modifier.testTag("ai_video_voiceover_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AI Keyframe Visual (Nano Banana 2.1)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Generates AI scene artwork + Ken Burns motion (30-60s, Blaze)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useAiKeyframeArt,
                                onCheckedChange = { useAiKeyframeArt = it },
                                modifier = Modifier.testTag("ai_video_keyframe_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val p = aiVideoPrompt
                                aiVideoPrompt = ""
                                onGenerateAiVideo(
                                    p,
                                    aiVideoDurationSec,
                                    aiVisualStyle,
                                    useAiKeyframeArt,
                                    enableVoiceover,
                                    autoPost,
                                    privacyStatus
                                )
                            },
                            enabled = !isBusy && aiVideoPrompt.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("generate_ai_video_button")
                        ) {
                            Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (autoPost && isYouTubeConnected) "Generate AI Video & Post to YouTube"
                                else stringResource(R.string.generate_ai_video_cta)
                            )
                        }
                    }
                }
            }
        }

        // MODE 1: UNIVERSAL LINK VIDEO CLIPPER (YOUTUBE URL OR DIRECT MP4 LINK)
        if (studioMode == 1) {
            item(key = "youtube_url_clipper_card") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Clip Video by Inputting Any Link",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Paste a direct .mp4 / video stream URL to download & losslessly trim the top highlight, OR paste any YouTube link to extract 8 viral Shorts clips + auto-render Clip #1 MP4!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = linkUrlInput,
                            onValueChange = { linkUrlInput = it },
                            placeholder = { Text("https://youtube.com/watch?v=... or https://.../video.mp4") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("youtube_url_clip_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = linkFocusPrompt,
                            onValueChange = { linkFocusPrompt = it },
                            placeholder = { Text("Optional clipping prompt: e.g. Cut the best 15s hook & add FYP tags…") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("link_clip_focus_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                onClipFromLink(linkUrlInput, linkFocusPrompt, autoPost, privacyStatus)
                            },
                            enabled = !isBusy && linkUrlInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("analyze_youtube_url_button")
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.analyze_youtube_link_cta))
                        }
                    }
                }
            }
        }

        // MODE 2: UPLOAD VIDEO FROM DEVICE & AUTO-CLIP / MULTI-SLICE
        if (studioMode == 2) {
            item(key = "clip_factory_card") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Upload Video & Auto-Clip Highlights",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Upload any video from your Gallery or Files. Nexus inspects multi-point keyframes to cut 1 viral highlight, slice 3 clips automatically, or trim an exact timestamp window.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    galleryVideoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .testTag("pick_video_button")
                            ) {
                                Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.pick_video_cta), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    documentVideoPicker.launch(arrayOf("video/*"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(48.dp)
                                    .testTag("pick_video_file_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.pick_video_file_cta), maxLines = 1)
                            }
                        }

                        if (selectedVideoUri != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val thumb = uploadedMeta?.thumbnail
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = "Uploaded video preview frame",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Uploaded Source Ready ✓",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = uploadedMeta?.let {
                                                "Duration: ${it.durationSec}s • ${it.width}x${it.height} • ${it.sampleFrames.size} AI Keyframes"
                                            } ?: "Inspecting video stream…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            selectedVideoUri = null
                                            customStartInput = ""
                                            customEndInput = ""
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Clear")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customStartInput,
                                    onValueChange = { customStartInput = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Start Sec (Auto)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("custom_trim_start_input")
                                )
                                OutlinedTextField(
                                    value = customEndInput,
                                    onValueChange = { customEndInput = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("End Sec (Auto)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("custom_trim_end_input")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = uploadClipPrompt,
                            onValueChange = { uploadClipPrompt = it },
                            placeholder = {
                                Text("Prompt: Extract the highest-energy moment, write viral title & FYP hashtags…")
                            },
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clip_prompt_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val uri = selectedVideoUri ?: return@Button
                                    val p = uploadClipPrompt
                                    val sSec = customStartInput.toIntOrNull()
                                    val eSec = customEndInput.toIntOrNull()
                                    uploadClipPrompt = ""
                                    onClipUploadedVideo(uri, p, false, sSec, eSec, autoPost, privacyStatus)
                                },
                                enabled = !isBusy && selectedVideoUri != null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("create_clip_button")
                            ) {
                                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.generate_clip_cta), maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val uri = selectedVideoUri ?: return@OutlinedButton
                                    val p = uploadClipPrompt
                                    uploadClipPrompt = ""
                                    onClipUploadedVideo(uri, p, true, null, null, false, privacyStatus)
                                },
                                enabled = !isBusy && selectedVideoUri != null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("slice_three_clips_button")
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.generate_multi_clip_cta), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Video Processing Pipeline (On-Device Media3 Transformer + Render Cloud FFmpeg)
        item(key = "video_pipeline_and_render_card") {
            PipelineAndRenderCard(
                verticalCrop916 = verticalCrop916,
                useRenderCloudForUploads = useRenderCloudForUploads,
                renderBaseUrl = renderBaseUrl,
                renderHealthStatus = renderHealthStatus,
                isBusy = isBusy,
                onToggleVerticalCrop = onToggleVerticalCrop,
                onToggleRenderUploads = onToggleRenderUploads,
                onSaveAndTestRenderUrl = onSaveAndTestRenderUrl
            )
        }

        // Extracted YouTube / Universal Link Candidates (shown whenever populated)
        if (youtubeLinkCandidates.isNotEmpty()) {
            item(key = "batch_render_top_3_header") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Extracted ${youtubeLinkCandidates.size} Viral Shorts Blueprints", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Auto-render the top 3 clips into narrated vertical MP4s in 1 tap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onBatchRenderTopCandidates(autoPost, privacyStatus) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.height(48.dp).testTag("batch_render_top_3_button")
                        ) {
                            Text("Batch Top 3")
                        }
                    }
                }
            }
            items(youtubeLinkCandidates, key = { "yt_cand_${it.index}_${it.title}" }) { cand ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "CLIP #${cand.index} • ${cand.timestampRange}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cb?.setPrimaryClip(ClipData.newPlainText("FFmpeg Clip Command", cand.ffmpegCmd))
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy yt-dlp and FFmpeg clip command")
                            }
                        }
                        Text(text = cand.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hook (0-3s): \"${cand.hook}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = cand.hashtags.joinToString(" ") { "#$it" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (renderBaseUrl.isNotBlank()) {
                                Button(
                                    onClick = { onCutCandidateViaRender(cand, autoPost, privacyStatus) },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("cut_ffmpeg_yt_candidate_${cand.index}")
                                ) {
                                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.render_cut_real_clip_cta), maxLines = 1)
                                }
                            }
                            Button(
                                onClick = { onRenderLinkCandidate(cand, autoPost, privacyStatus) },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("render_yt_candidate_${cand.index}")
                            ) {
                                Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (renderBaseUrl.isNotBlank()) "AI Short + Voice"
                                    else if (autoPost && isYouTubeConnected) "Render MP4 & Auto-Upload YT"
                                    else "Render Vertical MP4 Short (+ Voice)",
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // YouTube Channel OAuth & Auto-Post Preferences Card
        item(key = "youtube_connection_card") {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(
                    1.dp,
                    if (isYouTubeConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isYouTubeConnected) Icons.Default.CheckCircle else Icons.Default.Link,
                            contentDescription = "YouTube Connection Status",
                            tint = if (isYouTubeConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isYouTubeConnected) {
                                    youtubeChannel?.title ?: stringResource(R.string.youtube_connected_badge)
                                } else {
                                    "Connect Your YouTube Account"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isYouTubeConnected) {
                                    "${youtubeChannel?.handle.orEmpty()} • Direct API Shorts Upload Active"
                                } else {
                                    "Authorize YouTube Data API v3 to auto-post generated & clipped MP4s"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!authError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = authError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (!isYouTubeConnected) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onConnectYouTube,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("connect_youtube_button")
                        ) {
                            Text(stringResource(R.string.connect_youtube_cta))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Post to YouTube after Render/Clip", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Uploads automatically when YouTube is connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoPost,
                            onCheckedChange = { autoPost = it },
                            modifier = Modifier.testTag("auto_post_youtube_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("public", "unlisted", "private").forEach { vis ->
                            FilterChip(
                                selected = privacyStatus == vis,
                                onClick = { privacyStatus = vis },
                                label = { Text(vis.replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.height(48.dp)
                            )
                        }
                    }
                }
            }
        }

        // Saved Videos & Clips List
        items(clips, key = { it.id }) { clip ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val badgeLabel = if (clip.sourceType == "trimmed") "CLIPPED MP4" else "AI VIDEO MP4"
                            Text(
                                text = "$badgeLabel • ${clip.durationSec}s • ${clip.youtubeStatus.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { onDeleteClip(clip) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete clip")
                        }
                    }

                    Text(text = clip.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = clip.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )

                    if (clip.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            clip.tags.take(6).forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "#$tag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!clip.youtubeVideoId.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AssistChip(
                            onClick = {
                                openExternalUrl(
                                    context,
                                    "https://youtube.com/shorts/${clip.youtubeVideoId}",
                                    "com.google.android.youtube"
                                )
                            },
                            label = { Text("Watch Uploaded Short (${clip.youtubeVideoId})") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onPreviewClip(clip) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.95f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onExportClipToGallery(clip) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_gallery_clip_${clip.id}")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.save_gallery_cta), maxLines = 1)
                        }
                        Button(
                            onClick = {
                                if (isYouTubeConnected) onUploadClip(clip, privacyStatus)
                                else onShareViaApp(clip)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("publish_clip_${clip.id}")
                        ) {
                            Icon(
                                if (isYouTubeConnected) Icons.Default.CloudUpload else Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isYouTubeConnected) "Upload YT" else "YT App", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipPlayerScreen(
    clip: VideoClipProject,
    isYouTubeLinked: Boolean,
    onClose: () -> Unit,
    onSaveGallery: () -> Unit,
    onUploadApi: () -> Unit,
    onShareToYouTubeApp: () -> Unit
) {
    val context = LocalContext.current
    val videoFile = remember(clip.localFilePath) { File(clip.localFilePath) }
    val fileExists = remember(videoFile) { videoFile.exists() && videoFile.length() > 0L }

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    val exoPlayer = remember(clip.localFilePath) {
        if (fileExists) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
        } else {
            null
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("close_clip_player_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clip.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Text(
                        text = "Media3 HD Player • ${clip.durationSec}s MP4",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val meta = "${clip.title}\n\n${clip.description}\n\n${clip.tags.joinToString(" ") { "#$it" }}"
                        cb?.setPrimaryClip(ClipData.newPlainText("Clip Metadata", meta))
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Clip Metadata")
                }
                IconButton(onClick = onSaveGallery, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Save MP4 to Gallery")
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (fileExists && exoPlayer != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).also { tv ->
                                exoPlayer.setVideoTextureView(tv)
                            }
                        },
                        update = { tv ->
                            exoPlayer.setVideoTextureView(tv)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Text(
                    text = "Local MP4 file is no longer cached on this device. Generate or clip a new video above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (exoPlayer != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            isPlaying = false
                        } else {
                            exoPlayer.play()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.height(48.dp).testTag("player_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPlaying) "Pause" else "Play")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        exoPlayer.seekTo(0L)
                        exoPlayer.play()
                        isPlaying = true
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Replay, contentDescription = "Replay")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Replay")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute"
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            PersistentYouTubePromoCard(compact = true)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isYouTubeLinked) {
                Button(
                    onClick = onUploadApi,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.upload_youtube_cta))
                }
            }
            OutlinedButton(
                onClick = onShareToYouTubeApp,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(stringResource(R.string.share_youtube_app_cta))
            }
        }
    }
}
