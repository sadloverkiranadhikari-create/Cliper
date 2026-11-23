package com.example.data.remote

import com.example.data.model.YouTubeParsedLink
import java.net.URI

object YouTubeUrlParser {
    private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
    private val FALLBACK_ID_REGEX = Regex("(?:v=|youtu\\.be/|shorts/|live/|embed/)([A-Za-z0-9_-]{11})")

    fun parse(rawInput: String): YouTubeParsedLink? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val normalizedInput = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val videoId = extractVideoId(normalizedInput) ?: return null
        return YouTubeParsedLink(
            videoId = videoId,
            canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        )
    }

    private fun extractVideoId(url: String): String? {
        try {
            val uri = URI(url)
            val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: ""
            val pathSegments = uri.path?.split("/")?.filter { it.isNotBlank() }.orEmpty()

            if (host == "youtu.be" && pathSegments.isNotEmpty()) {
                val candidate = pathSegments.first()
                if (VIDEO_ID_REGEX.matches(candidate)) return candidate
            }

            if (host == "youtube.com" || host == "music.youtube.com" || host == "youtube-nocookie.com") {
                val queryMap = uri.rawQuery.orEmpty()
                    .split("&")
                    .mapNotNull { param ->
                        val parts = param.split("=", limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else null
                    }
                    .toMap()

                val vParam = queryMap["v"]
                if (vParam != null && VIDEO_ID_REGEX.matches(vParam)) return vParam

                if (pathSegments.size >= 2 && pathSegments[0] in setOf("shorts", "live", "embed", "v")) {
                    val candidate = pathSegments[1]
                    if (VIDEO_ID_REGEX.matches(candidate)) return candidate
                }
            }
        } catch (_: Exception) {
            // Fall through to regex fallback
        }

        val match = FALLBACK_ID_REGEX.find(url)
        return match?.groupValues?.getOrNull(1)?.takeIf { VIDEO_ID_REGEX.matches(it) }
    }

    fun formatTimestamp(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun formatSrtTimestamp(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return String.format("%02d:%02d:%02d,000", hours, minutes, seconds)
    }

    fun buildTimestampJumpUrl(videoId: String, startSeconds: Int): String {
        val safeStart = startSeconds.coerceAtLeast(0)
        return "https://youtu.be/$videoId?t=$safeStart"
    }
}
