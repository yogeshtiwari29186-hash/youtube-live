package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StreamConnectionState
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryLiveRed
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToPlaylist: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val playlist by viewModel.playlist.collectAsState()
    val currentVideo by viewModel.currentStreamingVideo.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val streamKey by viewModel.streamKey.collectAsState()

    // SAF File Picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addVideos(uris)
            onNavigateToPlaylist()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Header Hero Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    PrimaryLiveRed.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(PrimaryLiveRed, Color(0xFFFF5252))
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LiveTv,
                                    contentDescription = "Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "LocalStream Live",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Continuous YouTube Live Broadcaster",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        StatusBadge(state = connectionState)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Status text callout
                    Text(
                        text = statusMessage,
                        fontSize = 13.sp,
                        color = if (connectionState == StreamConnectionState.ERROR) PrimaryLiveRed else TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Action Center: Big START / STOP LIVE Button
        item {
            val isLiveOrConnecting = connectionState == StreamConnectionState.LIVE ||
                    connectionState == StreamConnectionState.CONNECTING ||
                    connectionState == StreamConnectionState.RECONNECTING ||
                    connectionState == StreamConnectionState.PAUSED

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isLiveOrConnecting) {
                        Button(
                            onClick = {
                                if (streamKey.isBlank()) {
                                    onNavigateToSetup()
                                } else if (playlist.isEmpty()) {
                                    videoPickerLauncher.launch(arrayOf("video/*"))
                                } else {
                                    viewModel.startLive()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("start_live_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryLiveRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "START LIVE",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (connectionState == StreamConnectionState.PAUSED) {
                                Button(
                                    onClick = { viewModel.resumeLive() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("resume_live_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resume", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.pauseLive() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("pause_live_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause", color = TextPrimary)
                                }
                            }

                            Button(
                                onClick = { viewModel.stopLive() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("stop_live_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLiveRed),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop LIVE", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (streamKey.isBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onNavigateToSetup() }
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("YouTube Stream Key not set. Tap to configure.", color = StatusAmber, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Live Dashboard Snippet if streaming
        item {
            AnimatedVisibility(visible = connectionState == StreamConnectionState.LIVE || connectionState == StreamConnectionState.RECONNECTING) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDashboard() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryLiveRed.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Live Dashboard Active", fontWeight = FontWeight.Bold, color = PrimaryLiveRed)
                            Text("Tap to view full telemetry ↗", fontSize = 12.sp, color = AccentCyan)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = currentVideo?.title ?: "Broadcasting playlist item...",
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        val progress = if (metrics.currentVideoDurationMs > 0) {
                            (metrics.currentVideoElapsedMs.toFloat() / metrics.currentVideoDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = PrimaryLiveRed,
                            trackColor = DarkCardBorder
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${metrics.bitrateKbps} kbps", fontSize = 12.sp, color = TextSecondary)
                            Text("${metrics.fps} FPS", fontSize = 12.sp, color = TextSecondary)
                            Text("Drop: ${metrics.droppedFrames}", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }

        // Quick Navigation Grid
        item {
            Text(
                text = "Studio Controls",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Select Local Videos Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { videoPickerLauncher.launch(arrayOf("video/*")) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(AccentCyan.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AccentCyan)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Select Videos", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Pick MP4, MKV, MOV", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                // Playlist Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToPlaylist() },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(AccentEmerald.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = AccentEmerald)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Playlist (${playlist.size})", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Loop & reorder items", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // YouTube Setup Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToSetup() },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(PrimaryLiveRed.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LiveTv, contentDescription = null, tint = PrimaryLiveRed)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("YouTube Setup", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(if (streamKey.isNotBlank()) "Key Configured ✓" else "Not configured", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                // Settings Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToSettings() },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(TextSecondary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = TextSecondary)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Settings", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Battery & Background", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        // Transparency & Storage Architecture Notice
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.6f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Zero-Cloud Local Storage Streaming",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your videos stay 100% on this Android device. The built-in streaming engine reads and streams local files directly into YouTube Live without uploading to any cloud storage.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
