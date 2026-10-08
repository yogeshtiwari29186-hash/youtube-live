package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel

private fun notificationGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun batteryUnrestricted(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

@Composable
fun PermissionSetupScreen(viewModel: MainViewModel, onComplete: () -> Unit) {
    val context = LocalContext.current
    var notificationOk by remember { mutableStateOf(notificationGranted(context)) }
    var batteryOk by remember { mutableStateOf(batteryUnrestricted(context)) }
    var busy by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    fun refresh() {
        notificationOk = notificationGranted(context)
        batteryOk = batteryUnrestricted(context)
    }

    // Battery optimization and OEM auto-start are device-specific recommendations, not blockers.
    // Android must allow the user to continue even when those settings are unavailable.
    val allRequired = notificationOk
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        busy = false
        refresh()
        if (Build.VERSION.SDK_INT >= 33 &&
            result[Manifest.permission.POST_NOTIFICATIONS] == false &&
            context is android.app.Activity &&
            !ActivityCompat.shouldShowRequestPermissionRationale(context, Manifest.permission.POST_NOTIFICATIONS)
        ) permanentlyDenied = true
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        Modifier.fillMaxSize().background(DarkBackground).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Set up LocalStream Live", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Allow the required access once. Battery and OEM background controls can be configured later.",
            color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)

        PermissionRow(Icons.Default.Notifications, "Notifications",
            "Required for the persistent LIVE streaming notification.", notificationOk)
        PermissionRow(Icons.Default.PhotoLibrary, "Photos & Videos / Local Media",
            "Uses Android's system picker. No broad storage permission is requested.", true)
        PermissionRow(Icons.Default.PlayCircle, "Foreground Service / Background Streaming",
            "Declared as a mediaPlayback foreground service so streaming can continue outside the Activity.", true)
        PermissionRow(Icons.Default.BatteryAlert, "Battery Optimization",
            if (batteryOk) "Unrestricted background activity is enabled."
            else "Allow unrestricted background activity for reliable long-running streams.", batteryOk)

        Card(colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Settings, null, tint = AccentCyan)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("OEM Auto-start / Background Activity (Optional)", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Android has no universal auto-start runtime permission. If your device exposes OEM controls, use App System Settings.",
                        color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:" + context.packageName)
                        })
                    }) { Text("Open App System Settings") }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (permanentlyDenied) {
            Text("Notifications are permanently denied. Enable them in Android Settings.",
                color = StatusAmber, fontSize = 12.sp)
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                })
            }, modifier = Modifier.fillMaxWidth()) { Text("Open Settings") }
        }

        Button(
            onClick = {
                if (allRequired) {
                    viewModel.markPermissionSetupComplete()
                    onComplete()
                } else {
                    busy = true
                    val permissions = buildList {
                        if (Build.VERSION.SDK_INT >= 33 && !notificationOk)
                            add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (permissions.isNotEmpty()) launcher.launch(permissions.toTypedArray())
                    else busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allRequired) AccentEmerald else PrimaryLiveRed),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
            else Text(if (allRequired) "CONTINUE" else "ALLOW REQUIRED ACCESS",
                fontWeight = FontWeight.Bold)
        }

        if (!batteryOk) {
            OutlinedButton(onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:" + context.packageName)
                    })
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Allow Unrestricted Battery Activity (Optional)") }
        }

        Text("Local videos stay on this phone. The app does not upload them to cloud storage.",
            color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, detail: String, granted: Boolean
) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (granted) AccentEmerald.copy(alpha = .45f) else DarkCardBorder)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (granted) AccentEmerald else TextSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(detail, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.Warning, null,
                tint = if (granted) AccentEmerald else StatusAmber)
        }
    }
}
