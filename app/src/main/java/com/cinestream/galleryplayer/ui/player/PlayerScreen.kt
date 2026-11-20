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
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cinestream.galleryplayer.model.DrmType
import com.cinestream.galleryplayer.model.StreamConfig
import com.cinestream.galleryplayer.model.StreamFormat
import com.cinestream.galleryplayer.ui.components.tvFocusable
import com.cinestream.galleryplayer.ui.theme.*
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

/**
 * Universal DRM Callback supporting:
 * 1. Offline / inline hex ClearKey ("kid:k") -> instant W3C ClearKey JSON conversion
 * 2. Remote ClearKey license APIs (e.g. JioTV PHP key scripts) with header forwarding
 * 3. Standard Widevine and PlayReady license servers
 */
@UnstableApi
class UniversalDrmCallback(
    private val drmType: DrmType,
    private val licenseKeyOrUrl: String,
    private val headers: Map<String, String>
) : MediaDrmCallback {

    override fun executeProvisionRequest(
        uuid: UUID,
        request: ExoMediaDrm.ProvisionRequest
    ): ByteArray {
        val url = request.defaultUrl + "&signedRequest=" + String(request.data, Charsets.UTF_8)
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            for ((k, v) in headers) {
                connection.setRequestProperty(k, v)
            }
            return connection.inputStream.readBytes()
        } finally {
            connection.disconnect()
        }
    }

    override fun executeKeyRequest(
        uuid: UUID,
        request: ExoMediaDrm.KeyRequest
    ): ByteArray {
        val trimmedKey = licenseKeyOrUrl.trim()

        // 1. Hex ClearKey (kid:key) -> immediate W3C JSON return, zero network latency!
        if (trimmedKey.contains(":") && !trimmedKey.startsWith("http", ignoreCase = true)) {
            val clearKeyJson = StreamConfig.buildClearKeyJson(trimmedKey)
            if (clearKeyJson.isNotBlank()) {
                return clearKeyJson.toByteArray(Charsets.UTF_8)
            }
        }

        // 2. Already W3C JSON
        if (trimmedKey.startsWith("{") && trimmedKey.endsWith("}")) {
            return trimmedKey.toByteArray(Charsets.UTF_8)
        }

        // 3. Remote HTTP(S) License Server
        if (trimmedKey.startsWith("http://", ignoreCase = true) || trimmedKey.startsWith("https://", ignoreCase = true)) {
            val isClearKey = (uuid == C.CLEARKEY_UUID || drmType == DrmType.CLEARKEY)
            return fetchRemoteKey(trimmedKey, request.data, headers, isClearKey)
        }

        return ByteArray(0)
    }

    private fun fetchRemoteKey(
        targetUrl: String,
        bodyData: ByteArray,
        reqHeaders: Map<String, String>,
        isClearKey: Boolean
    ): ByteArray {
        val hasQueryParams = targetUrl.contains("?")
        val preferGet = isClearKey && hasQueryParams

        if (preferGet) {
            try {
                val resp = executeHttp(targetUrl, "GET", null, reqHeaders)
                val processed = formatClearKeyIfPossible(resp)
                if (processed.isNotEmpty()) return processed
            } catch (_: Exception) {}
        }

        try {
            val resp = executeHttp(targetUrl, "POST", bodyData, reqHeaders)
            if (isClearKey) {
                val processed = formatClearKeyIfPossible(resp)
                if (processed.isNotEmpty()) return processed
            }
            return resp
        } catch (e: Exception) {
            if (!preferGet) {
                val resp = executeHttp(targetUrl, "GET", null, reqHeaders)
                if (isClearKey) {
                    val processed = formatClearKeyIfPossible(resp)
                    if (processed.isNotEmpty()) return processed
                }
                return resp
            }
            throw e
        }
    }

    private fun formatClearKeyIfPossible(data: ByteArray): ByteArray {
        val text = String(data, Charsets.UTF_8).trim()
        if (text.isBlank()) return data
        if (text.contains(":") && !text.startsWith("{")) {
            val json = StreamConfig.buildClearKeyJson(text)
            if (json.isNotBlank()) return json.toByteArray(Charsets.UTF_8)
        }
        return data
    }

    private fun executeHttp(
        urlString: String,
        method: String,
        body: ByteArray?,
        reqHeaders: Map<String, String>
    ): ByteArray {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            for ((k, v) in reqHeaders) {
                conn.setRequestProperty(k, v)
            }
            if (method == "POST" && body != null && body.isNotEmpty()) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
            return stream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}

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
                controller.hide(WindowInsetsCompat.Type.statusBars())
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
    var showStreamSettingsDialog by remember { mutableStateOf(false) }
    var streamSettingsSelectedTab by remember { mutableIntStateOf(0) }
    var isFullscreenLandscape by remember { mutableStateOf(false) }
    var trackSelectionVersion by remember { mutableIntStateOf(0) }

    // Resolve URL with pipe parameters dynamically
    val effectiveConfig = remember(streamConfig) {
        val parsed = if (streamConfig.url.contains("|")) {
            StreamConfig.parse(streamConfig.url, streamConfig.title)
        } else null

        if (parsed != null) {
            parsed.copy(
                cookie = if (streamConfig.cookie.isNotBlank()) streamConfig.cookie else parsed.cookie,
                referer = if (streamConfig.referer.isNotBlank()) streamConfig.referer else parsed.referer,
                origin = if (streamConfig.origin.isNotBlank()) streamConfig.origin else parsed.origin,
                userAgent = if (streamConfig.userAgent.isNotBlank() && !streamConfig.userAgent.startsWith("Mozilla")) streamConfig.userAgent else parsed.userAgent,
                drmLicenseUrl = if (streamConfig.drmLicenseUrl.isNotBlank()) streamConfig.drmLicenseUrl else parsed.drmLicenseUrl,
                drmType = if (streamConfig.drmType != DrmType.NONE) streamConfig.drmType else parsed.drmType,
                format = if (streamConfig.format != StreamFormat.AUTO) streamConfig.format else parsed.format,
                extraHeaders = streamConfig.extraHeaders + parsed.extraHeaders
            )
        } else {
            streamConfig
        }
    }

    // Initialize ExoPlayer with maximum stream compatibility
    val exoPlayer = remember(effectiveConfig) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
            .setUserAgent(
                effectiveConfig.userAgent.ifBlank {
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }
            )

        val headers = effectiveConfig.toHeaderMap()
        if (headers.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(headers)
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(effectiveConfig.url))

        // Set MIME type
        val mimeType = effectiveConfig.effectiveMimeType()
        if (mimeType != null) {
            mediaItemBuilder.setMimeType(mimeType)
        }

        // Attach DRM Configuration & Custom Session Manager
        val hasDrm = effectiveConfig.drmType != DrmType.NONE || effectiveConfig.drmLicenseUrl.isNotBlank()
        if (hasDrm) {
            val drmUuid = effectiveConfig.drmType.uuid ?: (if (effectiveConfig.isClearKeyHex()) C.CLEARKEY_UUID else C.WIDEVINE_UUID)
            val drmCallback = UniversalDrmCallback(
                drmType = effectiveConfig.drmType,
                licenseKeyOrUrl = effectiveConfig.drmLicenseUrl,
                headers = headers
            )
            val drmSessionManager = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(drmUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(true)
                .build(drmCallback)

            mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }

            val drmConfigBuilder = MediaItem.DrmConfiguration.Builder(drmUuid)
            if (effectiveConfig.drmLicenseUrl.startsWith("http://", ignoreCase = true) ||
                effectiveConfig.drmLicenseUrl.startsWith("https://", ignoreCase = true)) {
                drmConfigBuilder.setLicenseUri(effectiveConfig.drmLicenseUrl)
            }
            drmConfigBuilder.setLicenseRequestHeaders(headers)
            mediaItemBuilder.setDrmConfiguration(drmConfigBuilder.build())
        }

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

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
                        val displayTitle = remember(effectiveConfig) {
                            if (effectiveConfig.title.isNotBlank() && effectiveConfig.title != "Live Stream" && effectiveConfig.title != "Cine Stream" && effectiveConfig.title != "JD Stream") {
                                effectiveConfig.title
                            } else {
                                effectiveConfig.url.substringAfterLast("/").substringBefore("?").ifBlank { "index.mpd" }
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

                    // Right items: [ HD ] [ AUDIO ] [ 🔒 ]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // [ HD ] Quality Quick Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NavySurface.copy(alpha = 0.85f))
                                .clickable {
                                    streamSettingsSelectedTab = 0
                                    showStreamSettingsDialog = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "HD",
                                color = CyanAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // [ AUDIO ] Language Quick Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NavySurface.copy(alpha = 0.85f))
                                .clickable {
                                    streamSettingsSelectedTab = 1
                                    showStreamSettingsDialog = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Audio Language",
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
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

                        // Right Action Buttons Row matching Photo 2:
                        // [ 🔊 ]  [ 🎦⚙ ]  [ 🗖 ]  [ ⛶ ]  [ › ]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. [ 🔊 / 🔇 ] Volume / Mute (Photo 2)
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Audio / Mute",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        isMuted = !isMuted
                                        exoPlayer.volume = if (isMuted) 0f else 1f
                                    }
                            )

                            // 2. [ 🎦⚙ ] Video & Stream Settings (Photo 2 - Quality, Language, Speed, Aspect)
                            Icon(
                                imageVector = Icons.Default.VideoSettings,
                                contentDescription = "Video Settings",
                                tint = CyanAccent,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        streamSettingsSelectedTab = 0
                                        showStreamSettingsDialog = true
                                    }
                            )

                            // 3. [ 🗖 ] Picture in Picture (Photo 2)
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
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

                            // 4. [ ⛶ ] Fullscreen / Rotate Screen (Photo 2)
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Rotate",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        isFullscreenLandscape = !isFullscreenLandscape
                                        activity?.requestedOrientation = if (isFullscreenLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                    }
                            )

                            // 5. [ › ] Quick Actions Menu (Photo 2)
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "More",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        showStreamSettingsDialog = true
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    // Comprehensive Stream & Video Controls Dialog (Quality, Audio Language, Speed, Aspect Ratio)
    if (showStreamSettingsDialog) {
        Dialog(onDismissRequest = { showStreamSettingsDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header: Title + Close Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoSettings,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Stream & Video Controls",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { showStreamSettingsDialog = false }
                        )
                    }

                    // 4 Clean Filter Chips / Tabs: [ Quality ] [ Audio ] [ Speed ] [ Aspect ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Quality", "Audio", "Speed", "Aspect").forEachIndexed { index, tabName ->
                            val isSelected = streamSettingsSelectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) CyanAccent else NavyCard)
                                    .clickable { streamSettingsSelectedTab = index }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabName,
                                    color = if (isSelected) NavyBackground else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = NavyBorder, thickness = 1.dp)

                    // Tab Content
                    when (streamSettingsSelectedTab) {
                        0 -> {
                            // QUALITY TAB
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Select Resolution",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.heightIn(max = 260.dp)
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
                                                    showStreamSettingsDialog = false
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = option.label,
                                                color = if (option.isSelected) CyanAccent else TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = if (option.isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (option.isSelected) {
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
                        1 -> {
                            // AUDIO TAB
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Select Audio Track",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.heightIn(max = 260.dp)
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
                                                    showStreamSettingsDialog = false
                                                    Toast.makeText(context, "Audio: ${option.label}", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = option.label,
                                                color = if (option.isSelected) CyanAccent else TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = if (option.isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (option.isSelected) {
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
                        2 -> {
                            // SPEED TAB
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Playback Speed",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.heightIn(max = 260.dp)
                                ) {
                                    items(speeds) { speed ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (playbackSpeed == speed) TealPill else NavyCard)
                                                .clickable {
                                                    playbackSpeed = speed
                                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                                    showStreamSettingsDialog = false
                                                    Toast.makeText(context, "Speed: ${speed}x", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
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
                        3 -> {
                            // ASPECT RATIO TAB
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Screen Aspect Ratio",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.heightIn(max = 260.dp)
                                ) {
                                    items(PlayerResizeMode.entries) { mode ->
                                        val isSelected = currentResizeMode == mode
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) TealPill else NavyCard)
                                                .clickable {
                                                    currentResizeMode = mode
                                                    showStreamSettingsDialog = false
                                                    Toast.makeText(context, "Aspect: ${mode.displayName}", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = mode.displayName,
                                                color = if (isSelected) CyanAccent else TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (isSelected) {
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
