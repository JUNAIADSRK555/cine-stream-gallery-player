package com.cinestream.galleryplayer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cinestream.galleryplayer.model.DrmType
import com.cinestream.galleryplayer.model.StreamConfig
import com.cinestream.galleryplayer.model.StreamFormat
import com.cinestream.galleryplayer.ui.components.CineDropdownSelector
import com.cinestream.galleryplayer.ui.components.CineInputField
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
    var cookie by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var drmLicenseUrl by remember { mutableStateOf("") }

    var selectedFormat by remember { mutableStateOf(StreamFormat.AUTO) }
    var selectedDrmType by remember { mutableStateOf(DrmType.CLEARKEY) }
    var showSamplesDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section Title: "Cine Network Stream" + [📋 Paste] + [★ Favorites]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cine Network Stream",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Clipboard Paste Icon
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste Clipboard",
                        tint = CyanAccent,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    streamUrl = clip.trim()
                                    Toast.makeText(context, "URL pasted from clipboard", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                                }
                            }
                    )

                    // Star Icon (Opens Sample Streams Modal)
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Samples / Favorites",
                        tint = CyanAccent,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { showSamplesDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 1. Stream Url
            CineInputField(
                value = streamUrl,
                onValueChange = { streamUrl = it },
                placeholder = "Stream Url"
            )

            // 2. Cookie
            CineInputField(
                value = cookie,
                onValueChange = { cookie = it },
                placeholder = "Cookie"
            )

            // 3. Referer
            CineInputField(
                value = referer,
                onValueChange = { referer = it },
                placeholder = "Referer"
            )

            // 4. Origin
            CineInputField(
                value = origin,
                onValueChange = { origin = it },
                placeholder = "Origin"
            )

            // 5. Drm License
            CineInputField(
                value = drmLicenseUrl,
                onValueChange = { drmLicenseUrl = it },
                placeholder = "Drm License"
            )

            // Side-by-side Dropdown Selectors matching screenshot: [ Default ▼ ] [ ClearKey ▼ ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Format Selector: Default, HLS, DASH, SmoothStreaming, Progressive
                CineDropdownSelector(
                    items = StreamFormat.entries,
                    selectedItem = selectedFormat,
                    onItemSelected = { selectedFormat = it },
                    getItemLabel = { if (it == StreamFormat.AUTO) "Default" else it.displayName.substringBefore(" ") },
                    modifier = Modifier.weight(1f)
                )

                // DRM Selector: None, ClearKey, Widevine, PlayReady
                CineDropdownSelector(
                    items = listOf(DrmType.CLEARKEY, DrmType.WIDEVINE, DrmType.PLAYREADY, DrmType.NONE),
                    selectedItem = selectedDrmType,
                    onItemSelected = { selectedDrmType = it },
                    getItemLabel = { if (it == DrmType.NONE) "None" else it.displayName },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(80.dp)) // Padding for FAB
        }

        // Floating Action Button (FAB) matching the screenshot:
        // Large Bright Cyan circle with white play arrow in bottom-right corner!
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(CyanAccent)
                .tvFocusable(shape = CircleShape)
                .clickable {
                    if (streamUrl.isNotBlank()) {
                        onPlayStream(
                            StreamConfig(
                                url = streamUrl.trim(),
                                title = "Cine Stream",
                                cookie = cookie,
                                referer = referer,
                                origin = origin,
                                drmLicenseUrl = drmLicenseUrl,
                                drmType = selectedDrmType,
                                format = selectedFormat
                            )
                        )
                    } else {
                        Toast.makeText(context, "Please enter a Stream Url", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Stream",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }
    }

    // Samples / Favorites Dialog when clicking ★ icon
    if (showSamplesDialog) {
        SamplesDialog(
            onDismiss = { showSamplesDialog = false },
            onSelectSample = { sampleUrl, format, drm, drmUrl, title ->
                streamUrl = sampleUrl
                selectedFormat = format
                selectedDrmType = drm
                drmLicenseUrl = drmUrl
                showSamplesDialog = false
                Toast.makeText(context, "Loaded $title", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun SamplesDialog(
    onDismiss: () -> Unit,
    onSelectSample: (String, StreamFormat, DrmType, String, String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sample Streams",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                val samples = listOf(
                    Triple("Clear DASH (Big Buck Bunny)", "https://dash.akamaized.net/dash264/TestCases/1c/qualcomm/1/MultiRate.mpd", StreamFormat.DASH),
                    Triple("HLS Stream (Sintel)", "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8", StreamFormat.HLS),
                    Triple("Progressive MP4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", StreamFormat.PROGRESSIVE),
                    Triple("Tears of Steel (DASH)", "https://dash.akamaized.net/akamai/test/caption_test/ElephantsDream/elephants_dream_480p_heaac5_1.mpd", StreamFormat.DASH)
                )

                samples.forEach { (name, url, format) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NavyCard)
                            .tvFocusable(shape = RoundedCornerShape(10.dp))
                            .clickable {
                                onSelectSample(url, format, DrmType.NONE, "", name)
                            }
                            .padding(14.dp)
                    ) {
                        Text(text = name, color = CyanAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = TextSecondary)
                    }
                }
            }
        }
    }
}
