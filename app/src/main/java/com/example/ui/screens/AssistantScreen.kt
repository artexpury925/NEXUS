package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChatMessage
import com.example.util.openExternalUrl
import com.example.viewmodel.GenerationBannerState
import com.example.viewmodel.StreamState

@Composable
fun AssistantScreen(
    messagesState: StreamState<List<ChatMessage>>,
    bannerState: GenerationBannerState,
    turboModeEnabled: Boolean,
    onToggleTurboMode: () -> Unit,
    onSendMessage: (String, String, Bitmap?) -> Unit,
    onClearChat: () -> Unit,
    onSmartJumpToGenerateVideo: (String) -> Unit = {},
    onSmartJumpToClipUrl: (String) -> Unit = {},
    onSmartJumpToCloneUrl: (String) -> Unit = {},
    onSmartJumpToDropship: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var inputText by rememberSaveable { mutableStateOf("") }
    var selectedMode by rememberSaveable { mutableStateOf("assistant") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                val maxSide = 1024
                val scale = minOf(maxSide.toFloat() / bmp.width, maxSide.toFloat() / bmp.height, 1f)
                attachedBitmap = Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } catch (_: Exception) {
            }
        }
    }

    val listState = rememberLazyListState()
    val messages = (messagesState as? StreamState.Success)?.data.orEmpty()
    val isBusy = bannerState is GenerationBannerState.Working || bannerState is GenerationBannerState.StreamingReply

    LaunchedEffect(messages.size, bannerState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top Header + Mode Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nexus Intelligence",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Owner: Arnold Chirchir • @arnold_60011",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FilterChip(
                selected = turboModeEnabled,
                onClick = onToggleTurboMode,
                label = {
                    Text(
                        text = if (turboModeEnabled) "Turbo Zero-Delay" else "Deep Mode",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Toggle Turbo Zero-Delay Speed",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.secondary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("turbo_speed_toggle_chip")
            )
            if (messages.isNotEmpty()) {
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.clear_chat_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Mode switcher pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeChip(
                selected = selectedMode == "assistant",
                label = stringResource(R.string.mode_assistant),
                icon = Icons.Default.AutoAwesome,
                onClick = { selectedMode = "assistant" },
                tag = "mode_assistant_chip"
            )
            ModeChip(
                selected = selectedMode == "task",
                label = stringResource(R.string.mode_task),
                icon = Icons.Default.TaskAlt,
                onClick = { selectedMode = "task" },
                tag = "mode_task_chip"
            )
            ModeChip(
                selected = selectedMode == "reflection",
                label = stringResource(R.string.mode_reflection),
                icon = Icons.Default.Psychology,
                onClick = { selectedMode = "reflection" },
                tag = "mode_reflection_chip"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Messages area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "creator_promo_banner") {
                PersistentYouTubePromoCard()
            }

            if (messages.isEmpty() && bannerState !is GenerationBannerState.StreamingReply) {
                item(key = "empty_welcome_card") {
                    EmptyAssistantHero(
                        onQuickPrompt = { quickPrompt, mode ->
                            selectedMode = mode
                            onSendMessage(quickPrompt, mode, null)
                        }
                    )
                }
            }

            items(messages, key = { it.id.ifBlank { "${it.role}_${it.text.hashCode()}" } }) { msg ->
                ChatBubble(message = msg)
            }

            if (bannerState is GenerationBannerState.StreamingReply) {
                item(key = "streaming_reply_bubble") {
                    ChatBubble(
                        message = ChatMessage(
                            id = "streaming",
                            role = "model",
                            text = bannerState.partialText,
                            mode = selectedMode
                        ),
                        isStreaming = true
                    )
                }
            }
        }

        // Attached image preview
        if (attachedBitmap != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = attachedBitmap!!.asImageBitmap(),
                        contentDescription = "Attached image preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Image attached for multimodal analysis",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { attachedBitmap = null },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove image"
                        )
                    }
                }
            }
        }

        // Smart Video Gen / Video URL Clip / Clone URL / Dropship Intent Detector Bar
        val detectedUrl = remember(inputText) {
            Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE).find(inputText)?.value
        }
        val isVideoOrYtLink = remember(detectedUrl) {
            if (detectedUrl == null) false
            else {
                val u = detectedUrl.lowercase().substringBefore("?")
                u.contains("youtube.com") || u.contains("youtu.be") ||
                    u.endsWith(".mp4") || u.endsWith(".mov") || u.endsWith(".webm") || u.endsWith(".m4v")
            }
        }
        val isVideoPromptIntent = remember(inputText, detectedUrl) {
            detectedUrl == null && (
                inputText.contains("generate a video", ignoreCase = true) ||
                    inputText.contains("make a video", ignoreCase = true) ||
                    inputText.contains("create a video", ignoreCase = true) ||
                    inputText.contains("ai video", ignoreCase = true) ||
                    inputText.contains("generate video", ignoreCase = true)
                )
        }
        val isDropshipIntent = remember(inputText) {
            inputText.contains("dropship", ignoreCase = true) || inputText.contains("shopify", ignoreCase = true) || inputText.contains("online store", ignoreCase = true)
        }

        if (isVideoPromptIntent || isVideoOrYtLink || detectedUrl != null || isDropshipIntent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isVideoPromptIntent) {
                    AssistChip(
                        onClick = {
                            val promptForVideo = inputText
                            inputText = ""
                            onSmartJumpToGenerateVideo(promptForVideo)
                        },
                        leadingIcon = { Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Generate Narrated MP4 Video") },
                        modifier = Modifier.height(48.dp).testTag("smart_jump_ai_video_chip")
                    )
                }
                if (isVideoOrYtLink && detectedUrl != null) {
                    AssistChip(
                        onClick = {
                            val target = detectedUrl
                            inputText = ""
                            onSmartJumpToClipUrl(target)
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Clip Video from Link") },
                        modifier = Modifier.height(48.dp).testTag("smart_jump_yt_clip_chip")
                    )
                } else if (detectedUrl != null) {
                    AssistChip(
                        onClick = {
                            val target = detectedUrl
                            inputText = ""
                            onSmartJumpToCloneUrl(target)
                        },
                        leadingIcon = { Icon(Icons.Default.Web, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Clone & Rebrand Website in Builder") },
                        modifier = Modifier.height(48.dp).testTag("smart_jump_clone_url_chip")
                    )
                }
                if (isDropshipIntent) {
                    AssistChip(
                        onClick = {
                            val niche = inputText
                            inputText = ""
                            onSmartJumpToDropship(niche)
                        },
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Launch Dropshipping Store Factory") },
                        modifier = Modifier.height(48.dp).testTag("smart_jump_dropship_chip")
                    )
                }
            }
        }

        // Composer Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("attach_image_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = stringResource(R.string.attach_image_desc),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.chat_input_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
                    .testTag("assistant_prompt_input")
            )

            IconButton(
                onClick = {
                    val bmp = attachedBitmap
                    val textToSend = inputText
                    inputText = ""
                    attachedBitmap = null
                    onSendMessage(textToSend, selectedMode, bmp)
                },
                enabled = !isBusy && (inputText.isNotBlank() || attachedBitmap != null),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_message_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.send_message_desc)
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier
            .height(48.dp)
            .testTag(tag)
    )
}

@Composable
private fun EmptyAssistantHero(
    onQuickPrompt: (String, String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "NEXUS SELF-AWARE CORE ONLINE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "How can Nexus accelerate your work today?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Ask deep questions, execute multi-step tasks, inspect Nexus's self-reflective architecture, or jump to Builder & Clip Studio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            val starters = listOf(
                "Who are you, who created you, and how does your architecture work?" to "reflection",
                "Write a 30-day YouTube Shorts viral growth plan with FYP hooks" to "task",
                "Draft a full SaaS landing page copy & launch checklist" to "task"
            )
            starters.forEachIndexed { idx, (prompt, mode) ->
                AssistChip(
                    onClick = { onQuickPrompt(prompt, mode) },
                    label = {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .testTag("starter_prompt_$idx")
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isStreaming: Boolean = false
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            border = BorderStroke(
                1.dp,
                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isUser) "YOU" else if (isStreaming) "NEXUS • STREAMING…" else "NEXUS • ${message.mode.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isUser && !isStreaming) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PersistentYouTubePromoCard(compact = true)
                }
            }
        }
    }
}
