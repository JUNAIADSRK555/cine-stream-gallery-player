package com.cinestream.galleryplayer.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.cinestream.galleryplayer.model.LocalVideoItem
import com.cinestream.galleryplayer.model.StreamConfig
import com.cinestream.galleryplayer.model.StreamFormat
import com.cinestream.galleryplayer.ui.components.tvFocusable
import com.cinestream.galleryplayer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LocalScreen(
    onPlayVideo: (StreamConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Determine target permission based on Android API level
    val videoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, videoPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    var localVideos by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Storage permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Storage permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // USB / Folder Tree Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            Toast.makeText(context, "Selected Folder / USB: ${treeUri.lastPathSegment}", Toast.LENGTH_LONG).show()
        }
    }

    // Single Video File Picker (for USB or SAF directly)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { fileUri: Uri? ->
        if (fileUri != null) {
            val fileName = fileUri.lastPathSegment ?: "Local Video"
            onPlayVideo(
                StreamConfig(
                    url = fileUri.toString(),
                    title = fileName,
                    format = StreamFormat.AUTO
                )
            )
        }
    }

    // Load videos when permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            localVideos = withContext(Dispatchers.IO) {
                queryLocalVideos(context)
            }
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Local & USB Media",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Browse gallery videos, external storage and USB drives",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            // USB / Storage Actions Row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // USB / Folder Picker Button
                Button(
                    onClick = { folderPickerLauncher.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.tvFocusable(shape = RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = "USB",
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("USB / Folder", color = CyanAccent, fontSize = 12.sp)
                }

                // File Picker Button
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("video/*")) },
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick File", color = CyanAccent, fontSize = 12.sp)
                }
            }
        }

        // Permission Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (hasPermission) NavySurface else NavyCard
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (hasPermission) AccentGreen.copy(alpha = 0.15f) else AccentOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Lock,
                            contentDescription = "Permission Status",
                            tint = if (hasPermission) AccentGreen else AccentOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = if (hasPermission) "Gallery Access Granted" else "Gallery Permission Required",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (hasPermission) {
                                "${localVideos.size} video(s) indexed from internal & external storage"
                            } else {
                                "Grant storage access to browse and play local device videos"
                            },
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                if (!hasPermission) {
                    Button(
                        onClick = { permissionLauncher.launch(videoPermission) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.tvFocusable(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text("Grant Permission", color = NavyBackground, fontWeight = FontWeight.Bold)
                    }
                } else {
                    IconButton(
                        onClick = {
                            isLoading = true
                            localVideos = queryLocalVideos(context)
                            isLoading = false
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = CyanAccent
                        )
                    }
                }
            }
        }

        // Local Videos Content Area
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CyanAccent)
            }
        } else if (localVideos.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(NavyCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "No Videos",
                            tint = CyanAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "No Videos Found",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = if (!hasPermission) {
                            "Grant gallery permission above to index videos."
                        } else {
                            "No media files detected. Connect a USB drive or select a folder above."
                        },
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
        } else {
            // Video Grid (responsive for Phone & Android TV)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(localVideos, key = { it.id }) { video ->
                    VideoCardItem(
                        video = video,
                        onClick = {
                            onPlayVideo(
                                StreamConfig(
                                    url = video.contentUri.toString(),
                                    title = video.displayName,
                                    format = StreamFormat.AUTO
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCardItem(
    video: LocalVideoItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .tvFocusable(shape = RoundedCornerShape(12.dp), onClick = onClick)
            .clickable(onClick = onClick)
    ) {
        Column {
            // Video Thumbnail Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(NavyCard)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(video.contentUri)
                        .videoFrameMillis(2000)
                        .crossfade(true)
                        .build(),
                    contentDescription = video.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Duration Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Play icon overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(NavyBackground.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Info Column
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = video.formattedSize,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    if (video.resolution.isNotBlank()) {
                        Text(
                            text = video.resolution,
                            fontSize = 10.sp,
                            color = CyanAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Queries Android MediaStore for local videos.
 */
private fun queryLocalVideos(context: Context): List<LocalVideoItem> {
    val videoList = mutableListOf<LocalVideoItem>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.RESOLUTION,
        MediaStore.Video.Media.DATA
    )

    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    try {
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val resColumn = cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION)
            val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Untitled"
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val resolution = if (resColumn != -1) cursor.getString(resColumn) ?: "" else ""
                val path = if (dataColumn != -1) cursor.getString(dataColumn) ?: "" else ""

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                videoList.add(
                    LocalVideoItem(
                        id = id,
                        contentUri = contentUri,
                        displayName = name,
                        durationMs = duration,
                        sizeBytes = size,
                        resolution = resolution,
                        path = path
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return videoList
}
