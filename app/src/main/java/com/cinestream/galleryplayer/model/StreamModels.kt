package com.cinestream.galleryplayer.model

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import java.util.UUID

/**
 * Supported stream formats for explicit MIME type overrides.
 */
enum class StreamFormat(val displayName: String, val mimeType: String?) {
    AUTO("Auto (Detect)", null),
    HLS("HLS (.m3u8)", MimeTypes.APPLICATION_M3U8),
    DASH("DASH (.mpd)", MimeTypes.APPLICATION_MPD),
    SMOOTH_STREAMING("SmoothStreaming (.ism)", MimeTypes.APPLICATION_SS),
    PROGRESSIVE("Progressive / MP4", MimeTypes.VIDEO_MP4);

    companion object {
        fun fromMimeType(mime: String?): StreamFormat {
            return entries.firstOrNull { it.mimeType == mime } ?: AUTO
        }
    }
}

/**
 * Supported DRM encryption schemes.
 */
enum class DrmType(val displayName: String, val uuid: UUID?) {
    NONE("None", null),
    WIDEVINE("Widevine", C.WIDEVINE_UUID),
    CLEARKEY("ClearKey", C.CLEARKEY_UUID),
    PLAYREADY("PlayReady", C.PLAYREADY_UUID);

    companion object {
        fun fromName(name: String?): DrmType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NONE
        }
    }
}

/**
 * Stream configuration data class holding all connection & security parameters.
 */
data class StreamConfig(
    val url: String,
    val title: String = "Live Stream",
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val userAgent: String = "CineStreamPlayer/1.0",
    val drmLicenseUrl: String = "",
    val drmType: DrmType = DrmType.NONE,
    val format: StreamFormat = StreamFormat.AUTO,
    val extraHeaders: Map<String, String> = emptyMap()
) {
    fun toHeaderMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (cookie.isNotBlank()) map["Cookie"] = cookie.trim()
        if (referer.isNotBlank()) map["Referer"] = referer.trim()
        if (origin.isNotBlank()) map["Origin"] = origin.trim()
        if (userAgent.isNotBlank()) map["User-Agent"] = userAgent.trim()
        map.putAll(extraHeaders)
        return map
    }
}

/**
 * Local video file representation from MediaStore or SAF.
 */
data class LocalVideoItem(
    val id: Long,
    val contentUri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val resolution: String = "",
    val path: String = ""
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 MB"
            val mb = sizeBytes.toDouble() / (1024 * 1024)
            return if (mb >= 1024) {
                String.format("%.2f GB", mb / 1024)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}

/**
 * M3U Channel entry parsed from an IPTV/VOD playlist.
 */
data class M3uChannel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val group: String = "General",
    val headers: Map<String, String> = emptyMap(),
    val drmLicenseUrl: String? = null,
    val drmType: DrmType = DrmType.NONE
)
