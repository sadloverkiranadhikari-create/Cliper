package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ProjectWithClips
import com.example.data.local.VideoClipEntity
import com.example.data.model.ManifestFormat
import com.example.data.remote.YouTubeUrlParser
import com.example.ui.components.ClipHighlightCard
import com.example.ui.components.VideoPreviewDialog
import com.example.ui.viewmodel.ClipExportStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectWithClips: ProjectWithClips?,
    exportStatuses: Map<Long, ClipExportStatus>,
    isBatchExporting: Boolean,
    onBack: () -> Unit,
    onAttachLocalVideo: (Uri) -> Unit,
    onAttachDirectUrl: (String) -> Unit,
    onUpdateClipRange: (Long, Int, Int) -> Unit,
    onExportClipMp4: (String, VideoClipEntity) -> Unit,
    onExportAllClips: (String, List<VideoClipEntity>) -> Unit,
    onDownloadManifest: (ManifestFormat) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showDirectUrlDialog by remember { mutableStateOf(false) }
    var editingClip by remember { mutableStateOf<VideoClipEntity?>(null) }
    var previewingClip by remember { mutableStateOf<VideoClipEntity?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAttachLocalVideo(uri)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = projectWithClips?.project?.title ?: "Project",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_content_description)
                    )
                }
            },
            actions = {
                projectWithClips?.project?.youtubeUrl?.let { ytUrl ->
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)))
                        },
                        modifier = Modifier.testTag("detail_open_youtube")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.watch_on_youtube)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (projectWithClips == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val project = projectWithClips.project
        val clips = projectWithClips.clips
        val sourceUriString = project.attachedSourceUri

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Video Overview Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = project.thumbnailUrl,
                                contentDescription = project.title,
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = project.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = project.channelName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "${project.clipFocus} • ${project.targetDurationRange}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (project.videoSummary.isNotBlank()) {
                            Text(
                                text = project.videoSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Downloadable Manifest Buttons (.SRT / .JSON)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onDownloadManifest(ManifestFormat.SRT) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("download_srt_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(R.string.action_download_srt))
                            }
                            OutlinedButton(
                                onClick = { onDownloadManifest(ManifestFormat.JSON) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("download_json_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(R.string.action_download_json))
                            }
                        }
                    }
                }
            }

            // Source Video Attachment Card for Real On-Device MP4 Clipping
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (sourceUriString != null) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (sourceUriString != null) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoFile,
                                contentDescription = null,
                                tint = if (sourceUriString != null) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                            Text(
                                text = stringResource(R.string.source_video_section),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        Text(
                            text = project.attachedSourceLabel
                                ?: stringResource(R.string.source_video_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.VideoOnly
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_video_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(R.string.action_pick_video))
                            }

                            OutlinedButton(
                                onClick = { showDirectUrlDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("direct_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Http,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(R.string.action_mp4_url))
                            }
                        }

                        if (sourceUriString != null && clips.isNotEmpty()) {
                            Button(
                                onClick = { onExportAllClips(sourceUriString, clips) },
                                enabled = !isBatchExporting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("export_all_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isBatchExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Batch Exporting Clips…")
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.action_export_all))
                                }
                            }
                        }
                    }
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.clips_header),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "${clips.size} clips",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            itemsIndexed(clips, key = { _, clip -> clip.id }) { index, clip ->
                ClipHighlightCard(
                    index = index,
                    clip = clip,
                    hasSourceMedia = sourceUriString != null,
                    exportStatus = exportStatuses[clip.id] ?: ClipExportStatus.Idle,
                    onEditTrim = { editingClip = clip },
                    onJumpYouTube = {
                        val jumpUrl = YouTubeUrlParser.buildTimestampJumpUrl(
                            project.videoId,
                            clip.startSeconds
                        )
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(jumpUrl)))
                    },
                    onCopyCaption = {
                        val fullText = buildString {
                            if (clip.suggestedCaption.isNotBlank()) append(clip.suggestedCaption)
                            if (clip.hashtagsCsv.isNotBlank()) {
                                if (isNotEmpty()) append("\n\n")
                                append(clip.hashtagsCsv)
                            }
                        }
                        clipboardManager.setText(AnnotatedString(fullText))
                        onShowMessage("Caption & hashtags copied")
                    },
                    onExportMp4 = {
                        if (sourceUriString != null) {
                            onExportClipMp4(sourceUriString, clip)
                        }
                    },
                    onPlayClip = { previewingClip = clip },
                    onShareClip = {
                        val uriStr = clip.exportedMediaUri ?: return@ClipHighlightCard
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, clip.title))
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showDirectUrlDialog) {
        DirectMp4UrlDialog(
            onDismiss = { showDirectUrlDialog = false },
            onConfirm = { url ->
                showDirectUrlDialog = false
                onAttachDirectUrl(url)
            }
        )
    }

    editingClip?.let { target ->
        TrimClipDialog(
            clip = target,
            onDismiss = { editingClip = null },
            onSave = { startSec, endSec ->
                editingClip = null
                onUpdateClipRange(target.id, startSec, endSec)
            }
        )
    }

    previewingClip?.let { clip ->
        val uriStr = clip.exportedMediaUri
        if (!uriStr.isNullOrBlank()) {
            VideoPreviewDialog(
                title = clip.title,
                fileName = clip.exportedFileName ?: "Clip.mp4",
                mediaUri = Uri.parse(uriStr),
                onShare = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, clip.title))
                },
                onDismiss = { previewingClip = null }
            )
        }
    }
}

@Composable
private fun DirectMp4UrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.direct_url_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.direct_url_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text(stringResource(R.string.direct_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_mp4_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(urlText) },
                enabled = urlText.isNotBlank(),
                modifier = Modifier.testTag("confirm_direct_url_button")
            ) {
                Text(text = stringResource(R.string.action_attach))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun TrimClipDialog(
    clip: VideoClipEntity,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var startSec by remember(clip.id) { mutableIntStateOf(clip.startSeconds) }
    var endSec by remember(clip.id) { mutableIntStateOf(clip.endSeconds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.trim_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = clip.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                TimeStepperRow(
                    label = stringResource(R.string.trim_start_label),
                    seconds = startSec,
                    onAdjust = { delta ->
                        val next = (startSec + delta).coerceAtLeast(0)
                        startSec = next
                        if (endSec <= next) {
                            endSec = next + 1
                        }
                    }
                )

                TimeStepperRow(
                    label = stringResource(R.string.trim_end_label),
                    seconds = endSec,
                    onAdjust = { delta ->
                        endSec = (endSec + delta).coerceAtLeast(startSec + 1)
                    }
                )

                Text(
                    text = "Clip duration: ${(endSec - startSec).coerceAtLeast(1)}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(startSec, endSec) },
                modifier = Modifier.testTag("save_trim_button")
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun TimeStepperRow(
    label: String,
    seconds: Int,
    onAdjust: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = YouTubeUrlParser.formatTimestamp(seconds),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(-5, -1, +1, +5).forEach { delta ->
                OutlinedButton(
                    onClick = { onAdjust(delta) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (delta > 0) "+${delta}s" else "${delta}s",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
