package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.media.RenderHealthStatus
import com.example.util.openExternalUrl

@Composable
fun PipelineAndRenderCard(
    verticalCrop916: Boolean,
    useRenderCloudForUploads: Boolean,
    renderBaseUrl: String,
    renderHealthStatus: RenderHealthStatus?,
    isBusy: Boolean,
    onToggleVerticalCrop: (Boolean) -> Unit,
    onToggleRenderUploads: (Boolean) -> Unit,
    onSaveAndTestRenderUrl: (String) -> Unit
) {
    val context = LocalContext.current
    var urlInput by rememberSaveable(renderBaseUrl) { mutableStateOf(renderBaseUrl) }
    var showDeployGuide by rememberSaveable { mutableStateOf(false) }

    val isCloudConnected = renderBaseUrl.isNotBlank() && renderHealthStatus?.isOnline == true

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            1.dp,
            if (isCloudConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isCloudConnected) Icons.Default.Cloud else Icons.Default.Memory,
                    contentDescription = "Video Processing Pipeline",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pipeline_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = renderHealthStatus?.summary
                            ?: if (renderBaseUrl.isNotBlank()) "Render URL saved • Tap Test to verify FFmpeg"
                            else "On-Device Jetpack Media3 Transformer GPU Pipeline Active",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCloudConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (isCloudConnected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Render Cloud FFmpeg Online",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 9:16 Vertical Shorts Crop & H.264/AAC Transcode Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.CropPortrait,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.pipeline_vertical_crop_label),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Center-crops & scales clips to 720x1280 H.264 + AAC MP4",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = verticalCrop916,
                    onCheckedChange = onToggleVerticalCrop,
                    modifier = Modifier.testTag("pipeline_vertical_crop_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Render Cloud FFmpeg Endpoint Configuration
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Render Cloud FFmpeg URL (Optional)") },
                placeholder = { Text("https://nexus-ai-video-pipeline.onrender.com") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("render_backend_url_input")
            )

            if (renderBaseUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Process Uploaded Files on Render FFmpeg", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (useRenderCloudForUploads) "Uploads video to Render FFmpeg server"
                            else "Uses zero-latency on-device Media3 GPU pipeline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useRenderCloudForUploads,
                        onCheckedChange = onToggleRenderUploads,
                        modifier = Modifier.testTag("render_cloud_uploads_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveAndTestRenderUrl(urlInput) },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.25f)
                        .height(48.dp)
                        .testTag("save_render_url_button")
                ) {
                    Text(stringResource(R.string.render_connect_cta), maxLines = 1)
                }

                OutlinedButton(
                    onClick = { showDeployGuide = !showDeployGuide },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.95f)
                        .height(48.dp)
                        .testTag("render_deploy_guide_button")
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showDeployGuide) "Hide Guide" else "Deploy Guide", maxLines = 1)
                }
            }

            AnimatedVisibility(visible = showDeployGuide) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "1-Click Deploy on Render (render.yaml + Docker FFmpeg Ready)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Push this project to GitHub from AI Studio Settings.\n" +
                                "2. On dashboard.render.com, click New + -> Blueprint (or Web Service -> Docker) and select your repo.\n" +
                                "3. Render auto-detects render.yaml & Dockerfile, installs FFmpeg + yt-dlp, and launches the Nexus Cloud Clipper Web App + API.\n" +
                                "4. Paste your https://...onrender.com URL above to unlock direct cloud YouTube & URL stream cutting!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (renderBaseUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { openExternalUrl(context, renderBaseUrl) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Open Live Render Web Studio")
                            }
                        }
                    }
                }
            }
        }
    }
}
