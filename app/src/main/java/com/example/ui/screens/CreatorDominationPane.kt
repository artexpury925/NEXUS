package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.CompetitorAuditResult
import com.example.viewmodel.ContentCalendarResult
import com.example.viewmodel.CreatorGrowthResult
import com.example.viewmodel.FullUploadPackageResult
import com.example.viewmodel.ImageGenState
import com.example.viewmodel.RetentionOptimizerResult
import com.example.viewmodel.TitleThumbLabResult

private val BUILT_IN_HOOK_VAULT = listOf(
    "Stop scrolling if you still post YouTube Shorts without doing THIS in the first 2 seconds…",
    "I tested 50 viral channels in 7 days, and 99% of creators miss this hidden retention trigger…",
    "Never say 'Hey guys' at the start of a video — say THIS 5-word sentence instead…",
    "Here is the exact blueprint top 0.1% faceless channels use to force the FYP algorithm…",
    "This 10-second edit trick doubled Average View Duration overnight (steal it right now)…",
    "Everyone tells you to post 3x a day. That is killing your impressions — do this instead…"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatorDominationPane(
    fypResult: CreatorGrowthResult?,
    titleThumbResult: TitleThumbLabResult?,
    thumbnailGenState: ImageGenState,
    retentionResult: RetentionOptimizerResult?,
    uploadPackageResult: FullUploadPackageResult?,
    calendarResult: ContentCalendarResult?,
    competitorResult: CompetitorAuditResult?,
    customHooksResult: List<String>,
    isBusy: Boolean,
    onGenerateFypKit: (String) -> Unit,
    onRunTitleThumbLab: (String) -> Unit,
    onGenerateThumbnailImage: (String) -> Unit,
    onRunRetentionOptimizer: (String) -> Unit,
    onRunUploadPackage: (String) -> Unit,
    onRunCalendar: (String, Int) -> Unit,
    onRunCompetitorAudit: (String) -> Unit,
    onGenerateCustomHooks: (String) -> Unit
) {
    val context = LocalContext.current
    // 0=FYP & 3-Pack Hashtags, 1=Title/Thumb Lab, 2=Retention, 3=Upload Pkg, 4=Calendar, 5=Competitor, 6=Hook Vault
    var activeTool by rememberSaveable { mutableIntStateOf(0) }
    var topicInput by rememberSaveable { mutableStateOf("") }
    var calendarHorizonDays by rememberSaveable { mutableIntStateOf(30) }
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cb?.setPrimaryClip(ClipData.newPlainText(label, text))
        copiedLabel = label
    }

    val toolTabs = remember {
        listOf(
            Triple(0, "1. FYP & 3-Pack Hashtags", Icons.Default.Tag),
            Triple(1, "2. Title & Thumbnail Lab", Icons.Default.Image),
            Triple(2, "3. Retention Optimizer", Icons.Default.Timeline),
            Triple(3, "4. Full Upload Package", Icons.Default.Inventory2),
            Triple(4, "5. 30/60/90 Calendar", Icons.Default.CalendarMonth),
            Triple(5, "6. Competitor Analyzer", Icons.Default.Radar),
            Triple(6, "7. Viral 3s Hook Vault", Icons.Default.FlashOn)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "domination_yt_promo") {
            PersistentYouTubePromoCard(compact = true)
        }

        item(key = "domination_tool_selector") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                toolTabs.forEach { (idx, title, icon) ->
                    FilterChip(
                        selected = activeTool == idx,
                        onClick = { activeTool = idx },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.height(48.dp).testTag("domination_tool_chip_$idx")
                    )
                }
            }
        }

        if (copiedLabel != null) {
            item(key = "copied_banner") {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Copied $copiedLabel to clipboard!",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Shared Input Card for active YouTube Domination Lab
        item(key = "domination_input_card") {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val (headline, subtitle, ctaText) = when (activeTool) {
                        1 -> Triple(
                            "Title & Thumbnail Nuclear Lab",
                            "Generate 15 high-CTR titles, 3-word overlay, color psychology, emotion cues & live AI thumbnail art.",
                            "Generate 15 Titles & Thumbnail Blueprint"
                        )
                        2 -> Triple(
                            "Retention Graph Script Optimizer",
                            "Paste your draft script or idea to inject a 0-3s hook, 7s pattern interrupts, and a 100%+ seamless loop CTA.",
                            "Optimize Script for Maximum AVD"
                        )
                        3 -> Triple(
                            "Full YouTube Upload Package",
                            "One-click Title + SEO Description + Studio Tags CSV + Hashtags + Info Cards + End Screen + Poll.",
                            "Generate Complete Upload Package"
                        )
                        4 -> Triple(
                            "30 / 60 / 90-Day Content Calendar",
                            "Map out daily Shorts + Long-Form upload times, viral hooks, and monetization milestones.",
                            "Build $calendarHorizonDays-Day Creator Calendar"
                        )
                        5 -> Triple(
                            "Competitor Destruction Analyzer",
                            "Enter any competitor channel name or niche to uncover weaknesses, content gaps, and 5 outlier video concepts.",
                            "Run Competitor Gap Audit"
                        )
                        6 -> Triple(
                            "Viral First-3-Second Hook Vault",
                            "Browse proven scroll-stoppers below or generate 12 custom psychological hooks for your video.",
                            "Generate 12 Custom Viral Hooks"
                        )
                        else -> Triple(
                            "FYP Algorithm & 3-Pack Hyper-Hashtags",
                            "Generate Safe, Aggressive, and Nuclear hashtag packs, FYP viral score, hooks & Studio SEO tags.",
                            "Engineer FYP & 3-Pack Hashtags"
                        )
                    }

                    Text(text = headline, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        placeholder = {
                            Text(
                                if (activeTool == 2) "Paste your draft script or video concept…"
                                else if (activeTool == 5) "Enter competitor handle or niche (e.g., AI Tech Tutorials)…"
                                else "Enter video topic or niche (e.g., Faceless AI Automation Empire 2026)…"
                            )
                        },
                        minLines = if (activeTool == 2) 3 else 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("fyp_topic_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "AI Tools & Creator Automation (@arnold60011)",
                            "Faceless YouTube Shorts Monetization",
                            "High-Income Coding & SaaS Builds"
                        ).forEach { preset ->
                            AssistChip(
                                onClick = { topicInput = preset },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (activeTool == 4) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 60, 90).forEach { d ->
                                FilterChip(
                                    selected = calendarHorizonDays == d,
                                    onClick = { calendarHorizonDays = d },
                                    label = { Text("$d Days") },
                                    modifier = Modifier.height(48.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            when (activeTool) {
                                1 -> onRunTitleThumbLab(topicInput)
                                2 -> onRunRetentionOptimizer(topicInput)
                                3 -> onRunUploadPackage(topicInput)
                                4 -> onRunCalendar(topicInput, calendarHorizonDays)
                                5 -> onRunCompetitorAudit(topicInput)
                                6 -> onGenerateCustomHooks(topicInput)
                                else -> onGenerateFypKit(topicInput)
                            }
                        },
                        enabled = !isBusy && topicInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("generate_fyp_kit_button")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(ctaText)
                    }
                }
            }
        }

        // --- TOOL 0 OUTPUT: FYP & 3-PACK HASHTAGS ---
        if (activeTool == 0 && fypResult != null) {
            item(key = "fyp_output_card") {
                val allHashtags = (fypResult.fypHashtags + fypResult.nicheHashtags).distinct().joinToString(" ")
                ResultContainerCard(title = "FYP Score ${fypResult.fypScore}/100 • 3-Pack Hyper-Hashtags") {
                    Text(fypResult.bestPostingTip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Safe Pack (Broad FYP & Shorts)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fypResult.fypHashtags.forEach { tag ->
                            val clean = if (tag.startsWith("#")) tag else "#$tag"
                            AssistChip(onClick = { copyToClipboard(clean, clean) }, label = { Text(clean) })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Aggressive Pack (Niche Velocity)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fypResult.nicheHashtags.forEach { tag ->
                            val clean = if (tag.startsWith("#")) tag else "#$tag"
                            AssistChip(onClick = { copyToClipboard(clean, clean) }, label = { Text(clean) })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Nuclear Pack (Search & Viral Tags)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(fypResult.searchKeywords.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { copyToClipboard("All Hashtags", allHashtags) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Copy Hashtags") }
                        OutlinedButton(
                            onClick = { copyToClipboard("Studio Tags CSV", fypResult.searchKeywords.joinToString(", ")) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Copy Tags CSV") }
                    }
                }
            }
        }

        // --- TOOL 1 OUTPUT: TITLE & THUMBNAIL NUCLEAR LAB ---
        if (activeTool == 1 && titleThumbResult != null) {
            item(key = "title_thumb_output") {
                ResultContainerCard(title = "Thumbnail Blueprint & 15 Nuclear Titles") {
                    Text("3-Word Overlay: \"${titleThumbResult.overlayText}\"", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Color Psychology: ${titleThumbResult.colorPsychology}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Emotion & Subject Cue: ${titleThumbResult.emotionCue}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onGenerateThumbnailImage(titleThumbResult.visualPrompt) },
                        enabled = thumbnailGenState !is ImageGenState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_ai_thumbnail_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Live AI Thumbnail (Nano Banana 2.1)")
                    }
                    when (thumbnailGenState) {
                        is ImageGenState.Loading -> {
                            Row(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Synthesizing 16:9 thumbnail art (30-60s)…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is ImageGenState.Success -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp))) {
                                Image(
                                    bitmap = thumbnailGenState.bitmap.asImageBitmap(),
                                    contentDescription = "Generated AI Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                                ) {
                                    Text(
                                        text = titleThumbResult.overlayText.uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        is ImageGenState.Error -> {
                            Text(
                                text = thumbnailGenState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        ImageGenState.Idle -> {}
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    titleThumbResult.titles.forEachIndexed { idx, t ->
                        CopyableLineItem(label = "Title #${idx + 1}", text = "${idx + 1}. $t", rawCopy = t, onCopy = ::copyToClipboard)
                    }
                }
            }
        }

        // --- TOOL 2 OUTPUT: RETENTION GRAPH OPTIMIZER ---
        if (activeTool == 2 && retentionResult != null) {
            item(key = "retention_output") {
                ResultContainerCard(title = "Retention Graph Script • ${retentionResult.predictedAvdBoost}") {
                    Text("0–3s Opening Hook:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    Text(retentionResult.openingHook0to3s, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("7-Second Pattern Interrupts:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    retentionResult.patternInterrupts.forEach { cue ->
                        Text("• $cue", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Rewritten High-AVD Script:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(retentionResult.rewrittenScript, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Seamless Loop CTA: ${retentionResult.seamlessLoopCta}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { copyToClipboard("Optimized Script", "${retentionResult.openingHook0to3s}\n\n${retentionResult.rewrittenScript}\n\n${retentionResult.seamlessLoopCta}") },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Copy Complete High-Retention Script") }
                }
            }
        }

        // --- TOOL 3 OUTPUT: FULL UPLOAD PACKAGE ---
        if (activeTool == 3 && uploadPackageResult != null) {
            item(key = "upload_pkg_output") {
                val fullText = buildString {
                    appendLine("TITLE: ${uploadPackageResult.title}")
                    appendLine("\nDESCRIPTION:\n${uploadPackageResult.seoDescription}")
                    appendLine("\nHASHTAGS: ${(uploadPackageResult.safeHashtags + uploadPackageResult.aggressiveHashtags + uploadPackageResult.nuclearHashtags).joinToString(" ")}")
                    appendLine("\nSTUDIO TAGS CSV: ${uploadPackageResult.studioTagsCsv}")
                    appendLine("\nPINNED COMMENT: ${uploadPackageResult.pinnedComment}")
                    appendLine("\nCOMMUNITY POLL:\n${uploadPackageResult.communityPoll}")
                }
                ResultContainerCard(title = "Ready-to-Paste YouTube Upload Package") {
                    CopyableLineItem("Upload Title", "Title: ${uploadPackageResult.title}", uploadPackageResult.title, ::copyToClipboard)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("SEO Description:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(uploadPackageResult.seoDescription, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Studio Tags CSV: ${uploadPackageResult.studioTagsCsv}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Info Cards: ${uploadPackageResult.infoCards.joinToString(" | ")}", style = MaterialTheme.typography.bodySmall)
                    Text("End Screen: ${uploadPackageResult.endScreenPlan}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    CopyableLineItem("Pinned Comment", "Pinned: ${uploadPackageResult.pinnedComment}", uploadPackageResult.pinnedComment, ::copyToClipboard)
                    CopyableLineItem("Community Poll", "Poll: ${uploadPackageResult.communityPoll}", uploadPackageResult.communityPoll, ::copyToClipboard)
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { copyToClipboard("Full Upload Package", fullText) },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Copy Entire Upload Package") }
                }
            }
        }

        // --- TOOL 4 OUTPUT: 30/60/90 CALENDAR ---
        if (activeTool == 4 && calendarResult != null) {
            item(key = "calendar_output") {
                ResultContainerCard(title = calendarResult.horizonLabel) {
                    Text("Best Upload Windows: ${calendarResult.bestPostingWindows}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Monetization & Brand Deals: ${calendarResult.monetizationPlaybook}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    calendarResult.scheduleItems.forEach { row ->
                        CopyableLineItem("Calendar Slot", row, row, ::copyToClipboard)
                    }
                }
            }
        }

        // --- TOOL 5 OUTPUT: COMPETITOR ANALYZER ---
        if (activeTool == 5 && competitorResult != null) {
            item(key = "competitor_output") {
                ResultContainerCard(title = "Competitor Audit: ${competitorResult.targetName}") {
                    Text("Weaknesses Exploitable Now:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    competitorResult.weaknessesFound.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("High-Demand Content Gaps:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    competitorResult.contentGaps.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("5 Outlier Video Concepts to Overtake Them:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    competitorResult.outlierVideoConcepts.forEach { idea ->
                        CopyableLineItem("Outlier Video Idea", idea, idea, ::copyToClipboard)
                    }
                }
            }
        }

        // --- TOOL 6 OUTPUT: VIRAL 3-SECOND HOOK VAULT ---
        if (activeTool == 6) {
            item(key = "hook_vault_card") {
                val hooksToShow = if (customHooksResult.isNotEmpty()) customHooksResult else BUILT_IN_HOOK_VAULT
                ResultContainerCard(
                    title = if (customHooksResult.isNotEmpty()) "12 Custom AI Viral Hooks" else "Proven First-3-Second Hook Vault"
                ) {
                    hooksToShow.forEachIndexed { idx, h ->
                        CopyableLineItem("Viral Hook #${idx + 1}", "${idx + 1}. $h", h, ::copyToClipboard)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultContainerCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun CopyableLineItem(label: String, text: String, rawCopy: String, onCopy: (String, String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { onCopy(label, rawCopy) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label", modifier = Modifier.size(16.dp))
            }
        }
    }
}
