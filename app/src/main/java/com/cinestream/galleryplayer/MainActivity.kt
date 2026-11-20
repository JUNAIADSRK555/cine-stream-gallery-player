package com.cinestream.galleryplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.galleryplayer.model.StreamConfig
import com.cinestream.galleryplayer.ui.components.tvFocusable
import com.cinestream.galleryplayer.ui.player.PlayerScreen
import com.cinestream.galleryplayer.ui.screens.HomeScreen
import com.cinestream.galleryplayer.ui.screens.LocalScreen
import com.cinestream.galleryplayer.ui.screens.PlaylistScreen
import com.cinestream.galleryplayer.ui.theme.*

enum class CineTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LOCAL("Local", Icons.Default.VideoLibrary),
    PLAYLIST("Playlist", Icons.Default.PlaylistPlay)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CineStreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NavyBackground
                ) {
                    CineStreamApp()
                }
            }
        }
    }
}

@Composable
fun CineStreamApp() {
    var selectedTab by remember { mutableStateOf(CineTab.HOME) }
    var currentStreamConfig by remember { mutableStateOf<StreamConfig?>(null) }

    // If a stream is active, show the Fullscreen Player
    if (currentStreamConfig != null) {
        PlayerScreen(
            streamConfig = currentStreamConfig!!,
            onBack = { currentStreamConfig = null },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Main App Layout with 3 Tabs Only
        Scaffold(
            topBar = {
                CineTopBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            },
            containerColor = NavyBackground
        ) { paddingValues ->
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                label = "tab_content_animation"
            ) { tab ->
                when (tab) {
                    CineTab.HOME -> {
                        HomeScreen(
                            onPlayStream = { config ->
                                currentStreamConfig = config
                            }
                        )
                    }
                    CineTab.LOCAL -> {
                        LocalScreen(
                            onPlayVideo = { config ->
                                currentStreamConfig = config
                            }
                        )
                    }
                    CineTab.PLAYLIST -> {
                        PlaylistScreen(
                            onPlayChannel = { config ->
                                currentStreamConfig = config
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CineTopBar(
    selectedTab: CineTab,
    onTabSelected: (CineTab) -> Unit
) {
    Surface(
        color = NavySurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Logo & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyanAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "CineStream Logo",
                        tint = NavyBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row {
                    Text(
                        text = "CINE",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "STREAM",
                        color = CyanAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Exactly 3 Tabs: Home, Local, Playlist
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CineTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) NavyCardElevated else NavySurface)
                            .tvFocusable(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = CyanAccent,
                                onClick = { onTabSelected(tab) }
                            )
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) CyanAccent else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = tab.title,
                                color = if (isSelected) CyanAccent else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
