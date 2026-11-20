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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.cinestream.galleryplayer.data.PlaylistStateHolder
import com.cinestream.galleryplayer.model.M3uChannel
import com.cinestream.galleryplayer.model.M3uPlaylist
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

    // Persistent Multi-M3U playlists from StateHolder
    val playlists = PlaylistStateHolder.playlists
    val selectedPlaylistId = PlaylistStateHolder.selectedPlaylistId

    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var pendingPlaylistName by remember { mutableStateOf("") }

    // Load persistent playlists on launch
    LaunchedEffect(Unit) {
        PlaylistStateHolder.initialize(context)
    }

    // Active playlist
    val activePlaylist = remember(playlists, selectedPlaylistId) {
        playlists.firstOrNull { it.id == selectedPlaylistId } ?: playlists.firstOrNull()
    }

    val activeChannels = remember(activePlaylist) {
        activePlaylist?.channels ?: emptyList()
    }

    val filteredChannels = remember(activeChannels, searchQuery, selectedGroup) {
        activeChannels.filter { channel ->
            val matchesGroup = selectedGroup == "All" || channel.group.equals(selectedGroup, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    channel.name.contains(searchQuery, ignoreCase = true) ||
                    channel.group.contains(searchQuery, ignoreCase = true)
            matchesGroup && matchesSearch
        }
    }

    val groups = remember(activeChannels) {
        if (activeChannels.isEmpty()) emptyList()
        else listOf("All") + activeChannels.map { it.group }.distinct().sorted()
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
                isLoading = false
                if (parsed.isNotEmpty()) {
                    val name = pendingPlaylistName.ifBlank { "Playlist ${playlists.size + 1}" }
                    val newPlaylist = M3uPlaylist(
                        name = name,
                        sourceUrl = uri.toString(),
                        channels = parsed
                    )
                    PlaylistStateHolder.addPlaylist(context, newPlaylist)
                    Toast.makeText(context, "Saved '$name' permanently (${parsed.size} channels)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No channels found in file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header matching Photo 2 & 3:
        // [Playlists] [ X Available ]   [ ⟳ ]  [ + Add M3U ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Title + Available Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Playlists",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(TealPill)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${playlists.size} Available",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }
            }

            // Right: Refresh & Add M3U
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Refresh Button [⟳]
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(TealIconBg)
                        .tvFocusable(shape = CircleShape)
                        .clickable {
                            if (activePlaylist != null && activePlaylist.sourceUrl.startsWith("http", ignoreCase = true)) {
                                coroutineScope.launch {
                                    isLoading = true
                                    val reloaded = withContext(Dispatchers.IO) {
                                        fetchAndParseM3u(activePlaylist.sourceUrl)
                                    }
                                    if (reloaded.isNotEmpty()) {
                                        PlaylistStateHolder.updatePlaylistChannels(context, activePlaylist.id, reloaded)
                                        Toast.makeText(context, "Playlist updated (${reloaded.size} channels)", Toast.LENGTH_SHORT).show()
                                    }
                                    isLoading = false
                                }
                            } else {
                                Toast.makeText(context, "Playlists saved & up to date", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Cyan [ + Add M3U ] Button (Single-line, never wrapped)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyanAccent)
                        .tvFocusable(shape = RoundedCornerShape(10.dp), onClick = { showAddDialog = true })
                        .clickable { showAddDialog = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Add M3U",
                        color = NavyBackground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
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
        } else if (playlists.isEmpty()) {
            // Empty State matching Photo 3
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = "Playlist Empty",
                        tint = TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )

                    Text(
                        text = "No playlist added yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

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
            // Multi-M3U Playlist Tabs Bar:
            // Allows user to switch between multiple added M3U playlists!
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(playlists, key = { it.id }) { pl ->
                    val isSelected = pl.id == activePlaylist?.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) TealPill else NavyCard)
                            .border(
                                BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) CyanAccent else NavyBorder
                                ),
                                RoundedCornerShape(10.dp)
                            )
                            .tvFocusable(shape = RoundedCornerShape(10.dp), onClick = { PlaylistStateHolder.selectedPlaylistId = pl.id })
                            .clickable { PlaylistStateHolder.selectedPlaylistId = pl.id }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = pl.name,
                                color = if (isSelected) CyanAccent else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = "(${pl.channelCount})",
                                color = if (isSelected) CyanAccent.copy(alpha = 0.8f) else TextSecondary,
                                fontSize = 11.sp
                            )
                            // Delete button for this playlist
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        coroutineScope.launch {
                                            PlaylistStateHolder.removePlaylist(context, pl.id)
                                            Toast.makeText(context, "Removed '${pl.name}'", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // Search Bar matching Photo 3
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
                                text = "Search ${activeChannels.size} channels in ${activePlaylist?.name ?: "playlist"}...",
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
                            contentDescription = "Clear",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }

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
                                .tvFocusable(shape = RoundedCornerShape(8.dp), onClick = { selectedGroup = group })
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

            // Channels Grid
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

    // Add Multi-M3U Modal
    if (showAddDialog) {
        AddMultiM3uModal(
            onDismiss = { showAddDialog = false },
            onLoadUrl = { name, url ->
                showAddDialog = false
                isLoading = true
                coroutineScope.launch {
                    val parsed = withContext(Dispatchers.IO) {
                        fetchAndParseM3u(url)
                    }
                    isLoading = false
                    if (parsed.isNotEmpty()) {
                        val playlistName = name.ifBlank { "Playlist ${playlists.size + 1}" }
                        val newPlaylist = M3uPlaylist(
                            name = playlistName,
                            sourceUrl = url,
                            channels = parsed
                        )
                        PlaylistStateHolder.addPlaylist(context, newPlaylist)
                        Toast.makeText(context, "Saved '$playlistName' permanently (${parsed.size} channels)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Invalid playlist URL or empty playlist", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onPickFile = { name ->
                pendingPlaylistName = name
                showAddDialog = false
                m3uFilePicker.launch(arrayOf("*/*"))
            }
        )
    }
}

@Composable
private fun AddMultiM3uModal(
    onDismiss: () -> Unit,
    onLoadUrl: (name: String, url: String) -> Unit,
    onPickFile: (name: String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(18.dp),
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
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = "Playlist Name (e.g. Sports TV)"
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
                        onClick = { onPickFile(playlistName) },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).tvFocusable(shape = RoundedCornerShape(10.dp))
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
                                onLoadUrl(playlistName, playlistUrl.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).tvFocusable(shape = RoundedCornerShape(10.dp))
                    ) {
                        Text("Add URL", color = NavyBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )
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
