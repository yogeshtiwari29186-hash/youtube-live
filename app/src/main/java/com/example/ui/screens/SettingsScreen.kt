package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryLiveRed
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isIgnoringBattery by viewModel.isIgnoringBatteryOptimizations.collectAsState()
    val isLoopEnabled by viewModel.isLoopEnabled.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Battery Optimization Guidance
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isIgnoringBattery) AccentEmerald.copy(alpha = 0.5f) else StatusAmber)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isIgnoringBattery) AccentEmerald.copy(alpha = 0.15f) else StatusAmber.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isIgnoringBattery) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = if (isIgnoringBattery) AccentEmerald else StatusAmber
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Battery Optimization", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                                Text(
                                    text = if (isIgnoringBattery) "Unrestricted (Optimized for 24/7)" else "System may throttle in background",
                                    fontSize = 12.sp,
                                    color = if (isIgnoringBattery) AccentEmerald else StatusAmber
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Android battery saver can freeze apps when the screen is locked. Whitelisting LocalStream Live allows the RTMP foreground service to encode uninterrupted.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp).testTag("battery_settings_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isIgnoringBattery) DarkSurfaceVariant else StatusAmber),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isIgnoringBattery) "Manage Optimization" else "Disable Optimization", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.refreshBatteryOptimizationStatus() },
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Background Execution & OEM Guidance
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Background Execution Policy", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• The stream runs as an Android Foreground Service with Media Playback type.\n" +
                                "• A CPU Partial WakeLock is active while broadcasting.\n" +
                                "• You can switch apps, return home, or lock your screen.\n" +
                                "• For Samsung/Xiaomi/Huawei devices, ensure 'Auto-start' is permitted in device system settings.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Open App System Settings", fontSize = 12.sp)
                    }
                }
            }
        }

        // Streaming Profile & Auto-Loop
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideoSettings, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Stream Quality & Behavior", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Continuous Playlist Loop", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                            Text("Keep live stream running forever by looping", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = isLoopEnabled,
                            onCheckedChange = { viewModel.setLoopEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentEmerald, checkedTrackColor = AccentEmerald.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DarkCardBorder))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Encoding pipeline specs
                    Text("Encoder Specs", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Video: H.264 / AVC Pass-through (SPS/PPS Sequence Injected)", fontSize = 12.sp, color = TextSecondary)
                    Text("• Audio: AAC-LC Stereo 44.1kHz / 48kHz", fontSize = 12.sp, color = TextSecondary)
                    Text("• Container: FLV over RTMP / RTMPS Chunk Stream", fontSize = 12.sp, color = TextSecondary)
                    Text("• Reconnect: Exponential backoff with Network State Monitor", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }

        // Critical Limitation Disclosure
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Critical System Limitations", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = StatusAmber)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1. Phone must stay powered on and connected to internet.\n" +
                                "2. Local video files must remain untouched in phone storage.\n" +
                                "3. If Android OS force-stops the app due to extreme memory pressure or battery depletion, streaming will stop.\n" +
                                "4. This is authentic local phone hardware streaming, NOT a third-party cloud server.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
