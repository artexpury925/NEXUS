package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Web
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.AutomationWorkflow
import com.example.data.model.StudioCreation
import com.example.util.openExternalUrl
import com.example.viewmodel.GenerationBannerState
import com.example.viewmodel.StreamState

@Composable
fun BuilderScreen(
    creationsState: StreamState<List<StudioCreation>>,
    workflowsState: StreamState<List<AutomationWorkflow>>,
    latestBuiltCreation: StudioCreation?,
    bannerState: GenerationBannerState,
    onGenerateCreation: (String, String) -> Unit,
    onCloneUrl: (String, String) -> Unit,
    onRebrandCreation: (StudioCreation, String) -> Unit,
    onGenerateDropship: (String) -> Unit,
    onClearLatestPreview: () -> Unit,
    onDeleteCreation: (String) -> Unit,
    onCreateWorkflow: (String) -> Unit,
    onRunWorkflow: (AutomationWorkflow, String) -> Unit,
    onDeleteWorkflow: (String) -> Unit
) {
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }
    var activePreviewCreation by remember { mutableStateOf<StudioCreation?>(null) }
    var activeSourceCreation by remember { mutableStateOf<StudioCreation?>(null) }

    LaunchedEffect(latestBuiltCreation) {
        if (latestBuiltCreation != null) {
            activePreviewCreation = latestBuiltCreation
            onClearLatestPreview()
        }
    }

    // Full-screen interactive HTML5 Web App / Game Preview
    if (activePreviewCreation != null) {
        BackHandler { activePreviewCreation = null }
        LiveWebCreationPreview(
            creation = activePreviewCreation!!,
            onClose = { activePreviewCreation = null },
            onViewSource = {
                val target = activePreviewCreation
                activePreviewCreation = null
                activeSourceCreation = target
            }
        )
        return
    }

    // Source code inspector sub-screen
    if (activeSourceCreation != null) {
        BackHandler { activeSourceCreation = null }
        CreationSourceViewer(
            creation = activeSourceCreation!!,
            onClose = { activeSourceCreation = null },
            onLaunchPreview = {
                val target = activeSourceCreation
                activeSourceCreation = null
                activePreviewCreation = target
            }
        )
        return
    }

    val isBusy = bannerState is GenerationBannerState.Working

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Nexus Builder",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Synthesize custom HTML5 web apps, playable games & automated workflows",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text(stringResource(R.string.builder_tab_apps)) },
                icon = { Icon(Icons.Default.SportsEsports, contentDescription = "Web Apps and Games") },
                modifier = Modifier.testTag("builder_apps_tab")
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text(stringResource(R.string.builder_tab_workflows)) },
                icon = { Icon(Icons.Default.AccountTree, contentDescription = "Workflow Automator") },
                modifier = Modifier.testTag("builder_workflows_tab")
            )
        }

        if (selectedSubTab == 0) {
            WebAndGameBuilderPane(
                creations = (creationsState as? StreamState.Success)?.data.orEmpty(),
                isBusy = isBusy,
                onGenerate = onGenerateCreation,
                onCloneUrl = onCloneUrl,
                onRebrandCreation = onRebrandCreation,
                onGenerateDropship = onGenerateDropship,
                onOpenPreview = { activePreviewCreation = it },
                onOpenSource = { activeSourceCreation = it },
                onDelete = onDeleteCreation
            )
        } else {
            WorkflowAutomatorPane(
                workflows = (workflowsState as? StreamState.Success)?.data.orEmpty(),
                isBusy = isBusy,
                onCreateWorkflow = onCreateWorkflow,
                onRunWorkflow = onRunWorkflow,
                onDeleteWorkflow = onDeleteWorkflow
            )
        }
    }
}

@Composable
private fun WebAndGameBuilderPane(
    creations: List<StudioCreation>,
    isBusy: Boolean,
    onGenerate: (String, String) -> Unit,
    onCloneUrl: (String, String) -> Unit,
    onRebrandCreation: (StudioCreation, String) -> Unit,
    onGenerateDropship: (String) -> Unit,
    onOpenPreview: (StudioCreation) -> Unit,
    onOpenSource: (StudioCreation) -> Unit,
    onDelete: (String) -> Unit
) {
    var buildPrompt by rememberSaveable { mutableStateOf("") }
    var customBrandName by rememberSaveable { mutableStateOf("") }
    var buildCategory by rememberSaveable { mutableStateOf("webapp") }
    var rebrandingItemId by remember { mutableStateOf<String?>(null) }
    var rebrandCommandInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "builder_yt_promo") {
            PersistentYouTubePromoCard(compact = true)
        }

        item(key = "builder_composer") {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = buildCategory == "webapp",
                            onClick = { buildCategory = "webapp" },
                            label = { Text("Web App") },
                            leadingIcon = { Icon(Icons.Default.Web, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.height(48.dp).testTag("category_webapp_chip")
                        )
                        FilterChip(
                            selected = buildCategory == "clone",
                            onClick = { buildCategory = "clone" },
                            label = { Text("Clone URL") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.height(48.dp).testTag("category_clone_chip")
                        )
                        FilterChip(
                            selected = buildCategory == "dropship",
                            onClick = { buildCategory = "dropship" },
                            label = { Text("Dropship Store") },
                            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.height(48.dp).testTag("category_dropship_chip")
                        )
                        FilterChip(
                            selected = buildCategory == "game",
                            onClick = { buildCategory = "game" },
                            label = { Text("HTML5 Game") },
                            leadingIcon = { Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.height(48.dp).testTag("category_game_chip")
                        )
                        FilterChip(
                            selected = buildCategory == "bot",
                            onClick = { buildCategory = "bot" },
                            label = { Text("Bot / Extension") },
                            leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.height(48.dp).testTag("category_bot_chip")
                        )
                        FilterChip(
                            selected = buildCategory == "panel",
                            onClick = { buildCategory = "panel" },
                            label = { Text("Control Panel") },
                            leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.height(48.dp).testTag("category_panel_chip")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = buildPrompt,
                        onValueChange = { buildPrompt = it },
                        placeholder = {
                            Text(
                                when (buildCategory) {
                                    "clone" -> "Paste any website/app link (e.g., https://linear.app or https://linktr.ee) to clone…"
                                    "dropship" -> "Enter winning product or niche (e.g., Posture Corrector Smart Wearable or LED Desk Lamp)…"
                                    "game" -> "Describe your game (e.g., Neon Cyber Brick Breaker with powerups & high score)…"
                                    "bot" -> "Describe Discord/Telegram Bot or Chrome Extension (e.g., YouTube Upload Alert Discord Bot + Simulator)…"
                                    "panel" -> "Describe Creator Control Panel (e.g., Arnold Chirchir Executive Subscriber & SaaS Command Center)…"
                                    else -> "Describe your web app (e.g., Creator Sponsorship Rate Calculator & CRM)…"
                                }
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("builder_prompt_input")
                    )

                    if (buildCategory == "clone") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customBrandName,
                            onValueChange = { customBrandName = it },
                            placeholder = { Text("Optional Custom Rebrand Name (e.g., NexusLink Pro)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clone_custom_brand_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick templates
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val templates = when (buildCategory) {
                            "clone" -> listOf(
                                "https://linktr.ee",
                                "https://notion.so",
                                "https://gumroad.com"
                            )
                            "dropship" -> listOf(
                                "Portable Neck Massager Store + ROAS Calculator",
                                "Magnetic Wireless Charging Station Dropship Store",
                                "Smart LED Galaxy Projector Storefront & Ad Kit"
                            )
                            "game" -> listOf(
                                "Neon Asteroid Dodge arcade game with touch controls & particle explosions",
                                "Cyber 2048 puzzle game with combo multiplier",
                                "Tap Reflex Speed Arena with 30s timer & ranks"
                            )
                            "bot" -> listOf(
                                "Discord & Telegram YouTube Drop Alert Bot + Slash Command Simulator",
                                "YouTube Title & Hashtag Grader Chrome Extension + manifest.json",
                                "AI Customer Support & Lead Capture Telegram Bot"
                            )
                            "panel" -> listOf(
                                "Arnold Chirchir (@arnold60011) Creator Empire Analytics & Revenue Control Panel",
                                "Multi-Channel YouTube Shorts Scheduling & KPI Command Center"
                            )
                            else -> listOf(
                                "YouTube Revenue & RPM Estimator dashboard with sliders",
                                "Pomodoro Deep Focus Studio with task checklist & stats",
                                "Personal Expense & Subscription Tracker with visual breakdown"
                            )
                        }
                        templates.forEach { tpl ->
                            AssistChip(
                                onClick = { buildPrompt = tpl },
                                label = { Text(if (tpl.length > 36) tpl.take(36) + "…" else tpl, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val p = buildPrompt
                            val brand = customBrandName
                            buildPrompt = ""
                            customBrandName = ""
                            when (buildCategory) {
                                "clone" -> onCloneUrl(p, brand)
                                "dropship" -> onGenerateDropship(p)
                                else -> onGenerate(p, buildCategory)
                            }
                        },
                        enabled = !isBusy && buildPrompt.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("generate_creation_button")
                    ) {
                        Text(
                            when (buildCategory) {
                                "clone" -> stringResource(R.string.build_clone_cta)
                                "dropship" -> stringResource(R.string.build_dropship_cta)
                                "game" -> stringResource(R.string.build_game_cta)
                                "bot" -> stringResource(R.string.build_bot_cta)
                                "panel" -> "Build Creator Control Panel"
                                else -> stringResource(R.string.build_webapp_cta)
                            }
                        )
                    }
                }
            }
        }

        if (creations.isEmpty()) {
            item(key = "empty_creations") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "No creations yet",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Apps, Clones, Dropshipping Stores & Games",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Build from a single sentence, clone any URL with your own brand, or launch a full dropshipping storefront.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(creations, key = { it.id }) { item ->
            val badgeLabel = when (item.category) {
                "game" -> "HTML5 GAME"
                "clone" -> "CLONED & REBRANDED"
                "dropship" -> "DROPSHIP STORE"
                else -> "WEB APP"
            }
            val isRebrandingThis = rebrandingItemId == item.id

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (item.category == "game" || item.category == "dropship")
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.category == "game" || item.category == "dropship")
                                    MaterialTheme.colorScheme.secondary
                                else
                                    MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { onDelete(item.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete creation",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onOpenPreview(item) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("launch_preview_${item.id}")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run")
                        }
                        OutlinedButton(
                            onClick = {
                                if (isRebrandingThis) {
                                    rebrandingItemId = null
                                } else {
                                    rebrandingItemId = item.id
                                    rebrandCommandInput = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("toggle_rebrand_${item.id}")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Rebrand", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rebrand")
                        }
                        OutlinedButton(
                            onClick = { onOpenSource(item) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = "Source Code", modifier = Modifier.size(18.dp))
                        }
                    }

                    if (isRebrandingThis) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Switch to Luxury Obsidian + Gold theme & add VIP badge",
                                "Add 3-tier Pricing Table + Interactive Checkout Modal",
                                "Rebrand header with custom @arnold60011 Creator Pro logo"
                            ).forEach { quickCmd ->
                                AssistChip(
                                    onClick = { rebrandCommandInput = quickCmd },
                                    label = { Text(quickCmd.take(30) + "…", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = rebrandCommandInput,
                            onValueChange = { rebrandCommandInput = it },
                            placeholder = { Text("e.g., Change app name to ApexOS, switch accent to emerald, add pricing tier") },
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rebrand_command_input_${item.id}")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val cmd = rebrandCommandInput
                                rebrandingItemId = null
                                rebrandCommandInput = ""
                                onRebrandCreation(item, cmd)
                            },
                            enabled = !isBusy && rebrandCommandInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("apply_rebrand_button_${item.id}")
                        ) {
                            Text(stringResource(R.string.rebrand_apply_cta))
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LiveWebCreationPreview(
    creation: StudioCreation,
    onClose: () -> Unit,
    onViewSource: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_preview_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Builder"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = creation.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Text(
                        text = "Interactive Sandbox Preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = onViewSource,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Code")
                }
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            if (url.startsWith("http") && !url.contains("nexus.studio.local")) {
                                val pkg = when {
                                    url.contains("youtube.com") || url.contains("youtu.be") -> "com.google.android.youtube"
                                    url.contains("instagram.com") -> "com.instagram.android"
                                    else -> null
                                }
                                openExternalUrl(ctx, url, pkg)
                                return true
                            }
                            return false
                        }
                    }
                    webChromeClient = WebChromeClient()
                    loadDataWithBaseURL(
                        "https://nexus.studio.local/",
                        creation.htmlCode,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            update = { webView ->
                val sig = "${creation.id}_${creation.htmlCode.hashCode()}"
                if (webView.tag != sig) {
                    webView.tag = sig
                    webView.loadDataWithBaseURL(
                        "https://nexus.studio.local/",
                        creation.htmlCode,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun CreationSourceViewer(
    creation: StudioCreation,
    onClose: () -> Unit,
    onLaunchPreview: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

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
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "${creation.title} • HTML5",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cb?.setPrimaryClip(ClipData.newPlainText("HTML Source", creation.htmlCode))
                        copied = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy HTML code")
                }
                Button(onClick = onLaunchPreview, modifier = Modifier.height(48.dp)) {
                    Text(if (copied) "Copied! Run" else "Run")
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val slug = creation.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "nexus-web-app" }
                            val renderYaml = """
                                services:
                                  - type: web
                                    name: $slug
                                    runtime: static
                                    buildCommand: echo "Static HTML5 bundle ready"
                                    staticPublishPath: .
                                    routes:
                                      - type: rewrite
                                        source: /*
                                        destination: /index.html
                            """.trimIndent()
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cb?.setPrimaryClip(ClipData.newPlainText("render.yaml", renderYaml))
                            copied = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_render_yaml_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("render.yaml", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                val slug = creation.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "index" }
                                val htmlFile = File(context.cacheDir, "$slug.html")
                                htmlFile.writeText(creation.htmlCode)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    htmlFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/html"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "${creation.title} (Built with Nexus)")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Export index.html"))
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_html_file_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .html", maxLines = 1)
                    }
                }
                Text(
                    text = creation.htmlCode,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun WorkflowAutomatorPane(
    workflows: List<AutomationWorkflow>,
    isBusy: Boolean,
    onCreateWorkflow: (String) -> Unit,
    onRunWorkflow: (AutomationWorkflow, String) -> Unit,
    onDeleteWorkflow: (String) -> Unit
) {
    var goalInput by rememberSaveable { mutableStateOf("") }
    var runtimeInput by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "workflow_yt_promo") {
            PersistentYouTubePromoCard(compact = true)
        }

        item(key = "workflow_creator_card") {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI Workflow Automator",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Describe a repeatable multi-step process and execute it on demand.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it },
                        placeholder = {
                            Text("e.g., Turn any tech news topic into a YouTube Short script, viral title, FYP hashtags & community post")
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("workflow_goal_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val g = goalInput
                            goalInput = ""
                            onCreateWorkflow(g)
                        },
                        enabled = !isBusy && goalInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_workflow_button")
                    ) {
                        Text(stringResource(R.string.build_workflow_cta))
                    }
                }
            }
        }

        if (workflows.isNotEmpty()) {
            item(key = "workflow_runtime_context") {
                OutlinedTextField(
                    value = runtimeInput,
                    onValueChange = { runtimeInput = it },
                    label = { Text("Optional Run Input / Topic for Next Execution") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(workflows, key = { it.id }) { wf ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${wf.triggerType.uppercase()} • RUNS: ${wf.runCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { onDeleteWorkflow(wf.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete workflow")
                        }
                    }

                    Text(text = wf.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    wf.steps.forEachIndexed { index, step ->
                        Text(
                            text = "${index + 1}. $step",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    if (!wf.lastRunOutput.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = wf.lastRunOutput,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onRunWorkflow(wf, runtimeInput) },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_workflow_${wf.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.run_workflow_cta))
                    }
                }
            }
        }
    }
}
