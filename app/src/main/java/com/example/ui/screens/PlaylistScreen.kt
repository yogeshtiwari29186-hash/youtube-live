package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoItem
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryLiveRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.MainViewModel

@Composable
fun PlaylistScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val playlist by viewModel.playlist.collectAsState()
    val isLoopEnabled by viewModel.isLoopEnabled.collectAsState()
    val currentStreamingVideo by viewModel.currentStreamingVideo.collectAsState()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addVideos(uris)
        }
    }

    val totalDurationMs = playlist.sumOf { it.durationMs }
    val totalSec = totalDurationMs / 1000
    val totalHours = totalSec / 3600
    val totalMins = (totalSec % 3600) / 60
    val formattedTotalDuration = if (totalHours > 0) {
        "${totalHours}h ${totalMins}m"
    } else {
        "${totalMins}m ${totalSec % 60}s"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Summary Card
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
                        Column {
                            Text("Playlist Management", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                            Text("${playlist.size} videos • Total duration: $formattedTotalDuration", fontSize = 13.sp, color = TextSecondary)
                        }

                        IconButton(
                            onClick = { videoPickerLauncher.launch(arrayOf("video/*")) },
                            modifier = Modifier
                                .background(AccentCyan.copy(alpha = 0.15f), CircleShape)
                                .size(40.dp)
                                .testTag("add_video_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Videos", tint = AccentCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Loop Continuous Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Loop, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Continuous Loop", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                Text("Replay from video #1 after last finishes", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        Switch(
                            checked = isLoopEnabled,
                            onCheckedChange = { viewModel.setLoopEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentEmerald,
                                checkedTrackColor = AccentEmerald.copy(alpha = 0.3f)
                            )
                        )
                    }

                    if (playlist.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.clearPlaylist() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryLiveRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlaylistRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear All", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (playlist.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(DarkSurfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Movie,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No Videos in Playlist", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Select video files (MP4, MKV, MOV, WebM) stored locally on your device to create your broadcast queue.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { videoPickerLauncher.launch(arrayOf("video/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryLiveRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Local Videos")
                        }
                    }
                }
            }
        } else {
            itemsIndexed(playlist, key = { _, item -> item.id }) { index, video ->
                val isCurrentlyPlaying = currentStreamingVideo?.id == video.id
                PlaylistItemCard(
                    index = index,
                    video = video,
                    isCurrentlyPlaying = isCurrentlyPlaying,
                    canMoveUp = index > 0,
                    canMoveDown = index < playlist.size - 1,
                    onMoveUp = { viewModel.moveVideo(index, index - 1) },
                    onMoveDown = { viewModel.moveVideo(index, index + 1) },
                    onDelete = { viewModel.removeVideo(index) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlaylistItemCard(
    index: Int,
    video: VideoItem,
    isCurrentlyPlaying: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("playlist_item_$index"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying) DarkSurfaceVariant else DarkSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrentlyPlaying) PrimaryLiveRed else DarkCardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index & indicator
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isCurrentlyPlaying) PrimaryLiveRed else DarkSurfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentlyPlaying) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Playing",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.formattedDuration(),
                        fontSize = 12.sp,
                        color = if (isCurrentlyPlaying) PrimaryLiveRed else AccentCyan,
                        fontWeight = FontWeight.Medium
                    )
                    if (video.formattedSize().isNotBlank()) {
                        Text(" • ", fontSize = 12.sp, color = TextTertiary)
                        Text(video.formattedSize(), fontSize = 12.sp, color = TextSecondary)
                    }
                    if (video.width > 0 && video.height > 0) {
                        Text(" • ", fontSize = 12.sp, color = TextTertiary)
                        Text("${video.width}x${video.height}", fontSize = 12.sp, color = TextTertiary)
                    }
                }
            }

            // Reorder & Delete controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Move Up",
                        tint = if (canMoveUp) TextSecondary else DarkCardBorder,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Move Down",
                        tint = if (canMoveDown) TextSecondary else DarkCardBorder,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = PrimaryLiveRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
