package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlaylistScreen
import com.example.ui.screens.PermissionSetupScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.LocalStreamLiveTheme
import com.example.ui.theme.PrimaryLiveRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MainViewModel

enum class AppNavScreen {
    HOME,
    PLAYLIST,
    YOUTUBE_SETUP,
    DASHBOARD,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LocalStreamLiveTheme {
                var setupComplete by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(viewModel.isPermissionSetupComplete())
                }

                if (!setupComplete) {
                    PermissionSetupScreen(
                        viewModel = viewModel,
                        onComplete = { setupComplete = true }
                    )
                } else {
                    MainAppContainer(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: MainViewModel) {
    var currentScreen by rememberSaveable { mutableStateOf(AppNavScreen.HOME) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            AppNavScreen.HOME -> "LocalStream Live"
                            AppNavScreen.PLAYLIST -> "Playlist Queue"
                            AppNavScreen.YOUTUBE_SETUP -> "YouTube Live Setup"
                            AppNavScreen.DASHBOARD -> "Live Broadcast Studio"
                            AppNavScreen.SETTINGS -> "Settings & Health"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = DarkSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.HOME,
                    onClick = { currentScreen = AppNavScreen.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_home"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryLiveRed,
                        selectedTextColor = PrimaryLiveRed,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = PrimaryLiveRed.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.PLAYLIST,
                    onClick = { currentScreen = AppNavScreen.PLAYLIST },
                    icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = "Playlist") },
                    label = { Text("Playlist", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_playlist"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan,
                        selectedTextColor = AccentCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = AccentCyan.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.YOUTUBE_SETUP,
                    onClick = { currentScreen = AppNavScreen.YOUTUBE_SETUP },
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = "YouTube") },
                    label = { Text("YouTube", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_youtube"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryLiveRed,
                        selectedTextColor = PrimaryLiveRed,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = PrimaryLiveRed.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.DASHBOARD,
                    onClick = { currentScreen = AppNavScreen.DASHBOARD },
                    icon = { Icon(Icons.Default.Sensors, contentDescription = "Dashboard") },
                    label = { Text("Telemetry", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_dashboard"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryLiveRed,
                        selectedTextColor = PrimaryLiveRed,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = PrimaryLiveRed.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == AppNavScreen.SETTINGS,
                    onClick = { currentScreen = AppNavScreen.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_settings"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan,
                        selectedTextColor = AccentCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = AccentCyan.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppNavScreen.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToPlaylist = { currentScreen = AppNavScreen.PLAYLIST },
                    onNavigateToSetup = { currentScreen = AppNavScreen.YOUTUBE_SETUP },
                    onNavigateToDashboard = { currentScreen = AppNavScreen.DASHBOARD },
                    onNavigateToSettings = { currentScreen = AppNavScreen.SETTINGS }
                )
                AppNavScreen.PLAYLIST -> PlaylistScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = AppNavScreen.HOME }
                )
                AppNavScreen.YOUTUBE_SETUP -> SetupScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = AppNavScreen.HOME }
                )
                AppNavScreen.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = AppNavScreen.HOME }
                )
                AppNavScreen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = AppNavScreen.HOME }
                )
            }
        }
    }
}
