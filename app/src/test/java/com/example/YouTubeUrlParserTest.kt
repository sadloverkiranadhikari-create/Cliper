package com.example

import com.example.data.remote.YouTubeUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun parse_standardWatchUrl_returnsCanonicalInfo() {
        val parsed = YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s")
        assertNotNull(parsed)
        assertEquals("dQw4w9WgXcQ", parsed?.videoId)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", parsed?.canonicalUrl)
    }

    @Test
    fun parse_shortAndShortsUrls_extractVideoId() {
        val shortUrl = YouTubeUrlParser.parse("youtu.be/aqz-KE-bpKQ")
        assertEquals("aqz-KE-bpKQ", shortUrl?.videoId)

        val shortsUrl = YouTubeUrlParser.parse("https://youtube.com/shorts/9bZkp7q19f0?feature=share")
        assertEquals("9bZkp7q19f0", shortsUrl?.videoId)
    }

    @Test
    fun parse_invalidUrl_returnsNull() {
        assertNull(YouTubeUrlParser.parse("https://example.com/not-youtube"))
        assertNull(YouTubeUrlParser.parse("   "))
    }

    @Test
    fun formatTimestamp_formatsMinutesAndHours() {
        assertEquals("01:15", YouTubeUrlParser.formatTimestamp(75))
        assertEquals("1:01:05", YouTubeUrlParser.formatTimestamp(3665))
        assertEquals("00:01:15,000", YouTubeUrlParser.formatSrtTimestamp(75))
    }
}
