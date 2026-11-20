package com.cinestream.galleryplayer.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.cinestream.galleryplayer.model.M3uChannel
import com.cinestream.galleryplayer.model.StreamConfig
import com.cinestream.galleryplayer.model.StreamFormat
import com.cinestream.galleryplayer.ui.components.CineInputField
import com.cinestream.galleryplayer.ui.components.tvFocusable
import com.cinestream.galleryplayer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
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

    val filteredChannels = remember(channels, searchQuery, selectedGroup) {
        channels.filter { channel ->
            val matchesGroup = selectedGroup == "All" || channel.group.equals(selectedGroup, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    channel.name.contains(searchQuery, ignoreCase = true) ||
                    channel.group.contains(searchQuery, ignoreCase = true)
            matchesGroup && matchesSearch
        }
    }

    val groups = remember(channels) {
        if (channels.isEmpty()) emptyList()
        else listOf("All") + channels.map { it.group }.distinct().sorted()
    }

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
                Toast.makeText(context, "Loaded ${parsed.size} channels", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Row matching Image 2:
        // [Playlists] [ 0 Available ]   [ ⟳ ]  [ + Add M3U ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Playlists",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Teal Pill Badge: "X Available"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(TealPill)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${channels.size} Available",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular Refresh Button [⟳]
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TealIconBg)
                        .tvFocusable(shape = CircleShape)
                        .clickable {
                            Toast.makeText(context, "Playlists refreshed", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Cyan [ + Add M3U ] Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyanAccent)
                        .tvFocusable(shape = RoundedCornerShape(12.dp), onClick = { showAddDialog = true })
                        .clickable { showAddDialog = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Add M3U",
                        color = NavyBackground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Search Bar matching Image 2:
        // [ 🔍 Search 0 playlists (Live TV, Sports, etc.)... ]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NavyInput)
                .border(BorderStroke(1.dp, NavyBorder), RoundedCornerShape(14.dp))
                .tvFocusable(shape = RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextMuted,
                    modifier = Modifier.size(22.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search ${channels.size} playlists (Live TV, Sports, etc.)...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(CyanAccent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Search",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { searchQuery = "" }
                    )
                }
            }
        }

        // Content Area: Empty State or Channels Grid
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CyanAccent)
            }
        } else if (channels.isEmpty()) {
            // Empty State matching Image 2:
            // Centered icon [≡+], "No playlist added yet", and [ + Add M3U Playlist ] Cyan button
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Muted Playlist Plus Icon
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = "Playlist Empty",
                        tint = TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )

                    // Title: "No playlist added yet"
                    Text(
                        text = "No playlist added yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Big Cyan Button: [ + Add M3U Playlist ]
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .padding(horizontal = 8.dp)
                            .tvFocusable(shape = RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = "+ Add M3U Playlist",
                            color = NavyBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
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
                                .padding(horizontal = 14.dp, vertical = 7.dp)
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
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .tvFocusable(
                                shape = RoundedCornerShape(12.dp),
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
                            .clickable {
                                onPlayChannel(
                                    StreamConfig(
                                        url = channel.streamUrl,
                                        title = channel.name,
                                        format = StreamFormat.AUTO,
                                        extraHeaders = channel.headers
                                    )
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NavySurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!channel.logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = channel.logoUrl,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(4.dp)
                                    )
                                } else {
                                    Text(
                                        text = channel.name.take(3).uppercase(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent
                                    )
                                }
                            }

                            Text(
                                text = channel.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = channel.group,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // Add M3U Dialog
    if (showAddDialog) {
        AddM3uModal(
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
                        Toast.makeText(context, "Loaded ${parsed.size} channels", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Playlist empty or invalid URL", Toast.LENGTH_LONG).show()
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
private fun AddM3uModal(
    onDismiss: () -> Unit,
    onLoadUrl: (String) -> Unit,
    onPickFile: () -> Unit
) {
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

                CineInputField(
                    value = playlistUrl,
                    onValueChange = { playlistUrl = it },
                    placeholder = "https://example.com/playlist.m3u"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickFile,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).tvFocusable(shape = RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "File",
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick File", color = CyanAccent, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (playlistUrl.isNotBlank()) {
                                onLoadUrl(playlistUrl.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).tvFocusable(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text("Load", color = NavyBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

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

            val commaIdx = line.lastIndexOf(',')
            currentName = if (commaIdx != -1 && commaIdx < line.length - 1) {
                line.substring(commaIdx + 1).trim()
            } else {
                val nameMatch = tvgNameRegex.find(line)
                nameMatch?.groupValues?.getOrNull(1)?.ifBlank { "Channel" } ?: "Channel"
            }
        } else if (!line.startsWith("#") && line.isNotBlank()) {
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
