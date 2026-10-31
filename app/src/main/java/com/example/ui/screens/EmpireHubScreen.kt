package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AutomationWorkflow
import com.example.viewmodel.BusinessEmpireResult
import com.example.viewmodel.ChannelHealthAndTrendResult
import com.example.viewmodel.GenerationBannerState
import com.example.viewmodel.MassRepurposeResult
import com.example.viewmodel.StreamState
import kotlin.math.roundToInt

private val AI_EMPLOYEE_ROSTER = listOf(
    "Lead Researcher" to "Deep competitor, trend & niche intelligence",
    "Viral Scriptwriter" to "0-3s hooks, Shorts scripts & loop CTAs",
    "Retention Video Editor" to "7s pattern interrupts, B-roll & FFmpeg spec",
    "Growth Marketer" to "3-Pack hashtags & multi-platform launch",
    "Dropship Product Hunter" to "High-margin products, CJ/AliExpress & UGC ads",
    "High-Ticket Closer" to "Offer stack, DM scripts & closing sequence"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmpireHubScreen(
    workflowsState: StreamState<List<AutomationWorkflow>>,
    repurposeResult: MassRepurposeResult?,
    channelAuditResult: ChannelHealthAndTrendResult?,
    businessEmpireResult: BusinessEmpireResult?,
    bannerState: GenerationBannerState,
    onRunMassRepurposer: (String) -> Unit,
    onRunChannelAudit: (String) -> Unit,
    onDeployAiEmployee: (String, String) -> Unit,
    onRunWorkflow: (AutomationWorkflow, String) -> Unit,
    onDeleteWorkflow: (String) -> Unit,
    onRunBusinessArchitect: (String, String) -> Unit,
    onLaunchStorefrontInBuilder: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }
    val isBusy = bannerState is GenerationBannerState.Working

    fun copyText(label: String, value: String) {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cb?.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Nexus Empire Hub",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "1-to-50 Social Repurposer, Autonomous AI Employees & Dropshipping / SaaS Architect",
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
                text = { Text("Social 1->50") },
                icon = { Icon(Icons.Default.Public, contentDescription = "Social Empire") },
                modifier = Modifier.testTag("empire_social_tab")
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("AI Employees") },
                icon = { Icon(Icons.Default.SmartToy, contentDescription = "AI Employees") },
                modifier = Modifier.testTag("empire_agents_tab")
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                text = { Text("Dropship & SaaS") },
                icon = { Icon(Icons.Default.Storefront, contentDescription = "Dropship and SaaS") },
                modifier = Modifier.testTag("empire_business_tab")
            )
        }

        when (selectedSubTab) {
            0 -> SocialEmpirePane(
                repurposeResult = repurposeResult,
                channelAuditResult = channelAuditResult,
                isBusy = isBusy,
                onRepurpose = onRunMassRepurposer,
                onAudit = onRunChannelAudit,
                onCopy = ::copyText
            )

            1 -> AiEmployeesPane(
                workflows = (workflowsState as? StreamState.Success)?.data.orEmpty(),
                isBusy = isBusy,
                onDeployEmployee = onDeployAiEmployee,
                onRunWorkflow = onRunWorkflow,
                onDeleteWorkflow = onDeleteWorkflow,
                onCopy = ::copyText
            )

            2 -> BusinessAndDropshipPane(
                result = businessEmpireResult,
                isBusy = isBusy,
                onRunArchitect = onRunBusinessArchitect,
                onBuildStorefront = onLaunchStorefrontInBuilder,
                onCopy = ::copyText
            )
        }
    }
}

@Composable
private fun SocialEmpirePane(
    repurposeResult: MassRepurposeResult?,
    channelAuditResult: ChannelHealthAndTrendResult?,
    isBusy: Boolean,
    onRepurpose: (String) -> Unit,
    onAudit: (String) -> Unit,
    onCopy: (String, String) -> Unit
) {
    var sourceInput by rememberSaveable { mutableStateOf("") }
    var auditInput by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "social_yt_promo") { PersistentYouTubePromoCard(compact = true) }

        item(key = "mass_repurpose_card") {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("1-to-50 Multi-Platform Content Repurposer", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Turn 1 video link or topic into native YouTube Shorts, TikToks, IG Reels, X Thread, LinkedIn, Threads & Community posts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sourceInput,
                        onValueChange = { sourceInput = it },
                        placeholder = { Text("Paste YouTube link, video title, or topic…") },
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("social_repurpose_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onRepurpose(sourceInput) },
                        enabled = !isBusy && sourceInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_mass_repurpose_button")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Repurpose Across All 7 Platforms")
                    }
                }
            }
        }

        if (repurposeResult != null) {
            item(key = "repurpose_output") {
                val combined = buildString {
                    appendLine("=== YOUTUBE SHORTS ===\n" + repurposeResult.youtubeShortsScripts.joinToString("\n"))
                    appendLine("\n=== TIKTOK ===\n" + repurposeResult.tiktokHooks.joinToString("\n"))
                    appendLine("\n=== IG REELS ===\n" + repurposeResult.igReelsCaptions.joinToString("\n"))
                    appendLine("\n=== X THREAD ===\n" + repurposeResult.xThreadPosts.joinToString("\n"))
                    appendLine("\n=== LINKEDIN ===\n" + repurposeResult.linkedinPost)
                    appendLine("\n=== THREADS ===\n" + repurposeResult.threadsPost)
                    appendLine("\n=== YT COMMUNITY ===\n" + repurposeResult.communityUpdate)
                }
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Multi-Platform Distribution Pack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("YouTube Shorts Outlines:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        repurposeResult.youtubeShortsScripts.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("TikTok & IG Reels Hooks:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        (repurposeResult.tiktokHooks + repurposeResult.igReelsCaptions).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("X / Twitter Authority Thread:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        repurposeResult.xThreadPosts.forEachIndexed { idx, t -> Text("${idx + 1}/ $t", style = MaterialTheme.typography.bodySmall) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("LinkedIn: ${repurposeResult.linkedinPost}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("YouTube Community: ${repurposeResult.communityUpdate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onCopy("Social Empire Pack", combined) },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy All Multi-Platform Posts")
                        }
                    }
                }
            }
        }

        item(key = "shadowban_trend_card") {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Shadowban Recovery & Early Trend Radar", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = auditInput,
                        onValueChange = { auditInput = it },
                        placeholder = { Text("Enter @handle or niche (e.g., @arnold60011 AI & Tech)…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("channel_audit_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onAudit(auditInput) },
                        enabled = !isBusy && auditInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_channel_audit_button")
                    ) { Text("Scan Algorithmic Health & Rising Trends") }

                    if (channelAuditResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Discovery Health Score: ${channelAuditResult.healthScore}/100", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Rising Viral Trends to Film Now:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        channelAuditResult.risingViralTrends.forEach { Text("🔥 $it", style = MaterialTheme.typography.bodySmall) }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Velocity & Recovery Protocol:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        channelAuditResult.recoveryProtocol.forEach { Text("✓ $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiEmployeesPane(
    workflows: List<AutomationWorkflow>,
    isBusy: Boolean,
    onDeployEmployee: (String, String) -> Unit,
    onRunWorkflow: (AutomationWorkflow, String) -> Unit,
    onDeleteWorkflow: (String) -> Unit,
    onCopy: (String, String) -> Unit
) {
    var selectedRole by rememberSaveable { mutableStateOf(AI_EMPLOYEE_ROSTER.first().first) }
    var missionInput by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "agents_yt_promo") { PersistentYouTubePromoCard(compact = true) }

        item(key = "deploy_employee_card") {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Deploy Autonomous AI Employees", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Pick a specialized AI agent role, assign your objective, and Nexus builds + runs the multi-step workflow live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AI_EMPLOYEE_ROSTER.forEach { (role, _) ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role },
                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(role) },
                                modifier = Modifier.height(48.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = missionInput,
                        onValueChange = { missionInput = it },
                        placeholder = { Text("Mission objective for $selectedRole (e.g., Launch 5 viral Shorts for @arnold60011)…") },
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("ai_employee_mission_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val m = missionInput
                            missionInput = ""
                            onDeployEmployee(selectedRole, m)
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("deploy_ai_employee_button")
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Deploy $selectedRole & Execute Now")
                    }
                }
            }
        }

        items(workflows, key = { it.id }) { wf ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(wf.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDeleteWorkflow(wf.id) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete agent")
                        }
                    }
                    Text("Objective: ${wf.goal}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val lastOutput = wf.lastRunOutput.orEmpty()
                    if (lastOutput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("LATEST DELIVERABLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { onCopy(wf.title, lastOutput) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy deliverable", modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(lastOutput.take(900), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onRunWorkflow(wf, "") },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-Run Autonomous Pipeline (Runs: ${wf.runCount})")
                    }
                }
            }
        }
    }
}

@Composable
private fun BusinessAndDropshipPane(
    result: BusinessEmpireResult?,
    isBusy: Boolean,
    onRunArchitect: (String, String) -> Unit,
    onBuildStorefront: (String) -> Unit,
    onCopy: (String, String) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("dropship") }
    var nicheInput by rememberSaveable { mutableStateOf("") }

    // Interactive Live Profit & ROAS Calculator State
    var supplierCost by rememberSaveable { mutableFloatStateOf(12f) }
    var sellingPrice by rememberSaveable { mutableFloatStateOf(49f) }
    var adCpa by rememberSaveable { mutableFloatStateOf(16f) }
    var dailyOrders by rememberSaveable { mutableFloatStateOf(15f) }

    val netPerOrder = (sellingPrice - supplierCost - adCpa)
    val marginPct = if (sellingPrice > 0f) ((netPerOrder / sellingPrice) * 100f).roundToInt() else 0
    val monthlyNet = (netPerOrder * dailyOrders * 30f).roundToInt()
    val breakEvenRoas = remember(sellingPrice, supplierCost) {
        val gross = (sellingPrice - supplierCost).coerceAtLeast(1f)
        ((sellingPrice / gross) * 100f).roundToInt() / 100f
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "biz_yt_promo") { PersistentYouTubePromoCard(compact = true) }

        item(key = "business_architect_card") {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == "dropship",
                            onClick = { mode = "dropship" },
                            label = { Text("Dropshipping Empire") },
                            modifier = Modifier.height(48.dp)
                        )
                        FilterChip(
                            selected = mode == "saas",
                            onClick = { mode = "saas" },
                            label = { Text("SaaS & High-Ticket Funnel") },
                            modifier = Modifier.height(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nicheInput,
                        onValueChange = { nicheInput = it },
                        placeholder = {
                            Text(
                                if (mode == "dropship") "Enter niche (e.g., Smart Fitness Recovery, Desk Tech, Pet Gadgets)…"
                                else "Enter SaaS or high-ticket offer idea (e.g., AI YouTube Clipping Micro-SaaS)…"
                            )
                        },
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("business_niche_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Smart Posture & Neck Recovery", "Creator Desk Lighting Tech", "AI Agency SaaS Offer").forEach { tpl ->
                            AssistChip(onClick = { nicheInput = tpl }, label = { Text(tpl, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onRunArchitect(mode, nicheInput) },
                            enabled = !isBusy && nicheInput.isNotBlank(),
                            modifier = Modifier.weight(1f).height(50.dp).testTag("run_business_architect_button")
                        ) { Text(if (mode == "dropship") "Find Products & Suppliers" else "Architect SaaS & Funnel", maxLines = 1) }

                        if (mode == "dropship") {
                            OutlinedButton(
                                onClick = { onBuildStorefront(nicheInput.ifBlank { "Smart Creator Tech Store" }) },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f).height(50.dp).testTag("launch_store_builder_button")
                            ) { Text("Build Live Store", maxLines = 1) }
                        }
                    }
                }
            }
        }

        if (result != null) {
            item(key = "business_result_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(result.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (result.mode == "dropship") "Winning High-Margin Products:" else "Core MVP & Pricing Stack:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        result.coreDeliverables.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (result.mode == "dropship") "Supplier Playbook (AliExpress / CJ / Zendrop):" else "Full-Stack & Render Deployment:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        result.supplierOrTechStack.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (result.mode == "dropship") "Viral TikTok/Shorts UGC Ad Scripts:" else "High-Ticket Closing Scripts:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        result.adOrSalesScripts.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Scaling Playbook: ${result.scalingPlaybook}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onCopy("Empire Blueprint", "${result.headline}\n\n${result.coreDeliverables.joinToString("\n")}\n\n${result.adOrSalesScripts.joinToString("\n")}") },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("Copy Complete Blueprint") }
                    }
                }
            }
        }

        // Interactive Live Dropshipping & Funnel Profit / ROAS Calculator
        item(key = "live_roas_calculator") {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Live Dropshipping Profit & ROAS Calculator", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Retail Price: $${sellingPrice.roundToInt()} • Supplier Cost: $${supplierCost.roundToInt()}", style = MaterialTheme.typography.bodySmall)
                    Slider(value = sellingPrice, onValueChange = { sellingPrice = it }, valueRange = 15f..199f)
                    Text("Landed Supplier Cost (CJ / AliExpress): $${supplierCost.roundToInt()}", style = MaterialTheme.typography.bodySmall)
                    Slider(value = supplierCost, onValueChange = { supplierCost = it }, valueRange = 2f..80f)
                    Text("Ad CPA (TikTok / Meta / YT Ads): $${adCpa.roundToInt()} • Orders/Day: ${dailyOrders.roundToInt()}", style = MaterialTheme.typography.bodySmall)
                    Slider(value = adCpa, onValueChange = { adCpa = it }, valueRange = 3f..60f)
                    Slider(value = dailyOrders, onValueChange = { dailyOrders = it }, valueRange = 1f..150f)

                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Net / Order", style = MaterialTheme.typography.labelSmall)
                                Text("$${netPerOrder.roundToInt()} ($marginPct%)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Column {
                                Text("Break-Even ROAS", style = MaterialTheme.typography.labelSmall)
                                Text("${breakEvenRoas}x", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Est. Monthly Net", style = MaterialTheme.typography.labelSmall)
                                Text("$$monthlyNet/mo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
