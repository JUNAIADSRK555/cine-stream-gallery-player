package com.cinestream.galleryplayer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.galleryplayer.model.StreamConfig
import com.cinestream.galleryplayer.ui.components.AppTab
import com.cinestream.galleryplayer.ui.components.CineBottomBar
import com.cinestream.galleryplayer.ui.components.CineTopBar
import com.cinestream.galleryplayer.ui.components.tvFocusable
import com.cinestream.galleryplayer.ui.player.PlayerScreen
import com.cinestream.galleryplayer.ui.screens.*
import com.cinestream.galleryplayer.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CineStreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NavyBackground
                ) {
                    CinePlayerApp()
                }
            }
        }
    }
}

@Composable
fun CinePlayerApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var showSamplesScreen by remember { mutableStateOf(false) }
    var currentStreamConfig by remember { mutableStateOf<StreamConfig?>(null) }

    // Hardware back handler when in Samples view
    BackHandler(enabled = showSamplesScreen && currentStreamConfig == null) {
        showSamplesScreen = false
    }

    if (currentStreamConfig != null) {
        // Fullscreen ExoPlayer Screen
        PlayerScreen(
            streamConfig = currentStreamConfig!!,
            onBack = { currentStreamConfig = null },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Modal Navigation Drawer for hamburger menu
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = NavySurface,
                    drawerContentColor = TextPrimary,
                    modifier = Modifier.width(300.dp)
                ) {
                    // Drawer Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyCard)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Cine ",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Player",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                        }
                        Text(
                            text = "Next-Gen Media & Stream Player",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Drawer Items
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Podcasts, contentDescription = null, tint = CyanAccent) },
                        label = { Text("Cine Network Stream", color = TextPrimary) },
                        selected = currentTab == AppTab.HOME && !showSamplesScreen,
                        onClick = {
                            currentTab = AppTab.HOME
                            showSamplesScreen = false
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.SmartDisplay, contentDescription = null, tint = CyanAccent) },
                        label = { Text("Local & USB Videos", color = TextPrimary) },
                        selected = currentTab == AppTab.LOCAL && !showSamplesScreen,
                        onClick = {
                            currentTab = AppTab.LOCAL
                            showSamplesScreen = false
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = CyanAccent) },
                        label = { Text("Playlists (M3U / IPTV)", color = TextPrimary) },
                        selected = currentTab == AppTab.PLAYLIST && !showSamplesScreen,
                        onClick = {
                            currentTab = AppTab.PLAYLIST
                            showSamplesScreen = false
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = GoldText) },
                        label = { Text("ExoPlayer Samples (Demo)", color = GoldText) },
                        selected = showSamplesScreen,
                        onClick = {
                            showSamplesScreen = true
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = CyanAccent) },
                        label = { Text("Settings", color = TextPrimary) },
                        selected = currentTab == AppTab.SETTINGS && !showSamplesScreen,
                        onClick = {
                            currentTab = AppTab.SETTINGS
                            showSamplesScreen = false
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    CineTopBar(
                        onMenuClick = {
                            coroutineScope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        },
                        onShareClick = {
                            Toast.makeText(context, "Cine Player v1.0.0", Toast.LENGTH_SHORT).show()
                        },
                        onAdsFreeClick = {
                            Toast.makeText(context, "Ads Free Pro Active!", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                bottomBar = {
                    CineBottomBar(
                        currentTab = currentTab,
                        onTabSelected = { tab ->
                            currentTab = tab
                            showSamplesScreen = false
                        }
                    )
                },
                containerColor = NavyBackground
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showSamplesScreen) {
                        SamplesScreen(
                            onPlaySample = { config ->
                                currentStreamConfig = config
                            }
                        )
                    } else {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "tab_switch_animation"
                        ) { tab ->
                            when (tab) {
                                AppTab.HOME -> {
                                    HomeScreen(
                                        onPlayStream = { config ->
                                            currentStreamConfig = config
                                        }
                                    )
                                }
                                AppTab.LOCAL -> {
                                    LocalScreen(
                                        onPlayVideo = { config ->
                                            currentStreamConfig = config
                                        }
                                    )
                                }
                                AppTab.PLAYLIST -> {
                                    PlaylistScreen(
                                        onPlayChannel = { config ->
                                            currentStreamConfig = config
                                        }
                                    )
                                }
                                AppTab.SETTINGS -> {
                                    SettingsScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
