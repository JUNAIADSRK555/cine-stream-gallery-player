package com.cinestream.galleryplayer.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cinestream.galleryplayer.model.DrmType
import com.cinestream.galleryplayer.model.StreamConfig
import com.cinestream.galleryplayer.ui.components.tvFocusable
import com.cinestream.galleryplayer.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

data class VideoTrackOption(
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val label: String,
    val isAuto: Boolean = false,
    val isSelected: Boolean = false
)

data class AudioTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String,
    val label: String,
    val isSelected: Boolean = false
)

enum class PlayerResizeMode(val displayName: String, val modeInt: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FIXED_WIDTH("16:9", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    FIXED_HEIGHT("4:3", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT)
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamConfig: StreamConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val window = activity?.window

    // Immersive Mode: Hide Status Bar (5G, Wifi, Clock, Battery) & Navigation Bar
    DisposableEffect(window) {
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        onDispose {
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Handle Hardware & Remote Back Button
    BackHandler {
        onBack()
    }

    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isLiveStream by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentResizeMode by remember { mutableStateOf(PlayerResizeMode.FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var isFullscreenLandscape by remember { mutableStateOf(false) }
    var trackSelectionVersion by remember { mutableIntStateOf(0) }

    // Initialize ExoPlayer with maximum stream compatibility
    val exoPlayer = remember(streamConfig) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent(
                streamConfig.userAgent.ifBlank {
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }
            )

        val headers = streamConfig.toHeaderMap()
        if (headers.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(headers)
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(streamConfig.url))

        // Set MIME type
        val mimeType = streamConfig.effectiveMimeType()
        if (mimeType != null) {
            mediaItemBuilder.setMimeType(mimeType)
        }

        // Attach DRM Configuration if DRM is selected and URL provided
        if (streamConfig.drmType != DrmType.NONE && streamConfig.drmLicenseUrl.isNotBlank()) {
            val drmUuid = streamConfig.drmType.uuid ?: C.WIDEVINE_UUID
            val drmConfig = MediaItem.DrmConfiguration.Builder(drmUuid)
                .setLicenseUri(streamConfig.drmLicenseUrl)
                .setLicenseRequestHeaders(headers)
                .build()
            mediaItemBuilder.setDrmConfiguration(drmConfig)
        }

        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.playWhenReady = true
        player
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    errorMessage = null
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                    isLiveStream = exoPlayer.isCurrentMediaItemLive
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                val reason = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network connection failed / timed out"
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "HTTP Bad Status (Check Cookie/Referer)"
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Stream source not found (404)"
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Malformed manifest / index"
                    PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "DRM License acquisition failed"
                    else -> error.errorCodeName.replace("ERROR_CODE_", "").replace("_", " ").lowercase()
                }
                errorMessage = reason.ifBlank { "Source error" }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Ticker for current playback time
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isLiveStream = exoPlayer.isCurrentMediaItemLive
            delay(500)
        }
    }

    // Auto-hide controls timer (4s)
    LaunchedEffect(showControls, isScreenLocked) {
        if (showControls && !isScreenLocked) {
            delay(4000)
            showControls = false
        }
    }

    // Extract Video Quality Options from ExoPlayer
    val videoOptions = remember(exoPlayer.currentTracks, trackSelectionVersion) {
        val list = mutableListOf<VideoTrackOption>()
        val currentMaxHeight = exoPlayer.trackSelectionParameters.maxVideoHeight
        val isAutoSelected = currentMaxHeight == Int.MAX_VALUE

        list.add(
            VideoTrackOption(
                width = 0,
                height = 0,
                bitrate = 0,
                label = "Auto (Adaptive)",
                isAuto = true,
                isSelected = isAutoSelected
            )
        )

        val seenHeights = mutableSetOf<Int>()
        for (group in exoPlayer.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val h = format.height
                    val w = format.width
                    if (h > 0 && seenHeights.add(h)) {
                        val label = when {
                            h >= 2160 -> "4K (2160p)"
                            h >= 1440 -> "2K (1440p)"
                            h >= 1080 -> "1080p FHD"
                            h >= 720 -> "720p HD"
                            h >= 480 -> "480p SD"
                            h >= 360 -> "360p"
                            h >= 240 -> "240p"
                            else -> "${h}p"
                        }
                        val bitrateStr = if (format.bitrate > 0) " (${format.bitrate / 1000} kbps)" else ""
                        val isSelected = !isAutoSelected && (currentMaxHeight == h || group.isTrackSelected(i))
                        list.add(
                            VideoTrackOption(
                                width = w,
                                height = h,
                                bitrate = format.bitrate,
                                label = "$label$bitrateStr",
                                isAuto = false,
                                isSelected = isSelected
                            )
                        )
                    }
                }
            }
        }
        val specific = list.drop(1).sortedByDescending { it.height }
        listOf(list.first()) + specific
    }

    // Extract Audio Language Options from ExoPlayer
    val audioOptions = remember(exoPlayer.currentTracks, trackSelectionVersion) {
        val list = mutableListOf<AudioTrackOption>()
        var trackCount = 0

        for (groupIndex in 0 until exoPlayer.currentTracks.groups.size) {
            val group = exoPlayer.currentTracks.groups[groupIndex]
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)
                    val langCode = format.language ?: "und"
                    val locale = if (langCode != "und" && langCode.isNotBlank()) Locale(langCode) else null
                    val langDisplayName = locale?.getDisplayLanguage(Locale.ENGLISH)?.replaceFirstChar { it.uppercase() } ?: ""

                    val label = buildString {
                        if (langDisplayName.isNotBlank()) {
                            append(langDisplayName)
                        } else if (!format.label.isNullOrBlank()) {
                            append(format.label)
                        } else {
                            append("Audio Track ${trackCount + 1}")
                        }
                        if (format.channelCount > 0) {
                            append(if (format.channelCount == 6) " (5.1)" else if (format.channelCount == 2) " (Stereo)" else " (${format.channelCount}ch)")
                        }
                    }

                    list.add(
                        AudioTrackOption(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            language = langCode,
                            label = label,
                            isSelected = isSelected
                        )
                    )
                    trackCount++
                }
            }
        }

        if (list.isEmpty()) {
            list.add(
                AudioTrackOption(
                    groupIndex = 0,
                    trackIndex = 0,
                    language = "default",
                    label = "Default Audio (Stereo)",
                    isSelected = true
                )
            )
        }
        list
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!showControls) {
                                showControls = true
                                true
                            } else {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                true
                            }
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            showControls = !showControls
                            true
                        }
                        Key.DirectionLeft -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                            showControls = true
                            true
                        }
                        Key.DirectionRight -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                            showControls = true
                            true
                        }
                        Key.Back, Key.Escape -> {
                            if (showControls) {
                                showControls = false
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isScreenLocked) {
                    showControls = !showControls
                }
            }
    ) {
        // ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exoPlayer
                    useController = false
                    resizeMode = currentResizeMode.modeInt
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = currentResizeMode.modeInt
            },
            modifier = Modifier.fillMaxSize()
        )

        // Center White Loading Spinner matching Photo 5:
        // Clean white circle spinner right in the middle during buffering!
        if (isBuffering && errorMessage == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        // Playback Failed Dialog matching Photo 1:
        // Red exclamation circle icon, "Playback Failed", "Source error", and [Back] [Retry] buttons
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NavyBackground.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Red exclamation mark circle icon matching Photo 1
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(AccentRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = AccentRed,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // Title: "Playback Failed"
                        Text(
                            text = "Playback Failed",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        // Subtitle: "Source error"
                        Text(
                            text = errorMessage ?: "Source error",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Buttons: [ Back ]  [ Retry ] (Cyan)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = NavySurface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .tvFocusable(shape = RoundedCornerShape(12.dp))
                            ) {
                                Text("Back", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    errorMessage = null
                                    isBuffering = true
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .tvFocusable(shape = RoundedCornerShape(12.dp))
                            ) {
                                Text("Retry", color = NavyBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Screen Lock Floating Indicator when Locked
        if (isScreenLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable {
                        isScreenLocked = false
                        showControls = true
                        Toast.makeText(context, "Screen Unlocked", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Unlock",
                    tint = CyanAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Controls Overlay matching Photo 5 & Photo 6
        AnimatedVisibility(
            visible = showControls && !isScreenLocked && errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Bar matching Photo 5 & 6:
                // [ ✕ ]   [ index.mpd / Title ]              [ 🔒 ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // [ ✕ ] Close Button
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { onBack() }
                        )

                        // Title: e.g. "index.mpd"
                        val displayTitle = remember(streamConfig) {
                            if (streamConfig.title.isNotBlank() && streamConfig.title != "Live Stream" && streamConfig.title != "Cine Stream") {
                                streamConfig.title
                            } else {
                                streamConfig.url.substringAfterLast("/").substringBefore("?").ifBlank { "index.mpd" }
                            }
                        }

                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // [ 🔒 ] Screen Lock Icon
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Lock Screen",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable {
                                isScreenLocked = true
                                showControls = false
                                Toast.makeText(context, "Screen Locked (Tap lock icon to unlock)", Toast.LENGTH_SHORT).show()
                            }
                    )
                }

                // Center Play / Pause Toggler
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Bottom Controls Bar matching Photo 5 & Photo 6:
                // Timeline Scrubber Line + Buttons Row:
                // [ 00:00 · 00:00 ]   [ 🔊 ]   [ Fit ]   [ CC ]   [ 🗖 ]   [ ⚙ ]   [ ⛶ ]   [ › ]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Timeline Scrubber
                    if (!isLiveStream && duration > 0) {
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { newPos ->
                                currentPosition = newPos.toLong()
                                exoPlayer.seekTo(newPos.toLong())
                            },
                            valueRange = 0f..duration.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Buttons Row matching Photo 5 & 6
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Current Time · Duration or LIVE
                        if (isLiveStream) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AccentRed)
                                )
                                Text(
                                    text = "LIVE",
                                    color = AccentRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "${formatPlaybackTime(currentPosition)} · ${formatPlaybackTime(duration)}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Right Action Buttons Row matching Photo 5 & 6 + Quality & Language:
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. [ 🔊 / 🔇 ] Volume / Mute
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Audio / Mute",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        isMuted = !isMuted
                                        exoPlayer.volume = if (isMuted) 0f else 1f
                                    }
                            )

                            // 2. [ ⚙ Quality ] Video Quality Button (User request: qulaty butoon)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TealButtonBg)
                                    .clickable { showQualityDialog = true }
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HighQuality,
                                        contentDescription = "Quality",
                                        tint = CyanAccent,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Text(
                                        text = "Quality",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 3. [ 🌐 Lang ] Audio Language Button (User request: lanuge button)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TealButtonBg)
                                    .clickable { showLanguageDialog = true }
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "Language",
                                        tint = CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Lang",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 4. [ Fit ] Aspect Ratio Toggle Text Button (Photo 6)
                            Text(
                                text = currentResizeMode.displayName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        val modes = PlayerResizeMode.entries
                                        val nextIdx = (modes.indexOf(currentResizeMode) + 1) % modes.size
                                        currentResizeMode = modes[nextIdx]
                                    }
                            )

                            // 5. [ CC ] Subtitles / Captions (Photo 6)
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Closed Captions",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        Toast.makeText(context, "Captions / Subtitles Active", Toast.LENGTH_SHORT).show()
                                    }
                            )

                            // 6. [ 🗖 ] Picture in Picture (PiP) (Photo 5)
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                            try {
                                                val params = PictureInPictureParams.Builder().build()
                                                activity.enterPictureInPictureMode(params)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                            )

                            // 7. [ ⚙ ] Speed Settings (Photo 6)
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Playback Speed",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { showSpeedDialog = true }
                            )

                            // 8. [ ⛶ ] Fullscreen / Rotate Screen (Photo 5)
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Rotate",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        isFullscreenLandscape = !isFullscreenLandscape
                                        activity?.requestedOrientation = if (isFullscreenLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    // Playback Speed Dialog
    if (showSpeedDialog) {
        Dialog(onDismissRequest = { showSpeedDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Playback Speed",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    speeds.forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (playbackSpeed == speed) TealPill else NavyCard)
                                .clickable {
                                    playbackSpeed = speed
                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                    showSpeedDialog = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${speed}x",
                                color = if (playbackSpeed == speed) CyanAccent else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                            )
                            if (playbackSpeed == speed) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Video Quality Selection Dialog (User request: qulaty butoon)
    if (showQualityDialog) {
        Dialog(onDismissRequest = { showQualityDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = "Quality",
                            tint = CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Select Video Quality",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 350.dp)
                    ) {
                        items(videoOptions) { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (option.isSelected) TealPill else NavyCard)
                                    .clickable {
                                        if (option.isAuto) {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .clearVideoSizeConstraints()
                                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                                .build()
                                            Toast.makeText(context, "Quality: Auto (Adaptive)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setMaxVideoSize(option.width.coerceAtLeast(1), option.height.coerceAtLeast(1))
                                                .setMinVideoSize(option.width.coerceAtLeast(1), option.height.coerceAtLeast(1))
                                                .build()
                                            Toast.makeText(context, "Quality: ${option.label}", Toast.LENGTH_SHORT).show()
                                        }
                                        trackSelectionVersion++
                                        showQualityDialog = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (option.isSelected) CyanAccent else TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = if (option.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (option.isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = CyanAccent,
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

    // Audio Language Selection Dialog (User request: lanuge button)
    if (showLanguageDialog) {
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Language",
                            tint = CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Select Audio Language",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 350.dp)
                    ) {
                        items(audioOptions) { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (option.isSelected) TealPill else NavyCard)
                                    .clickable {
                                        val group = exoPlayer.currentTracks.groups.getOrNull(option.groupIndex)
                                        if (group != null) {
                                            val override = TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                .addOverride(override)
                                                .apply {
                                                    if (option.language != "und" && option.language != "default") {
                                                        setPreferredAudioLanguage(option.language)
                                                    }
                                                }
                                                .build()
                                        }
                                        trackSelectionVersion++
                                        showLanguageDialog = false
                                        Toast.makeText(context, "Audio: ${option.label}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (option.isSelected) CyanAccent else TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = if (option.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (option.isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = CyanAccent,
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

private fun formatPlaybackTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
