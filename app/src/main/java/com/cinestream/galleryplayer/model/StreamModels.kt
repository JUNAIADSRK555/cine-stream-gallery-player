package com.cinestream.galleryplayer.model

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import java.util.UUID

/**
 * Supported stream formats for explicit MIME type overrides.
 */
enum class StreamFormat(val displayName: String, val mimeType: String?) {
    AUTO("Default", null),
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
    CLEARKEY("ClearKey", C.CLEARKEY_UUID),
    WIDEVINE("Widevine", C.WIDEVINE_UUID),
    PLAYREADY("PlayReady", C.PLAYREADY_UUID);

    companion object {
        fun fromName(name: String?): DrmType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NONE
        }
    }
}

const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

/**
 * Stream configuration data class holding all connection & security parameters.
 */
data class StreamConfig(
    val url: String,
    val title: String = "Live Stream",
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val userAgent: String = DEFAULT_USER_AGENT,
    val drmLicenseUrl: String = "",
    val drmType: DrmType = DrmType.NONE,
    val format: StreamFormat = StreamFormat.AUTO,
    val extraHeaders: Map<String, String> = emptyMap()
) {
    fun toHeaderMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val effectiveUA = userAgent.ifBlank { DEFAULT_USER_AGENT }.trim()
        map["User-Agent"] = effectiveUA
        if (cookie.isNotBlank()) map["Cookie"] = cookie.trim()
        if (referer.isNotBlank()) map["Referer"] = referer.trim()
        if (origin.isNotBlank()) map["Origin"] = origin.trim()
        for ((k, v) in extraHeaders) {
            if (v.isNotBlank()) map[k] = v.trim()
        }
        return map
    }

    fun effectiveMimeType(): String? {
        if (format != StreamFormat.AUTO && format.mimeType != null) {
            return format.mimeType
        }
        val lower = url.lowercase().substringBefore("?")
        return when {
            lower.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            lower.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            lower.endsWith(".ism") || lower.endsWith(".isml/manifest") -> MimeTypes.APPLICATION_SS
            lower.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
            lower.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            else -> null
        }
    }

    /**
     * Checks if this stream config has ClearKey hex keys (e.g. kid:k format)
     */
    fun isClearKeyHex(): Boolean {
        return drmType == DrmType.CLEARKEY &&
                drmLicenseUrl.contains(":") &&
                !drmLicenseUrl.startsWith("http", ignoreCase = true)
    }

    companion object {
        /**
         * Universal parser for any stream URL that uses the standard pipe '|' format:
         * URL|param1=val1&param2=val2...
         * Supports:
         * - drmScheme / license_type (clearkey, widevine, playready)
         * - drmLicense / license_key (hex kid:k or https:// license server)
         * - Cookie, User-Agent, Referer, Origin, and arbitrary custom HTTP headers
         */
        fun parse(rawInput: String, defaultTitle: String = "Live Stream"): StreamConfig {
            val trimmed = rawInput.trim()
            if (!trimmed.contains("|")) {
                val cleanUrl = if (trimmed.endsWith("?") && !trimmed.dropLast(1).contains("?")) trimmed.dropLast(1) else trimmed.trimEnd('&')
                return StreamConfig(
                    url = cleanUrl,
                    title = defaultTitle,
                    drmType = DrmType.NONE
                )
            }

            val pipeParts = trimmed.split("|")
            val rawUrl = pipeParts[0].trim()
            val cleanUrl = if (rawUrl.endsWith("?") && !rawUrl.dropLast(1).contains("?")) rawUrl.dropLast(1) else rawUrl.trimEnd('&')
            val paramString = pipeParts.drop(1).joinToString("&")

            var cookie = ""
            var userAgent = DEFAULT_USER_AGENT
            var referer = ""
            var origin = ""
            var drmLicense = ""
            var drmType = DrmType.NONE
            val extraHeaders = mutableMapOf<String, String>()

            val pairs = paramString.split(Regex("[&\\n]+"))
            for (pair in pairs) {
                if (!pair.contains("=")) continue
                val kv = pair.split("=", limit = 2)
                val k = kv[0].trim()
                val v = kv[1].trim()
                val kLower = k.lowercase()

                when {
                    kLower == "cookie" -> cookie = v
                    kLower in listOf("user-agent", "user_agent", "useragent", "http-user-agent") -> userAgent = v
                    kLower in listOf("referer", "referrer", "http-referer", "http-referrer") -> referer = v
                    kLower in listOf("origin", "http-origin") -> origin = v
                    kLower in listOf("drmscheme", "drm_scheme", "license_type", "licensetype", "scheme", "type") -> {
                        drmType = when {
                            v.contains("clearkey", ignoreCase = true) -> DrmType.CLEARKEY
                            v.contains("widevine", ignoreCase = true) -> DrmType.WIDEVINE
                            v.contains("playready", ignoreCase = true) -> DrmType.PLAYREADY
                            else -> DrmType.NONE
                        }
                    }
                    kLower in listOf("drmlicense", "drm_license", "license_key", "licensekey", "license_url", "key", "drmkey") -> {
                        drmLicense = v
                    }
                    else -> {
                        extraHeaders[k] = v
                    }
                }
            }

            // Only attach DRM if drmLicense is present or type explicitly set
            if (drmType == DrmType.NONE && drmLicense.isNotBlank()) {
                drmType = if (drmLicense.contains(":") && !drmLicense.startsWith("http", ignoreCase = true)) {
                    DrmType.CLEARKEY
                } else {
                    DrmType.WIDEVINE
                }
            }

            return StreamConfig(
                url = cleanUrl,
                title = defaultTitle,
                cookie = cookie,
                referer = referer,
                origin = origin,
                userAgent = userAgent.ifBlank { DEFAULT_USER_AGENT },
                drmLicenseUrl = drmLicense,
                drmType = drmType,
                format = StreamFormat.AUTO,
                extraHeaders = extraHeaders
            )
        }

        /**
         * Converts hex key pairs into W3C ClearKey JSON:
         * Input: "f3df7843080ae743bf865dc5fdf64c68:567c863bc12eb74788ea74888c042e1b"
         * Output: {"keys":[{"kty":"oct","k":"...","kid":"..."}],"type":"temporary"}
         */
        fun buildClearKeyJson(hexInput: String): String {
            val trimmed = hexInput.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                return trimmed
            }

            val keysList = mutableListOf<String>()
            val pairs = trimmed.split(Regex("[,;\\s]+")).filter { it.contains(":") }

            for (pair in pairs) {
                val parts = pair.split(":")
                if (parts.size >= 2) {
                    val kidHex = parts[0].trim()
                    val kHex = parts[1].trim()
                    val kidB64 = hexToBase64Url(kidHex)
                    val kB64 = hexToBase64Url(kHex)
                    if (kidB64.isNotBlank() && kB64.isNotBlank()) {
                        keysList.add(
                            """{"kty":"oct","k":"$kB64","kid":"$kidB64"}"""
                        )
                    }
                }
            }

            if (keysList.isEmpty()) return ""
            return """{"keys":[${keysList.joinToString(",")}],"type":"temporary"}"""
        }

        private fun hexToBase64Url(hex: String): String {
            val clean = hex.replace("-", "").replace(" ", "").trim()
            if (clean.length % 2 != 0) return ""
            return try {
                val bytes = ByteArray(clean.length / 2)
                for (i in bytes.indices) {
                    val index = i * 2
                    bytes[i] = clean.substring(index, index + 2).toInt(16).toByte()
                }
                android.util.Base64.encodeToString(
                    bytes,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                ).trim()
            } catch (e: Exception) {
                ""
            }
        }
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
            if (durationMs <= 0) return "00:00"
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
                String.format("%.1f GB", mb / 1024)
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

/**
 * Represents a saved M3U playlist for Multi-M3U support.
 */
data class M3uPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sourceUrl: String,
    val userAgentPreset: String = "Default",
    val customUserAgent: String = "",
    val channels: List<M3uChannel> = emptyList(),
    val dateAdded: Long = System.currentTimeMillis()
) {
    val channelCount: Int get() = channels.size

    fun getEffectiveUserAgent(): String {
        return when (userAgentPreset) {
            "Chrome(Android)" -> "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            "Chrome(PC)" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            "IE(PC)" -> "Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko"
            "Firefox(PC)" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
            "iPhone" -> "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
            "Nokia" -> "NokiaN97/21.0.045 (SymbianOS/9.4; Series60/5.0 Mozilla/5.0; Profile/MIDP-2.1 Configuration/CLDC-1.1) AppleWebkit/525 (KHTML, like Gecko) BrowserNG/7.1.4"
            "Custom" -> customUserAgent.ifBlank { DEFAULT_USER_AGENT }
            else -> DEFAULT_USER_AGENT
        }
    }
}
