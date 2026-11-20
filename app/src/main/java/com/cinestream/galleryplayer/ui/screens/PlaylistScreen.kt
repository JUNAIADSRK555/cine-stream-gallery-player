package com.cinestream.galleryplayer.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.cinestream.galleryplayer.model.DrmType
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

val USER_AGENT_PRESETS = listOf(
    "Default",
    "Chrome(Android)",
    "Chrome(PC)",
    "IE(PC)",
    "Firefox(PC)",
    "iPhone",
    "Nokia",
    "Custom"
)

@Composable
fun PlaylistScreen(
    onPlayChannel: (StreamConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val playlists = PlaylistStateHolder.playlists

    var viewingPlaylist by remember { mutableStateOf<M3uPlaylist?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<M3uPlaylist?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("Loading playlist...") }

    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("All") }

    // Intercept back button when inside a playlist's channel view
    BackHandler(enabled = viewingPlaylist != null) {
        viewingPlaylist = null
        searchQuery = ""
        selectedGroup = "All"
    }

    // Keep viewingPlaylist up to date with state holder
    LaunchedEffect(playlists) {
        if (viewingPlaylist != null) {
            viewingPlaylist = playlists.firstOrNull { it.id == viewingPlaylist?.id }
        }
    }

    // Storage Access Framework file picker
    val m3uFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoading = true
                loadingMessage = "Reading local M3U file..."
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Local Playlist"
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
                    val newPlaylist = M3uPlaylist(
                        name = fileName,
                        sourceUrl = uri.toString(),
                        channels = parsed
                    )
                    PlaylistStateHolder.addPlaylist(context, newPlaylist)
                    Toast.makeText(context, "Added '$fileName' (${parsed.size} channels)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No playable channels found in file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = CyanAccent)
                    Text(
                        text = loadingMessage,
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (viewingPlaylist != null) {
            // ==========================================
            // LEVEL 2: CHANNELS VIEW (Within Selected Playlist)
            // ==========================================
            val playlist = viewingPlaylist!!
            val channels = playlist.channels

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Header: [ ← Back ] [ Playlist Name ] [ ⟳ Refresh ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(NavyCard)
                                .clickable {
                                    viewingPlaylist = null
                                    searchQuery = ""
                                    selectedGroup = "All"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Playlists",
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = playlist.name.ifBlank { "Playlist" },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${channels.size} channels",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Refresh Button for this playlist
                    if (playlist.sourceUrl.startsWith("http://", ignoreCase = true) ||
                        playlist.sourceUrl.startsWith("https://", ignoreCase = true)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(TealIconBg)
                                .clickable {
                                    coroutineScope.launch {
                                        isLoading = true
                                        loadingMessage = "Updating channels..."
                                        val reloaded = withContext(Dispatchers.IO) {
                                            fetchAndParseM3u(playlist.sourceUrl, playlist.getEffectiveUserAgent())
                                        }
                                        if (reloaded.isNotEmpty()) {
                                            PlaylistStateHolder.updatePlaylistChannels(context, playlist.id, reloaded)
                                            Toast.makeText(context, "Updated (${reloaded.size} channels)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Unable to refresh playlist", Toast.LENGTH_SHORT).show()
                                        }
                                        isLoading = false
                                    }
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
                    }
                }

                // Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NavyInput)
                        .border(BorderStroke(1.dp, NavyBorder), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search in ${playlist.name}...",
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

                // Group / Category Chips
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
                                    .clickable { selectedGroup = group }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
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
                                .clickable {
                                    val parsed = StreamConfig.parse(channel.streamUrl, channel.name)
                                    val finalHeaders = channel.headers.toMutableMap()
                                    finalHeaders.putAll(parsed.toHeaderMap())

                                    // Fallback to playlist User-Agent if channel doesn't provide one
                                    val effectiveUa = channel.headers["User-Agent"]
                                        ?: parsed.userAgent.takeIf { !it.startsWith("Mozilla") }
                                        ?: playlist.getEffectiveUserAgent()

                                    val finalConfig = StreamConfig(
                                        url = parsed.url,
                                        title = channel.name,
                                        cookie = channel.headers["Cookie"] ?: parsed.cookie,
                                        referer = channel.headers["Referer"] ?: parsed.referer,
                                        origin = channel.headers["Origin"] ?: parsed.origin,
                                        userAgent = effectiveUa,
                                        drmLicenseUrl = channel.drmLicenseUrl ?: parsed.drmLicenseUrl,
                                        drmType = if (channel.drmType != DrmType.NONE) channel.drmType else parsed.drmType,
                                        format = StreamFormat.AUTO,
                                        extraHeaders = finalHeaders
                                    )
                                    onPlayChannel(finalConfig)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
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

                                    if (channel.drmType != DrmType.NONE) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(CyanAccent)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = channel.drmType.displayName,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NavyBackground
                                            )
                                        }
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
        } else {
            // ==========================================
            // LEVEL 1: PLAYLIST MANAGEMENT SCREEN (Matches Image 10 exactly)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Header: "Playlist" on left, [ ⋮ ] 3-dots menu on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playlist",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.background(NavySurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add M3U URL", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.AddLink, contentDescription = null, tint = CyanAccent) },
                                onClick = {
                                    showOverflowMenu = false
                                    editingPlaylist = null
                                    showAddDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Import Local M3U/M3U8 File", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CyanAccent) },
                                onClick = {
                                    showOverflowMenu = false
                                    m3uFilePicker.launch(arrayOf("*/*"))
                                }
                            )

                            if (playlists.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Refresh Playlists", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = CyanAccent) },
                                    onClick = {
                                        showOverflowMenu = false
                                        coroutineScope.launch {
                                            isLoading = true
                                            loadingMessage = "Refreshing all playlists..."
                                            for (pl in playlists) {
                                                if (pl.sourceUrl.startsWith("http", ignoreCase = true)) {
                                                    val reloaded = withContext(Dispatchers.IO) {
                                                        fetchAndParseM3u(pl.sourceUrl, pl.getEffectiveUserAgent())
                                                    }
                                                    if (reloaded.isNotEmpty()) {
                                                        PlaylistStateHolder.updatePlaylistChannels(context, pl.id, reloaded)
                                                    }
                                                }
                                            }
                                            isLoading = false
                                            Toast.makeText(context, "Playlists refreshed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Clear All Playlists", color = AccentRed) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AccentRed) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showClearConfirmation = true
                                    }
                                )
                            }
                        }
                    }
                }

                if (playlists.isEmpty()) {
                    // Empty State
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
                                contentDescription = null,
                                tint = TextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(76.dp)
                            )

                            Text(
                                text = "No playlist added yet",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Text(
                                text = "Add an M3U IPTV link or import a local file to start streaming",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    editingPlaylist = null
                                    showAddDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(48.dp)
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
                    // Vertical List of Playlist Cards (Image 10)
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = NavyCard),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, NavyBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { viewingPlaylist = pl }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left: Name, URL, Channel Count
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = pl.name.ifBlank { pl.sourceUrl },
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Text(
                                            text = pl.sourceUrl,
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 16.sp
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(TealPill)
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${pl.channelCount} channels",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = CyanAccent
                                                )
                                            }

                                            if (pl.userAgentPreset != "Default") {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(NavySurface)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = pl.userAgentPreset,
                                                        fontSize = 10.sp,
                                                        color = TextMuted
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Right: Pencil Edit Icon
                                    IconButton(
                                        onClick = {
                                            editingPlaylist = pl
                                            showAddDialog = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Playlist",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // ADD / EDIT PLAYLIST DIALOG ("Enter Playlist details")
    // ==========================================
    if (showAddDialog) {
        PlaylistDetailsDialog(
            initialPlaylist = editingPlaylist,
            onDismiss = {
                showAddDialog = false
                editingPlaylist = null
            },
            onSave = { name, url, uaPreset, customUa ->
                showAddDialog = false
                val isEditing = editingPlaylist != null
                val plId = editingPlaylist?.id
                editingPlaylist = null

                coroutineScope.launch {
                    isLoading = true
                    loadingMessage = if (isEditing) "Updating playlist..." else "Fetching playlist channels..."

                    val parsed = withContext(Dispatchers.IO) {
                        fetchAndParseM3u(url, getEffectiveUaString(uaPreset, customUa))
                    }

                    isLoading = false
                    val fallbackName = name.ifBlank { url.substringAfterLast("/").substringBefore("?").ifBlank { "Playlist" } }

                    if (isEditing && plId != null) {
                        val existing = playlists.firstOrNull { it.id == plId }
                        if (existing != null) {
                            val updatedChannels = if (parsed.isNotEmpty()) parsed else existing.channels
                            val updated = existing.copy(
                                name = fallbackName,
                                sourceUrl = url,
                                userAgentPreset = uaPreset,
                                customUserAgent = customUa,
                                channels = updatedChannels
                            )
                            PlaylistStateHolder.updatePlaylist(context, updated)
                            Toast.makeText(context, "Playlist updated", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        if (parsed.isNotEmpty()) {
                            val newPlaylist = M3uPlaylist(
                                name = fallbackName,
                                sourceUrl = url,
                                userAgentPreset = uaPreset,
                                customUserAgent = customUa,
                                channels = parsed
                            )
                            PlaylistStateHolder.addPlaylist(context, newPlaylist)
                            Toast.makeText(context, "Saved '$fallbackName' (${parsed.size} channels)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Invalid playlist URL or unable to load channels", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onDelete = { pl ->
                showAddDialog = false
                editingPlaylist = null
                coroutineScope.launch {
                    PlaylistStateHolder.removePlaylist(context, pl.id)
                    if (viewingPlaylist?.id == pl.id) viewingPlaylist = null
                    Toast.makeText(context, "Deleted '${pl.name}'", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Clear All Confirmation Dialog
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear All Playlists?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove all saved playlists? This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        coroutineScope.launch {
                            PlaylistStateHolder.clearAllPlaylists(context)
                            viewingPlaylist = null
                            Toast.makeText(context, "All playlists cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Clear All", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel", color = TextPrimary)
                }
            },
            containerColor = NavySurface
        )
    }
}

/**
 * Clean Modal Dialog matching "Enter Playlist details":
 * 1. "Enter playlist name" text input
 * 2. "Enter playlist url" multi-line text input
 * 3. "UserAgent" dropdown selector with presets (Default, Chrome, IE, Firefox, iPhone, Nokia, Custom)
 * 4. Action Buttons: Cancel, Delete (if editing), Save / Update
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailsDialog(
    initialPlaylist: M3uPlaylist?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, uaPreset: String, customUa: String) -> Unit,
    onDelete: (M3uPlaylist) -> Unit
) {
    var name by remember { mutableStateOf(initialPlaylist?.name ?: "") }
    var url by remember { mutableStateOf(initialPlaylist?.sourceUrl ?: "") }
    var selectedUaPreset by remember { mutableStateOf(initialPlaylist?.userAgentPreset ?: "Default") }
    var customUa by remember { mutableStateOf(initialPlaylist?.customUserAgent ?: "") }
    var uaDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dialog Title
                Text(
                    text = "Enter Playlist details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // 1. Enter playlist name
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Playlist Name",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    CineInputField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Enter playlist name"
                    )
                }

                // 2. Enter playlist url
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Playlist URL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NavyInput)
                            .border(BorderStroke(1.dp, NavyBorder), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (url.isEmpty()) {
                            Text(
                                text = "Enter playlist url",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = url,
                            onValueChange = { url = it },
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(CyanAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 3. UserAgent Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "UserAgent",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )

                    ExposedDropdownMenuBox(
                        expanded = uaDropdownExpanded,
                        onExpandedChange = { uaDropdownExpanded = !uaDropdownExpanded }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NavyInput)
                                .border(BorderStroke(1.dp, NavyBorder), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedUaPreset,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = if (uaDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = CyanAccent
                                )
                            }
                        }

                        ExposedDropdownMenu(
                            expanded = uaDropdownExpanded,
                            onDismissRequest = { uaDropdownExpanded = false },
                            modifier = Modifier.background(NavyCard)
                        ) {
                            USER_AGENT_PRESETS.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset, color = if (preset == selectedUaPreset) CyanAccent else TextPrimary) },
                                    onClick = {
                                        selectedUaPreset = preset
                                        uaDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // If Custom UserAgent chosen, reveal input field
                    if (selectedUaPreset == "Custom") {
                        Spacer(modifier = Modifier.height(4.dp))
                        CineInputField(
                            value = customUa,
                            onValueChange = { customUa = it },
                            placeholder = "Enter custom User-Agent string"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom Action Buttons (Pill shaped)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NavyBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Cancel", color = TextSecondary, fontSize = 13.sp)
                    }

                    // Delete (visible only when editing)
                    if (initialPlaylist != null) {
                        Button(
                            onClick = { onDelete(initialPlaylist) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text("Delete", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Save / Update
                    Button(
                        onClick = {
                            val cleanUrl = url.trim()
                            if (cleanUrl.isNotBlank()) {
                                onSave(name.trim(), cleanUrl, selectedUaPreset, customUa.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (initialPlaylist != null) "Update" else "Save",
                            color = NavyBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

private fun getEffectiveUaString(preset: String, custom: String): String {
    return when (preset) {
        "Chrome(Android)" -> "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        "Chrome(PC)" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        "IE(PC)" -> "Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko"
        "Firefox(PC)" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
        "iPhone" -> "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
        "Nokia" -> "NokiaN97/21.0.045 (SymbianOS/9.4; Series60/5.0 Mozilla/5.0; Profile/MIDP-2.1 Configuration/CLDC-1.1) AppleWebkit/525 (KHTML, like Gecko) BrowserNG/7.1.4"
        "Custom" -> custom.ifBlank { "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36" }
        else -> "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

private fun fetchAndParseM3u(urlString: String, userAgent: String): List<M3uChannel> {
    return try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty(
            "User-Agent",
            userAgent.ifBlank { "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36" }
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
    val currentHeaders = mutableMapOf<String, String>()
    var currentDrmType = DrmType.NONE
    var currentDrmLicenseUrl: String? = null

    val tvgNameRegex = Regex("""tvg-name="([^"]*)"""")
    val tvgLogoRegex = Regex("""tvg-logo="([^"]*)"""")
    val groupRegex = Regex("""group-title="([^"]*)"""")

    reader.forEachLine { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXTINF:", ignoreCase = true) -> {
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
            }
            line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                val prop = line.substringAfter(":").trim()
                val k = prop.substringBefore("=").trim().lowercase()
                val v = prop.substringAfter("=").trim()
                when {
                    k.contains("license_type") -> {
                        currentDrmType = when {
                            v.contains("clearkey", ignoreCase = true) -> DrmType.CLEARKEY
                            v.contains("widevine", ignoreCase = true) || v.contains("com.widevine.alpha", ignoreCase = true) -> DrmType.WIDEVINE
                            v.contains("playready", ignoreCase = true) || v.contains("com.microsoft.playready", ignoreCase = true) -> DrmType.PLAYREADY
                            else -> DrmType.NONE
                        }
                    }
                    k.contains("license_key") -> {
                        currentDrmLicenseUrl = v
                        if (currentDrmType == DrmType.NONE) {
                            currentDrmType = if (v.contains(":") && !v.startsWith("http", ignoreCase = true)) DrmType.CLEARKEY else DrmType.WIDEVINE
                        }
                    }
                }
            }
            line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                val opt = line.substringAfter(":").trim()
                val k = opt.substringBefore("=").trim().lowercase()
                val v = opt.substringAfter("=").trim()
                when {
                    k in listOf("http-user-agent", "user-agent") -> currentHeaders["User-Agent"] = v
                    k in listOf("http-referrer", "http-referer", "referrer", "referer") -> currentHeaders["Referer"] = v
                    k in listOf("http-origin", "origin") -> currentHeaders["Origin"] = v
                    k in listOf("http-cookie", "cookie") -> currentHeaders["Cookie"] = v
                    else -> currentHeaders[k] = v
                }
            }
            line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                val jsonStr = line.substringAfter(":").trim()
                try {
                    val jsonObj = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                    for (entry in jsonObj.entrySet()) {
                        val k = entry.key
                        val v = entry.value.asString
                        val kLower = k.lowercase()
                        when (kLower) {
                            "cookie" -> currentHeaders["Cookie"] = v
                            "user-agent" -> currentHeaders["User-Agent"] = v
                            "referer", "referrer" -> currentHeaders["Referer"] = v
                            "origin" -> currentHeaders["Origin"] = v
                            else -> currentHeaders[k] = v
                        }
                    }
                } catch (_: Exception) {}
            }
            !line.startsWith("#") && line.isNotBlank() -> {
                val name = currentName ?: "Channel ${channels.size + 1}"

                // Parse inline pipe URL parameters if present
                val parsedConfig = if (line.contains("|")) StreamConfig.parse(line, name) else null
                val finalUrl = parsedConfig?.url ?: line
                val finalHeaders = currentHeaders.toMutableMap()
                if (parsedConfig != null) {
                    finalHeaders.putAll(parsedConfig.toHeaderMap())
                }
                val finalDrmType = if (currentDrmType != DrmType.NONE) currentDrmType else (parsedConfig?.drmType ?: DrmType.NONE)
                val finalDrmLicense = currentDrmLicenseUrl ?: parsedConfig?.drmLicenseUrl

                channels.add(
                    M3uChannel(
                        name = name,
                        streamUrl = finalUrl,
                        logoUrl = currentLogo,
                        group = currentGroup,
                        headers = finalHeaders,
                        drmType = finalDrmType,
                        drmLicenseUrl = finalDrmLicense
                    )
                )

                currentName = null
                currentLogo = null
                currentGroup = "General"
                currentHeaders.clear()
                currentDrmType = DrmType.NONE
                currentDrmLicenseUrl = null
            }
        }
    }

    return channels
}
