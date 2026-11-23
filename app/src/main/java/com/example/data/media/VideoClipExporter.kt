package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.data.local.ClipProjectEntity
import com.example.data.local.VideoClipEntity
import com.example.data.model.ManifestFormat
import com.example.data.remote.YouTubeUrlParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ClipExportEvent {
    data object Preparing : ClipExportEvent
    data class Processing(val progressPercent: Int) : ClipExportEvent
    data object SavingToDevice : ClipExportEvent
    data class Completed(
        val contentUri: Uri,
        val fileName: String,
        val fileSizeBytes: Long
    ) : ClipExportEvent
}

class VideoClipExporter(private val context: Context) {

    @OptIn(UnstableApi::class)
    suspend fun exportTrimmedMp4(
        sourceUri: Uri,
        clipTitle: String,
        startSeconds: Int,
        endSeconds: Int,
        onProgress: (ClipExportEvent) -> Unit
    ): ClipExportEvent.Completed {
        onProgress(ClipExportEvent.Preparing)

        val safeStartMs = startSeconds.coerceAtLeast(0) * 1000L
        val safeEndMs = endSeconds.coerceAtLeast(startSeconds + 1) * 1000L
        val sanitizedTitle = clipTitle
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "Clip" }
        val fileName = "ClipForge_${sanitizedTitle}_${startSeconds}s_${endSeconds}s.mp4"
        val tempFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")

        try {
            runTransformerOnMain(
                sourceUri = sourceUri,
                startMs = safeStartMs,
                endMs = safeEndMs,
                outputPath = tempFile.absolutePath,
                onPercent = { percent ->
                    onProgress(ClipExportEvent.Processing(percent))
                }
            )

            onProgress(ClipExportEvent.SavingToDevice)
            val savedUri = saveMp4ToMovies(tempFile = tempFile, displayName = fileName)
            val fileSize = tempFile.length()
            val completed = ClipExportEvent.Completed(
                contentUri = savedUri,
                fileName = fileName,
                fileSizeBytes = fileSize
            )
            onProgress(completed)
            return completed
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformerOnMain(
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        outputPath: String,
        onPercent: (Int) -> Unit
    ) = withContext(Dispatchers.Main.immediate) {
        val pollingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        suspendCancellableCoroutine<Unit> { continuation ->
            val mediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()

            val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

            val transformer = Transformer.Builder(context)
                .experimentalSetTrimOptimizationEnabled(true)
                .build()

            val progressJob = pollingScope.launch {
                val progressHolder = ProgressHolder()
                while (isActive) {
                    when (transformer.getProgress(progressHolder)) {
                        Transformer.PROGRESS_STATE_AVAILABLE -> {
                            onPercent(progressHolder.progress.coerceIn(0, 99))
                        }
                        Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY -> {
                            onPercent(5)
                        }
                        else -> Unit
                    }
                    delay(250L)
                }
            }

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    progressJob.cancel()
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exception: ExportException
                ) {
                    progressJob.cancel()
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException(
                                exception.localizedMessage
                                    ?: "Unable to clip this video source (${exception.errorCodeName})."
                            )
                        )
                    }
                }
            }

            transformer.addListener(listener)
            continuation.invokeOnCancellation {
                progressJob.cancel()
                transformer.cancel()
            }

            try {
                transformer.start(editedMediaItem, outputPath)
            } catch (e: CancellationException) {
                progressJob.cancel()
                throw e
            } catch (e: Exception) {
                progressJob.cancel()
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    private suspend fun saveMp4ToMovies(tempFile: File, displayName: String): Uri =
        withContext(Dispatchers.IO) {
            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalStateException("Rendered MP4 file is empty.")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/ClipForge"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val itemUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Failed to create MediaStore video entry.")

                try {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        FileInputStream(tempFile).use { input ->
                            input.copyTo(out)
                        }
                    } ?: throw IllegalStateException("Could not open MediaStore output stream.")

                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                    return@withContext itemUri
                } catch (e: Exception) {
                    resolver.delete(itemUri, null, null)
                    throw e
                }
            } else {
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: context.filesDir
                val targetDir = File(baseDir, "ClipForge").apply { mkdirs() }
                val destFile = File(targetDir, displayName)
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(destFile).use { out ->
                        input.copyTo(out)
                    }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                return@withContext Uri.fromFile(destFile)
            }
        }

    suspend fun exportManifestToDownloads(
        project: ClipProjectEntity,
        clips: List<VideoClipEntity>,
        format: ManifestFormat
    ): Pair<Uri, String> = withContext(Dispatchers.IO) {
        val safeTitle = project.title
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .take(32)
            .ifBlank { project.videoId }

        val fileName = "ClipForge_${safeTitle}.${format.extension}"
        val contentText = when (format) {
            ManifestFormat.SRT -> buildSrtContent(clips)
            ManifestFormat.JSON -> buildJsonContent(project, clips)
        }
        val bytes = contentText.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, format.mimeType)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/ClipForge"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Failed to create file in Downloads.")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                } ?: throw IllegalStateException("Failed to write to Downloads.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return@withContext uri to fileName
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "ClipForge"
            ).apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            return@withContext Uri.fromFile(file) to fileName
        }
    }

    private fun buildSrtContent(clips: List<VideoClipEntity>): String = buildString {
        clips.forEachIndexed { index, clip ->
            appendLine(index + 1)
            appendLine(
                "${YouTubeUrlParser.formatSrtTimestamp(clip.startSeconds)} --> ${
                    YouTubeUrlParser.formatSrtTimestamp(clip.endSeconds)
                }"
            )
            appendLine("${clip.title} (Hook Score: ${clip.viralScore}/100)")
            if (clip.hookLine.isNotBlank()) {
                appendLine(clip.hookLine)
            }
            appendLine()
        }
    }

    private fun buildJsonContent(
        project: ClipProjectEntity,
        clips: List<VideoClipEntity>
    ): String {
        val root = JSONObject().apply {
            put("title", project.title)
            put("channel", project.channelName)
            put("youtubeUrl", project.youtubeUrl)
            put("videoId", project.videoId)
            put("summary", project.videoSummary)
            put("clips", JSONArray().apply {
                clips.forEach { clip ->
                    put(JSONObject().apply {
                        put("title", clip.title)
                        put("startSeconds", clip.startSeconds)
                        put("endSeconds", clip.endSeconds)
                        put("startFormatted", YouTubeUrlParser.formatTimestamp(clip.startSeconds))
                        put("endFormatted", YouTubeUrlParser.formatTimestamp(clip.endSeconds))
                        put("viralScore", clip.viralScore)
                        put("hookLine", clip.hookLine)
                        put("summary", clip.summary)
                        put("suggestedCaption", clip.suggestedCaption)
                        put("hashtags", clip.hashtagsCsv)
                        put(
                            "youtubeJumpUrl",
                            YouTubeUrlParser.buildTimestampJumpUrl(project.videoId, clip.startSeconds)
                        )
                    })
                }
            })
        }
        return root.toString(2)
    }
}
