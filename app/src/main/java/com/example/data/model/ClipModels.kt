package com.example.data.model

enum class ClipFocus(val label: String, val aiInstruction: String) {
    VIRAL_HOOKS(
        label = "Viral Hooks",
        aiInstruction = "Prioritize bold opening statements, surprising twists, high-energy moments, and self-contained hooks ideal for Shorts/Reels."
    ),
    KEY_INSIGHTS(
        label = "Key Insights",
        aiInstruction = "Prioritize the sharpest takeaways, memorable explanations, frameworks, and high-value insights."
    ),
    FUNNY_MOMENTS(
        label = "Funny Moments",
        aiInstruction = "Prioritize comedic timing, witty reactions, banter, and entertaining stories."
    ),
    TUTORIAL_STEPS(
        label = "Tutorial Steps",
        aiInstruction = "Prioritize clear step-by-step demonstrations, actionable tips, and concise how-to segments."
    )
}

enum class DurationPreset(val label: String, val minSeconds: Int, val maxSeconds: Int) {
    SHORT("15–30s", 15, 30),
    MEDIUM("30–60s", 30, 60),
    LONG("60–90s", 60, 90)
}

enum class ManifestFormat(val extension: String, val mimeType: String, val label: String) {
    SRT("srt", "application/x-subrip", "SRT Chapters"),
    JSON("json", "application/json", "JSON Timestamps")
}

data class YouTubeParsedLink(
    val videoId: String,
    val canonicalUrl: String,
    val thumbnailUrl: String
)

data class GeneratedClipCandidate(
    val title: String,
    val startSeconds: Int,
    val endSeconds: Int,
    val viralScore: Int,
    val hookLine: String,
    val summary: String,
    val suggestedCaption: String,
    val hashtags: List<String>
)

data class AiClipAnalysisResult(
    val videoSummary: String,
    val clips: List<GeneratedClipCandidate>
)
