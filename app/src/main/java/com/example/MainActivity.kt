package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.PersonPin
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutNexus
import com.example.data.repository.NexusRepository
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BuilderScreen
import com.example.ui.screens.ClipStudioScreen
import com.example.ui.screens.CreatorHubScreen
import com.example.ui.screens.CreatorOnboardingGateScreen
import com.example.ui.screens.PersistentYouTubePromoCard
import com.example.ui.theme.NexusTheme
import com.example.util.configureAppCheck
import com.example.viewmodel.GenerationBannerState
import com.example.viewmodel.NexusViewModel
import com.example.viewmodel.StreamState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure Firebase App Check with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            NexusTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NexusRootNavigation()
                }
            }
        }
    }
}

private data class NavDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun NexusRootNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        AuthenticatedNexusWorkspace(
            currentUserId = user.uid,
            displayName = user.displayName ?: "Nexus Creator",
            email = user.email ?: "creator@nexus.app"
        )
    }
}

@Composable
private fun AuthenticatedNexusWorkspace(
    currentUserId: String,
    displayName: String,
    email: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: NexusViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NexusViewModel(
                    repository = NexusRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = displayName,
                    initialEmail = email
                )
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val creationsState by viewModel.creationsState.collectAsStateWithLifecycle()
    val workflowsState by viewModel.workflowsState.collectAsStateWithLifecycle()
    val clipsState by viewModel.clipsState.collectAsStateWithLifecycle()
    val bannerState by viewModel.bannerState.collectAsStateWithLifecycle()
    val youtubeChannel by viewModel.youtubeChannel.collectAsStateWithLifecycle()
    val youtubeToken by viewModel.youtubeAccessToken.collectAsStateWithLifecycle()
    val creatorGrowthResult by viewModel.creatorGrowthResult.collectAsStateWithLifecycle()
    val latestBuiltCreation by viewModel.latestBuiltCreation.collectAsStateWithLifecycle()
    val youtubeLinkCandidates by viewModel.youtubeLinkCandidates.collectAsStateWithLifecycle()
    val turboModeEnabled by viewModel.turboModeEnabled.collectAsStateWithLifecycle()
    val verticalCrop916 by viewModel.verticalCrop916.collectAsStateWithLifecycle()
    val useRenderCloudForUploads by viewModel.useRenderCloudForUploads.collectAsStateWithLifecycle()
    val renderBaseUrl by viewModel.renderBaseUrl.collectAsStateWithLifecycle()
    val renderHealthStatus by viewModel.renderHealthStatus.collectAsStateWithLifecycle()

    LaunchedEffect(context) {
        viewModel.loadRenderPipelineConfig(context)
    }

    val profile = (profileState as? StreamState.Success)?.data
    if (profile != null && !profile.creatorSupportVisited) {
        CreatorOnboardingGateScreen(
            displayName = profile.displayName,
            onContinueToStudio = { viewModel.completeCreatorOnboarding() }
        )
        return
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var clipStudioInitialMode by rememberSaveable { mutableIntStateOf(0) }

    if (selectedTab != 0) {
        BackHandler { selectedTab = 0 }
    }

    val destinations = remember {
        listOf(
            NavDestination(R.string.nav_assistant, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_assistant"),
            NavDestination(R.string.nav_builder, Icons.Filled.Build, Icons.Outlined.Build, "nav_builder"),
            NavDestination(R.string.nav_clips_studio, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary, "nav_clips"),
            NavDestination(R.string.nav_creator, Icons.Filled.PersonPin, Icons.Outlined.PersonPin, "nav_creator")
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        destinations.forEachIndexed { idx, dest ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == idx) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .padding(8.dp)
                    ) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f)
                        ) {
                            destinations.forEachIndexed { idx, dest ->
                                NavigationRailItem(
                                    selected = selectedTab == idx,
                                    onClick = { selectedTab = idx },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == idx) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                        PersistentYouTubePromoCard(compact = true)
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    StatusNotificationBar(
                        bannerState = bannerState,
                        onDismiss = { viewModel.dismissBanner() }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            0 -> AssistantScreen(
                                messagesState = messagesState,
                                bannerState = bannerState,
                                turboModeEnabled = turboModeEnabled,
                                onToggleTurboMode = { viewModel.toggleTurboMode() },
                                onSendMessage = { prompt, mode, bmp ->
                                    viewModel.sendAssistantPrompt(prompt, mode, bmp)
                                },
                                onClearChat = { viewModel.clearConversation() },
                                onSmartJumpToGenerateVideo = { videoPrompt ->
                                    clipStudioInitialMode = 0
                                    selectedTab = 2
                                    viewModel.generateAiVideoFromPrompt(
                                        context = context,
                                        prompt = videoPrompt
                                    )
                                },
                                onSmartJumpToClipUrl = { videoOrYtUrl ->
                                    clipStudioInitialMode = 1
                                    selectedTab = 2
                                    viewModel.clipVideoFromLink(
                                        context = context,
                                        videoUrl = videoOrYtUrl
                                    )
                                },
                                onSmartJumpToCloneUrl = { siteUrl ->
                                    selectedTab = 1
                                    viewModel.cloneAppFromUrl(siteUrl, "")
                                },
                                onSmartJumpToDropship = { niche ->
                                    selectedTab = 1
                                    viewModel.generateDropshippingEmpire(niche)
                                }
                            )

                            1 -> BuilderScreen(
                                creationsState = creationsState,
                                workflowsState = workflowsState,
                                latestBuiltCreation = latestBuiltCreation,
                                bannerState = bannerState,
                                onGenerateCreation = { prompt, cat ->
                                    viewModel.generateWebAppOrGame(prompt, cat)
                                },
                                onCloneUrl = { targetUrl, brand ->
                                    viewModel.cloneAppFromUrl(targetUrl, brand)
                                },
                                onRebrandCreation = { creation, command ->
                                    viewModel.rebrandExistingCreation(creation, command)
                                },
                                onGenerateDropship = { niche ->
                                    viewModel.generateDropshippingEmpire(niche)
                                },
                                onClearLatestPreview = { viewModel.clearLatestPreview() },
                                onDeleteCreation = { viewModel.deleteCreation(it) },
                                onCreateWorkflow = { viewModel.createAutomationWorkflow(it) },
                                onRunWorkflow = { wf, input -> viewModel.executeWorkflowLive(wf, input) },
                                onDeleteWorkflow = { viewModel.deleteWorkflow(it) }
                            )

                            2 -> ClipStudioScreen(
                                clipsState = clipsState,
                                youtubeLinkCandidates = youtubeLinkCandidates,
                                youtubeChannel = youtubeChannel,
                                youtubeAccessToken = youtubeToken,
                                creatorGrowthResult = creatorGrowthResult,
                                bannerState = bannerState,
                                initialStudioMode = clipStudioInitialMode,
                                verticalCrop916 = verticalCrop916,
                                useRenderCloudForUploads = useRenderCloudForUploads,
                                renderBaseUrl = renderBaseUrl,
                                renderHealthStatus = renderHealthStatus,
                                onToggleVerticalCrop = { viewModel.setVerticalCrop916(it) },
                                onToggleRenderUploads = { viewModel.setUseRenderCloudForUploads(it) },
                                onSaveAndTestRenderUrl = { ctx, url -> viewModel.saveAndTestRenderUrl(ctx, url) },
                                onYouTubeTokenGranted = { viewModel.onYouTubeTokenGranted(it) },
                                onGenerateAiVideo = { ctx, prompt, dur, style, useArt, useVoice, autoPost, vis ->
                                    viewModel.generateAiVideoFromPrompt(
                                        context = ctx,
                                        prompt = prompt,
                                        durationSec = dur,
                                        visualStyle = style,
                                        useAiKeyframeArt = useArt,
                                        enableVoiceover = useVoice,
                                        autoPostToYouTube = autoPost,
                                        privacyStatus = vis
                                    )
                                },
                                onClipFromLink = { ctx, url, focusPrompt, autoPost, vis ->
                                    viewModel.clipVideoFromLink(
                                        context = ctx,
                                        videoUrl = url,
                                        clipPrompt = focusPrompt,
                                        autoPostToYouTube = autoPost,
                                        privacyStatus = vis
                                    )
                                },
                                onClipUploadedVideo = { ctx, uri, prompt, sliceThree, startS, endS, autoPost, vis ->
                                    viewModel.clipUploadedVideo(
                                        context = ctx,
                                        sourceVideoUri = uri,
                                        prompt = prompt,
                                        sliceThreeHighlights = sliceThree,
                                        customStartSec = startS,
                                        customEndSec = endS,
                                        autoPostToYouTube = autoPost,
                                        privacyStatus = vis
                                    )
                                },
                                onRenderLinkCandidate = { ctx, cand, autoPost, vis ->
                                    viewModel.renderLinkCandidateToMp4AndPublish(ctx, cand, autoPost, vis)
                                },
                                onCutCandidateViaRender = { ctx, cand, autoPost, vis ->
                                    viewModel.cutCandidateViaRenderFfmpeg(ctx, cand, autoPost, vis)
                                },
                                onExportClipToGallery = { ctx, clip ->
                                    viewModel.exportClipToDeviceGallery(ctx, clip)
                                },
                                onUploadClip = { clip, vis ->
                                    viewModel.uploadExistingClipToYouTube(clip, vis)
                                },
                                onMarkSharedViaApp = { viewModel.markClipSharedViaApp(it) },
                                onDeleteClip = { viewModel.deleteClip(it) },
                                onGenerateFypKit = { viewModel.generateCreatorFypKit(it) },
                                onQuickSendToAssistant = { prompt ->
                                    selectedTab = 0
                                    viewModel.sendAssistantPrompt(prompt, "task", null)
                                }
                            )

                            3 -> CreatorHubScreen(
                                userProfile = profile,
                                creationsCount = (creationsState as? StreamState.Success)?.data?.size ?: 0,
                                workflowsCount = (workflowsState as? StreamState.Success)?.data?.size ?: 0,
                                clipsCount = (clipsState as? StreamState.Success)?.data?.size ?: 0,
                                onAskEvolutionUpgrade = { prompt ->
                                    selectedTab = 0
                                    viewModel.sendAssistantPrompt(prompt, "reflection", null)
                                },
                                onSignOut = {
                                    signOutNexus(
                                        credentialManager = credentialManager,
                                        onSignOutComplete = {},
                                        scope = scope
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusNotificationBar(
    bannerState: GenerationBannerState,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = bannerState is GenerationBannerState.Working ||
            bannerState is GenerationBannerState.Error ||
            bannerState is GenerationBannerState.Notice
    ) {
        val bgColor = when (bannerState) {
            is GenerationBannerState.Error -> MaterialTheme.colorScheme.errorContainer
            is GenerationBannerState.Notice -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
        val contentColor = when (bannerState) {
            is GenerationBannerState.Error -> MaterialTheme.colorScheme.onErrorContainer
            is GenerationBannerState.Notice -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSecondaryContainer
        }

        Surface(
            color = bgColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (bannerState is GenerationBannerState.Working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = bannerState.statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                } else if (bannerState is GenerationBannerState.Error) {
                    Text(
                        text = bannerState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = contentColor)
                    }
                } else if (bannerState is GenerationBannerState.Notice) {
                    Text(
                        text = bannerState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss notice", tint = contentColor)
                    }
                }
            }
        }
    }
}
