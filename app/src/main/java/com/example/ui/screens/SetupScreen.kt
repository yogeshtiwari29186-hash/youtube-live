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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val savedRtmpUrl by viewModel.rtmpUrl.collectAsState()
    val savedStreamKey by viewModel.streamKey.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()
    val testResult by viewModel.testConnectionResult.collectAsState()

    var urlInput by remember(savedRtmpUrl) { mutableStateOf(savedRtmpUrl) }
    var keyInput by remember(savedStreamKey) { mutableStateOf(savedStreamKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Security Guarantee Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(AccentEmerald.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = AccentEmerald)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Hardware-Encrypted Credentials", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        Text("Stored locally in Android Keystore. Never sent to any intermediary server.", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }

        // Configuration Form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "YouTube Live Ingest",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "From YouTube Studio > Create > Go live > Stream Settings",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // RTMP Server URL Field
                    Text("RTMP Server URL", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rtmp_url_input"),
                        placeholder = { Text("rtmp://a.rtmp.youtube.com/live2", color = TextSecondary) },
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null, tint = AccentCyan)
                        },
                        trailingIcon = {
                            if (urlInput.isNotBlank()) {
                                IconButton(onClick = { urlInput = "rtmp://a.rtmp.youtube.com/live2" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Reset default", tint = TextSecondary)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // YouTube Stream Key Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("YouTube Stream Key", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                        Text(
                            text = if (keyInput.isNotBlank()) "Masked (${keyInput.length} chars)" else "Empty",
                            fontSize = 11.sp,
                            color = if (keyInput.isNotBlank()) AccentEmerald else StatusAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stream_key_input"),
                        placeholder = { Text("Paste YouTube Stream Key (e.g. xxxx-xxxx-xxxx-xxxx)", color = TextSecondary) },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = PrimaryLiveRed)
                        },
                        trailingIcon = {
                            Row {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrBlank() && keyInput.isBlank()) {
                                    IconButton(onClick = { keyInput = clipText.trim() }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = AccentCyan)
                                    }
                                }
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isKeyVisible) "Hide" else "Show",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryLiveRed,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Save and Clear Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveConnection(urlInput, keyInput)
                                saveMessage = "Connection parameters saved securely."
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_connection_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Connection", fontWeight = FontWeight.Bold)
                        }

                        if (savedStreamKey.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearStreamKey()
                                    keyInput = ""
                                    saveMessage = "Stream key deleted."
                                },
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryLiveRed)
                            ) {
                                Text("Delete Key")
                            }
                        }
                    }

                    saveMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(msg, color = AccentEmerald, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(DarkCardBorder)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Test Connection Section
                    Text("Connection Verification", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Performs a quick RTMP handshake and socket verification to YouTube's live endpoint without broadcasting video frames.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { viewModel.testConnection() },
                        enabled = !isTesting && (keyInput.isNotBlank() || savedStreamKey.isNotBlank()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_connection_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing YouTube Handshake...", color = AccentCyan)
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test YouTube Connection", color = TextPrimary)
                        }
                    }

                    testResult?.let { res ->
                        Spacer(modifier = Modifier.height(12.dp))
                        val isSuccess = res.startsWith("Success")
                        val resultBg = if (isSuccess) AccentEmerald.copy(alpha = 0.15f) else StatusAmber.copy(alpha = 0.15f)
                        val resultBorder = if (isSuccess) AccentEmerald else StatusAmber

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(resultBg, RoundedCornerShape(10.dp))
                                .border(1.dp, resultBorder.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = res,
                                color = if (isSuccess) AccentEmerald else StatusAmber,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
