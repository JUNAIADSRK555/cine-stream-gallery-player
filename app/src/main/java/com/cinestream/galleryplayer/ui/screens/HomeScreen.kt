package com.cinestream.galleryplayer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.galleryplayer.model.DrmType
import com.cinestream.galleryplayer.model.StreamConfig
import com.cinestream.galleryplayer.model.StreamFormat
import com.cinestream.galleryplayer.ui.components.CineDropdown
import com.cinestream.galleryplayer.ui.components.CineTextField
import com.cinestream.galleryplayer.ui.components.tvFocusable
import com.cinestream.galleryplayer.ui.theme.*

@Composable
fun HomeScreen(
    onPlayStream: (StreamConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var streamUrl by remember { mutableStateOf("") }
    var streamTitle by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var drmLicenseUrl by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf(StreamFormat.AUTO) }
    var selectedDrmType by remember { mutableStateOf(DrmType.NONE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Stream Player",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Enter any HLS, DASH, MP4 stream with custom headers & DRM",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            // Quick Paste Button
            Button(
                onClick = {
                    val clip = clipboardManager.getText()?.text
                    if (!clip.isNullOrBlank()) {
                        streamUrl = clip.trim()
                        Toast.makeText(context, "URL pasted from clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NavyCard),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.tvFocusable(shape = RoundedCornerShape(8.dp))
            ) {
                Text("Paste URL", color = CyanAccent, fontSize = 12.sp)
            }
        }

        // Stream URL Card
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "STREAM SOURCE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent,
                    letterSpacing = 1.sp
                )

                CineTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = "Stream URL (m3u8, mpd, mp4, etc.) *",
                    placeholder = "https://example.com/live/master.m3u8"
                )

                CineTextField(
                    value = streamTitle,
                    onValueChange = { streamTitle = it },
                    label = "Stream Title (Optional)",
                    placeholder = "My Custom Stream"
                )
            }
        }

        // Format & DRM Selection Row
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "STREAM FORMAT & SECURITY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CineDropdown(
                        items = StreamFormat.entries,
                        selectedItem = selectedFormat,
                        onItemSelected = { selectedFormat = it },
                        label = "Format Override",
                        getItemLabel = { it.displayName },
                        modifier = Modifier.weight(1f)
                    )

                    CineDropdown(
                        items = DrmType.entries,
                        selectedItem = selectedDrmType,
                        onItemSelected = { selectedDrmType = it },
                        label = "DRM Scheme",
                        getItemLabel = { it.displayName },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (selectedDrmType != DrmType.NONE) {
                    CineTextField(
                        value = drmLicenseUrl,
                        onValueChange = { drmLicenseUrl = it },
                        label = "${selectedDrmType.displayName} License Server URL",
                        placeholder = "https://license.example.com/acquire"
                    )
                }
            }
        }

        // HTTP Headers Card
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "CUSTOM HTTP HEADERS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent,
                    letterSpacing = 1.sp
                )

                CineTextField(
                    value = cookie,
                    onValueChange = { cookie = it },
                    label = "Cookie",
                    placeholder = "session_id=abc123xyz; token=eyJ..."
                )

                CineTextField(
                    value = referer,
                    onValueChange = { referer = it },
                    label = "Referer",
                    placeholder = "https://example.com"
                )

                CineTextField(
                    value = origin,
                    onValueChange = { origin = it },
                    label = "Origin",
                    placeholder = "https://example.com"
                )
            }
        }

        // Big Cyan Play Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(CyanAccent, CyanDark)
                    )
                )
                .tvFocusable(
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        if (streamUrl.isNotBlank()) {
                            onPlayStream(
                                StreamConfig(
                                    url = streamUrl.trim(),
                                    title = streamTitle.ifBlank { "Custom Stream" },
                                    cookie = cookie,
                                    referer = referer,
                                    origin = origin,
                                    drmLicenseUrl = drmLicenseUrl,
                                    drmType = selectedDrmType,
                                    format = selectedFormat
                                )
                            )
                        } else {
                            Toast.makeText(context, "Please enter a stream URL", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                .clickable {
                    if (streamUrl.isNotBlank()) {
                        onPlayStream(
                            StreamConfig(
                                url = streamUrl.trim(),
                                title = streamTitle.ifBlank { "Custom Stream" },
                                cookie = cookie,
                                referer = referer,
                                origin = origin,
                                drmLicenseUrl = drmLicenseUrl,
                                drmType = selectedDrmType,
                                format = selectedFormat
                            )
                        )
                    } else {
                        Toast.makeText(context, "Please enter a stream URL", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = NavyBackground,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PLAY STREAM",
                    color = NavyBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Quick Presets for Instant Testing
        Text(
            text = "QUICK TEST PRESETS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetChip(
                name = "Big Buck Bunny (MP4)",
                modifier = Modifier.weight(1f),
                onClick = {
                    streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                    streamTitle = "Big Buck Bunny"
                    selectedFormat = StreamFormat.PROGRESSIVE
                    selectedDrmType = DrmType.NONE
                }
            )
            PresetChip(
                name = "Sintel (HLS)",
                modifier = Modifier.weight(1f),
                onClick = {
                    streamUrl = "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
                    streamTitle = "Sintel HLS"
                    selectedFormat = StreamFormat.HLS
                    selectedDrmType = DrmType.NONE
                }
            )
            PresetChip(
                name = "Tears of Steel (DASH)",
                modifier = Modifier.weight(1f),
                onClick = {
                    streamUrl = "https://dash.akamaized.net/akamai/test/caption_test/ElephantsDream/elephants_dream_480p_heaac5_1.mpd"
                    streamTitle = "Elephants Dream DASH"
                    selectedFormat = StreamFormat.DASH
                    selectedDrmType = DrmType.NONE
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PresetChip(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NavyCard)
            .tvFocusable(shape = RoundedCornerShape(8.dp), onClick = onClick)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = CyanAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
