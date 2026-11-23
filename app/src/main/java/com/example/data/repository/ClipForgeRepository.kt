package com.example.data.repository

import android.net.Uri
import com.example.data.ai.YouTubeClipAiService
import com.example.data.local.ClipForgeDao
import com.example.data.local.ClipProjectEntity
import com.example.data.local.ExportedClipWithProject
import com.example.data.local.ProjectWithClips
import com.example.data.local.VideoClipEntity
import com.example.data.media.ClipExportEvent
import com.example.data.media.VideoClipExporter
import com.example.data.model.ClipFocus
import com.example.data.model.DurationPreset
import com.example.data.model.ManifestFormat
import com.example.data.remote.YouTubeMetadataClient
import com.example.data.remote.YouTubeUrlParser
import kotlinx.coroutines.flow.Flow

class ClipForgeRepository(
    private val dao: ClipForgeDao,
    private val metadataClient: YouTubeMetadataClient,
    private val aiService: YouTubeClipAiService,
    private val exporter: VideoClipExporter
) {
    val allProjects: Flow<List<ProjectWithClips>> = dao.getAllProjectsWithClips()
    val exportedClips: Flow<List<ExportedClipWithProject>> = dao.getExportedClips()

    fun observeProject(projectId: Long): Flow<ProjectWithClips?> =
        dao.getProjectWithClips(projectId)

    suspend fun analyzeAndSaveProject(
        rawUrl: String,
        focus: ClipFocus,
        durationPreset: DurationPreset,
        onStageChanged: (AnalysisStage) -> Unit
    ): Long {
        val parsed = YouTubeUrlParser.parse(rawUrl)
            ?: throw IllegalArgumentException("Please enter a valid YouTube video or Shorts URL.")

        onStageChanged(AnalysisStage.FETCHING_METADATA)
        val oEmbed = metadataClient.fetchMetadata(parsed.canonicalUrl).getOrNull()
        val videoTitle = oEmbed?.title?.takeIf { it.isNotBlank() }
            ?: "YouTube Video (${parsed.videoId})"
        val channelName = oEmbed?.authorName?.takeIf { it.isNotBlank() }
            ?: "YouTube Creator"
        val thumbnailUrl = oEmbed?.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: parsed.thumbnailUrl

        onStageChanged(AnalysisStage.ANALYZING_AI)
        val analysis = aiService.analyzeYouTubeVideo(
            canonicalUrl = parsed.canonicalUrl,
            videoTitle = oEmbed?.title,
            channelName = oEmbed?.authorName,
            focus = focus,
            durationPreset = durationPreset
        )

        val projectId = dao.insertProject(
            ClipProjectEntity(
                youtubeUrl = parsed.canonicalUrl,
                videoId = parsed.videoId,
                title = videoTitle,
                channelName = channelName,
                thumbnailUrl = thumbnailUrl,
                videoSummary = analysis.videoSummary,
                clipFocus = focus.label,
                targetDurationRange = durationPreset.label
            )
        )

        val clipEntities = analysis.clips.map { candidate ->
            VideoClipEntity(
                projectId = projectId,
                title = candidate.title,
                startSeconds = candidate.startSeconds,
                endSeconds = candidate.endSeconds,
                viralScore = candidate.viralScore,
                hookLine = candidate.hookLine,
                summary = candidate.summary,
                suggestedCaption = candidate.suggestedCaption,
                hashtagsCsv = candidate.hashtags.joinToString(" ")
            )
        }
        dao.insertClips(clipEntities)
        return projectId
    }

    suspend fun attachSourceMedia(projectId: Long, uri: String, label: String) {
        dao.updateAttachedSource(projectId, uri, label)
    }

    suspend fun updateClipRange(clipId: Long, startSeconds: Int, endSeconds: Int) {
        val safeStart = startSeconds.coerceAtLeast(0)
        val safeEnd = endSeconds.coerceAtLeast(safeStart + 1)
        dao.updateClipRange(clipId, safeStart, safeEnd)
    }

    suspend fun exportClipMp4(
        sourceUri: Uri,
        clip: VideoClipEntity,
        onProgress: (ClipExportEvent) -> Unit
    ): ClipExportEvent.Completed {
        val result = exporter.exportTrimmedMp4(
            sourceUri = sourceUri,
            clipTitle = clip.title,
            startSeconds = clip.startSeconds,
            endSeconds = clip.endSeconds,
            onProgress = onProgress
        )
        dao.markClipExported(
            clipId = clip.id,
            uri = result.contentUri.toString(),
            fileName = result.fileName,
            sizeBytes = result.fileSizeBytes,
            exportedAt = System.currentTimeMillis()
        )
        return result
    }

    suspend fun downloadManifest(
        projectId: Long,
        format: ManifestFormat
    ): Pair<Uri, String> {
        val data = dao.getProjectWithClipsOnce(projectId)
            ?: throw IllegalStateException("Project not found.")
        return exporter.exportManifestToDownloads(data.project, data.clips, format)
    }

    suspend fun deleteProject(projectId: Long) {
        dao.deleteProjectById(projectId)
    }
}

enum class AnalysisStage {
    FETCHING_METADATA,
    ANALYZING_AI
}
