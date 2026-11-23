package com.example.data.ai

import android.util.Log
import com.example.data.model.AiClipAnalysisResult
import com.example.data.model.ClipFocus
import com.example.data.model.DurationPreset
import com.example.data.model.GeneratedClipCandidate
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "YouTubeClipAiService"

class YouTubeClipAiService {

    private val clipItemSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Punchy clip title under 8 words"),
            "startSeconds" to Schema.integer(description = "Clip start timestamp in whole seconds"),
            "endSeconds" to Schema.integer(description = "Clip end timestamp in whole seconds, strictly greater than startSeconds"),
            "viralScore" to Schema.integer(description = "Engagement potential score between 1 and 100"),
            "hookLine" to Schema.string(description = "Opening hook quote or attention-grabbing premise"),
            "summary" to Schema.string(description = "1-2 sentence explanation of why this moment works as a standalone clip"),
            "suggestedCaption" to Schema.string(description = "Ready-to-post short-form social caption"),
            "hashtags" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 concise hashtags without spaces"
            )
        )
    )

    private val clipAnalysisSchema = Schema.obj(
        properties = mapOf(
            "videoSummary" to Schema.string(description = "Brief 1-2 sentence summary of the full video"),
            "clips" to Schema.array(
                items = clipItemSchema,
                description = "4 to 6 standout highlight clips ordered from highest viralScore to lowest"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = clipAnalysisSchema
            }
        )
    }

    suspend fun analyzeYouTubeVideo(
        canonicalUrl: String,
        videoTitle: String?,
        channelName: String?,
        focus: ClipFocus,
        durationPreset: DurationPreset
    ): AiClipAnalysisResult {
        val contextLines = buildString {
            appendLine("You are an expert short-form video editor and producer.")
            appendLine("Watch and analyze the attached YouTube video directly to identify the 4 to 6 strongest standalone clip segments.")
            if (!videoTitle.isNullOrBlank()) {
                appendLine("Video Title: $videoTitle")
            }
            if (!channelName.isNullOrBlank()) {
                appendLine("Channel: $channelName")
            }
            appendLine("Editorial Focus: ${focus.label} — ${focus.aiInstruction}")
            appendLine(
                "Target Clip Duration: between ${durationPreset.minSeconds} and ${durationPreset.maxSeconds} seconds each (or shorter if the video itself is shorter)."
            )
            appendLine("Rules:")
            appendLine("- Every clip MUST have startSeconds >= 0 and endSeconds > startSeconds within the actual video duration.")
            appendLine("- Choose clean conversational boundaries (avoid cutting mid-sentence).")
            appendLine("- Order clips by viralScore descending.")
        }

        val response = try {
            withTimeout(60_000L) {
                model.generateContent(
                    content {
                        fileData(uri = canonicalUrl, mimeType = "video/mp4")
                        text(contextLines)
                    }
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Video clip analysis timed out", e)
            throw IllegalStateException("AI video analysis timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI video analysis failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to analyze YouTube video with Gemini AI.")
        }

        val rawJson = response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini AI returned an empty response.")

        return parseAnalysisJson(rawJson)
    }

    private fun parseAnalysisJson(rawJson: String): AiClipAnalysisResult {
        val root = JSONObject(rawJson)
        val videoSummary = root.optString("videoSummary", "").trim()
        val clipsArray = root.optJSONArray("clips")
            ?: throw IllegalStateException("AI response did not include any clips.")

        val candidates = mutableListOf<GeneratedClipCandidate>()
        for (i in 0 until clipsArray.length()) {
            val item = clipsArray.optJSONObject(i) ?: continue
            val rawStart = item.optInt("startSeconds", 0).coerceAtLeast(0)
            val rawEnd = item.optInt("endSeconds", rawStart + 15)
            val safeEnd = if (rawEnd > rawStart) rawEnd else rawStart + 15
            val viralScore = item.optInt("viralScore", 80).coerceIn(1, 100)

            val tagsArray = item.optJSONArray("hashtags")
            val tags = buildList {
                if (tagsArray != null) {
                    for (j in 0 until tagsArray.length()) {
                        val rawTag = tagsArray.optString(j).trim()
                        if (rawTag.isNotEmpty()) {
                            add(if (rawTag.startsWith("#")) rawTag else "#$rawTag")
                        }
                    }
                }
            }

            candidates.add(
                GeneratedClipCandidate(
                    title = item.optString("title", "Highlight ${i + 1}").trim(),
                    startSeconds = rawStart,
                    endSeconds = safeEnd,
                    viralScore = viralScore,
                    hookLine = item.optString("hookLine", "").trim(),
                    summary = item.optString("summary", "").trim(),
                    suggestedCaption = item.optString("suggestedCaption", "").trim(),
                    hashtags = tags
                )
            )
        }

        if (candidates.isEmpty()) {
            throw IllegalStateException("No valid highlight clips were returned for this video.")
        }

        return AiClipAnalysisResult(
            videoSummary = videoSummary,
            clips = candidates.sortedByDescending { it.viralScore }
        )
    }
}
