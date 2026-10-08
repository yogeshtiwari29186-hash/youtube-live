package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoFile
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
import androidx.compose.ui.text.font.FontFamily
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
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val currentVideo by viewModel.currentStreamingVideo.collectAsState()
    val playlist by viewModel.playlist.collectAsState()
    val currentIndex by viewModel.currentVideoIndex.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val streamHours = metrics.streamDurationSeconds / 3600
    val streamMinutes = (metrics.streamDurationSeconds % 3600) / 60
    val streamSeconds = metrics.streamDurationSeconds % 60
    val formattedStreamDuration = String.format("%02d:%02d:%02d", streamHours, streamMinutes, streamSeconds)

    val currentVideoProgress = if (metrics.currentVideoDurationMs > 0) {
        (metrics.currentVideoElapsedMs.toFloat() / metrics.currentVideoDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val elapsedSec = metrics.currentVideoElapsedMs / 1000
    val totalSec = metrics.currentVideoDurationMs / 1000
    val formattedElapsed = String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60)
    val formattedTotal = String.format("%02d:%02d", totalSec / 60, totalSec % 60)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Live Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (connectionState == StreamConnectionState.LIVE) PrimaryLiveRed else DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (connectionState == StreamConnectionState.LIVE) PrimaryLiveRed.copy(alpha = 0.12f) else Color.Transparent,
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
                                    .size(12.dp)
                                    .background(
                                        if (connectionState == StreamConnectionState.LIVE) PrimaryLiveRed else Color(0xFF64748B),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BROADCAST TELEMETRY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp,
                                color = TextPrimary
                            )
                        }
                        StatusBadge(state = connectionState)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Total Stream Duration",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = formattedStreamDuration,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (connectionState == StreamConnectionState.LIVE) PrimaryLiveRed else TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMessage,
                        fontSize = 13.sp,
                        color = if (connectionState == StreamConnectionState.ERROR) PrimaryLiveRed else TextSecondary
                    )
                }
            }
        }

        // Current & Next Video Progress Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Video Track", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        Text(
                            text = if (playlist.isNotEmpty()) "${(currentIndex % Math.max(1, playlist.size)) + 1} / ${playlist.size}" else "0/0",
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentVideo?.title ?: "No video actively broadcasting",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { currentVideoProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = PrimaryLiveRed,
                        trackColor = DarkSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("$formattedElapsed / $formattedTotal", fontSize = 12.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        Text("${(currentVideoProgress * 100).toInt()}%", fontSize = 12.sp, color = TextSecondary)
                    }

                    val nextIdx = (currentIndex + 1) % Math.max(1, playlist.size)
                    val nextVideo = if (playlist.isNotEmpty() && nextIdx in playlist.indices) playlist[nextIdx] else null
                    if (nextVideo != null && playlist.size > 1) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Up next: ", fontSize = 12.sp, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                                Text(nextVideo.title, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Live Encoder & Connection Metrics Grid
        item {
            Text("Hardware Encoder & Network Metrics", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bitrate
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Bitrate", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${metrics.bitrateKbps}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                        Text("kbps", color = TextTertiary, fontSize = 11.sp)
                    }
                }

                // FPS
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Frame Rate", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${metrics.fps}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentEmerald
                        )
                        Text("FPS", color = TextTertiary, fontSize = 11.sp)
                    }
                }

                // Dropped frames
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Dropped", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${metrics.droppedFrames}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (metrics.droppedFrames > 10) StatusAmber else TextPrimary
                        )
                        Text("frames", color = TextTertiary, fontSize = 11.sp)
                    }
                }
            }
        }

        // Live Controls
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Stream Actions", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    val isLiveOrPaused = connectionState == StreamConnectionState.LIVE || connectionState == StreamConnectionState.PAUSED
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (connectionState == StreamConnectionState.PAUSED) {
                            Button(
                                onClick = { viewModel.resumeLive() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume")
                            }
                        } else if (connectionState == StreamConnectionState.LIVE) {
                            OutlinedButton(
                                onClick = { viewModel.pauseLive() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause")
                            }
                        }

                        if (isLiveOrPaused || connectionState == StreamConnectionState.CONNECTING || connectionState == StreamConnectionState.RECONNECTING) {
                            Button(
                                onClick = { viewModel.stopLive() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLiveRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop LIVE")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startLive() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryLiveRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start LIVE")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
