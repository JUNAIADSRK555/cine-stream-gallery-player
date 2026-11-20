package com.cinestream.galleryplayer.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.cinestream.galleryplayer.model.M3uChannel
import com.cinestream.galleryplayer.model.StreamConfig
import com.cinestream.galleryplayer.model.StreamFormat
import com.cinestream.galleryplayer.ui.components.CineTextField
import com.cinestream.galleryplayer.ui.components.tvFocusable
import com.cinestream.galleryplayer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun PlaylistScreen(
    onPlayChannel: (StreamConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var channels by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Filter channels based on search query and selected group
    val filteredChannels = remember(channels, searchQuery, selectedGroup) {
        channels.filter { channel ->
            val matchesGroup = selectedGroup == "All" || channel.group.equals(selectedGroup, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    channel.name.contains(searchQuery, ignoreCase = true) ||
                    channel.group.contains(searchQuery, ignoreCase = true)
            matchesGroup && matchesSearch
        }
    }

    // Extract unique groups
    val groups = remember(channels) {
        if (channels.isEmpty()) emptyList()
        else listOf("All") + channels.map { it.group }.distinct().sorted()
    }

    // Local file picker for .m3u / .m3u8 files
    val m3uFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoading = true
                showAddDialog = false
                val parsed = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            parseM3u(stream.bufferedReader())
                        } ?: emptyList()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emptyList()
                    }
                }
                channels = parsed
                isLoading = false
                Toast.makeText(context, "Loaded ${parsed.size} channels from file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "IPTV & M3U Playlist",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (channels.isEmpty()) "No pre-loaded channels. Add an M3U playlist to begin."
                    else "${channels.size} channels available (${filteredChannels.size} filtered)",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (channels.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            channels = emptyList()
                            searchQuery = ""
                            selectedGroup = "All"
                            Toast.makeText(context, "Playlist cleared", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Playlist",
                            tint = TextSecondary
                        )
                    }
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.tvFocusable(shape = RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = NavyBackground,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Add M3U", color = NavyBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CyanAccent)
            }
        } else if (channels.isEmpty()) {
            // Empty State (User requirement: empty state with + Add M3U and search, NO pre-loaded channels)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(36.dp))
                                .background(NavyCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = "Empty Playlist",
                                tint = CyanAccent,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Text(
                            text = "No Channels in Playlist",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Text(
                            text = "Your playlist is currently empty. Click '+ Add M3U' to load your IPTV streams from an M3U/M3U8 URL or local file.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .tvFocusable(shape = RoundedCornerShape(10.dp))
                                .padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add M3U",
                                tint = NavyBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "+ Add M3U Playlist",
                                color = NavyBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Search Bar & Filter Chips
            CineTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Search Channels or Categories",
                placeholder = "Type channel name or group...",
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = CyanAccent
                    )
                }
            )

            // Category Chips Row
            if (groups.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(groups) { group ->
                        val isSelected = group == selectedGroup
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CyanAccent else NavyCard)
                                .tvFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = { selectedGroup = group }
                                )
                                .clickable { selectedGroup = group }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = group,
                                color = if (isSelected) NavyBackground else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Channel Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
                    ChannelCardItem(
                        channel = channel,
                        onClick = {
                            onPlayChannel(
                                StreamConfig(
                                    url = channel.streamUrl,
                                    title = channel.name,
                                    format = StreamFormat.AUTO,
                                    extraHeaders = channel.headers
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    // Add M3U Dialog
    if (showAddDialog) {
        AddM3uDialog(
            onDismiss = { showAddDialog = false },
            onLoadUrl = { url ->
                showAddDialog = false
                isLoading = true
                coroutineScope.launch {
                    val parsed = withContext(Dispatchers.IO) {
                        fetchAndParseM3u(url)
                    }
                    channels = parsed
                    isLoading = false
                    if (parsed.isNotEmpty()) {
                        Toast.makeText(context, "Loaded ${parsed.size} channels successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to load channels or playlist is empty", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onPickFile = {
                showAddDialog = false
                m3uFilePicker.launch(arrayOf("*/*"))
            }
        )
    }
}

@Composable
private fun ChannelCardItem(
    channel: M3uChannel,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .tvFocusable(shape = RoundedCornerShape(10.dp), onClick = onClick)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Channel Logo / Avatar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NavyCard),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                } else {
                    Text(
                        text = channel.name.take(3).uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }
            }

            // Channel Title & Group
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = channel.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.group,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AddM3uDialog(
    onDismiss: () -> Unit,
    onLoadUrl: (String) -> Unit,
    onPickFile: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var playlistUrl by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add M3U Playlist",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "Enter a remote M3U URL or select a local playlist file from your device / USB.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                CineTextField(
                    value = playlistUrl,
                    onValueChange = { playlistUrl = it },
                    label = "M3U / M3U8 Playlist URL",
                    placeholder = "https://example.com/playlist.m3u"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                playlistUrl = clip.trim()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.tvFocusable(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text("Paste URL", color = CyanAccent, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onPickFile,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.tvFocusable(shape = RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "File",
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick File", color = CyanAccent, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (playlistUrl.isNotBlank()) {
                                onLoadUrl(playlistUrl.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.tvFocusable(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text("Load Playlist", color = NavyBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Downloads and parses M3U playlist from HTTP/HTTPS URL.
 */
private fun fetchAndParseM3u(urlString: String): List<M3uChannel> {
    return try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "CineStreamPlayer/1.0")
        connection.connect()

        if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { reader ->
                parseM3u(reader)
            }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

/**
 * Parses M3U reader into M3uChannel items.
 */
private fun parseM3u(reader: BufferedReader): List<M3uChannel> {
    val channels = mutableListOf<M3uChannel>()
    var currentName: String? = null
    var currentLogo: String? = null
    var currentGroup = "General"

    val tvgNameRegex = Regex("""tvg-name="([^"]*)"""")
    val tvgLogoRegex = Regex("""tvg-logo="([^"]*)"""")
    val groupRegex = Regex("""group-title="([^"]*)"""")

    reader.forEachLine { rawLine ->
        val line = rawLine.trim()
        if (line.startsWith("#EXTINF:", ignoreCase = true)) {
            val logoMatch = tvgLogoRegex.find(line)
            currentLogo = logoMatch?.groupValues?.getOrNull(1)

            val groupMatch = groupRegex.find(line)
            currentGroup = groupMatch?.groupValues?.getOrNull(1)?.ifBlank { "General" } ?: "General"

            // Extract channel title after last comma
            val commaIdx = line.lastIndexOf(',')
            currentName = if (commaIdx != -1 && commaIdx < line.length - 1) {
                line.substring(commaIdx + 1).trim()
            } else {
                val nameMatch = tvgNameRegex.find(line)
                nameMatch?.groupValues?.getOrNull(1)?.ifBlank { "Channel" } ?: "Channel"
            }
        } else if (!line.startsWith("#") && line.isNotBlank()) {
            // Stream URL
            val name = currentName ?: "Channel ${channels.size + 1}"
            channels.add(
                M3uChannel(
                    name = name,
                    streamUrl = line,
                    logoUrl = currentLogo,
                    group = currentGroup
                )
            )
            currentName = null
            currentLogo = null
            currentGroup = "General"
        }
    }

    return channels
}
