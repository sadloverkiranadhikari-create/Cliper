package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.YouTubeClipAiService
import com.example.data.local.ClipForgeDatabase
import com.example.data.local.ExportedClipWithProject
import com.example.data.local.ProjectWithClips
import com.example.data.local.VideoClipEntity
import com.example.data.media.ClipExportEvent
import com.example.data.media.VideoClipExporter
import com.example.data.model.ClipFocus
import com.example.data.model.DurationPreset
import com.example.data.model.ManifestFormat
import com.example.data.model.YouTubeParsedLink
import com.example.data.remote.YouTubeMetadataClient
import com.example.data.remote.YouTubeUrlParser
import com.example.data.repository.AnalysisStage
import com.example.data.repository.ClipForgeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AnalyzeUiState {
    data object Idle : AnalyzeUiState
    data class Loading(val stage: AnalysisStage) : AnalyzeUiState
    data class Success(val projectId: Long) : AnalyzeUiState
    data class Error(val message: String) : AnalyzeUiState
}

sealed interface ClipExportStatus {
    data object Idle : ClipExportStatus
    data class Exporting(val label: String, val progressPercent: Int) : ClipExportStatus
    data class Completed(val fileName: String) : ClipExportStatus
    data class Failed(val message: String) : ClipExportStatus
}

data class StudioUiState(
    val urlInput: String = "",
    val parsedLink: YouTubeParsedLink? = null,
    val selectedFocus: ClipFocus = ClipFocus.VIRAL_HOOKS,
    val selectedDuration: DurationPreset = DurationPreset.MEDIUM,
    val analyzeState: AnalyzeUiState = AnalyzeUiState.Idle
)

class ClipForgeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClipForgeRepository by lazy {
        val db = ClipForgeDatabase.getInstance(application)
        ClipForgeRepository(
            dao = db.clipForgeDao(),
            metadataClient = YouTubeMetadataClient(),
            aiService = YouTubeClipAiService(),
            exporter = VideoClipExporter(application)
        )
    }

    private val _studioState = MutableStateFlow(StudioUiState())
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    private val _exportStatuses = MutableStateFlow<Map<Long, ClipExportStatus>>(emptyMap())
    val exportStatuses: StateFlow<Map<Long, ClipExportStatus>> = _exportStatuses.asStateFlow()

    private val _isBatchExporting = MutableStateFlow(false)
    val isBatchExporting: StateFlow<Boolean> = _isBatchExporting.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val projects: StateFlow<List<ProjectWithClips>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val exportedClips: StateFlow<List<ExportedClipWithProject>> = repository.exportedClips
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun observeProject(projectId: Long): Flow<ProjectWithClips?> =
        repository.observeProject(projectId)

    fun onUrlChanged(input: String) {
        _studioState.update { state ->
            state.copy(
                urlInput = input,
                parsedLink = YouTubeUrlParser.parse(input),
                analyzeState = if (state.analyzeState is AnalyzeUiState.Error) {
                    AnalyzeUiState.Idle
                } else {
                    state.analyzeState
                }
            )
        }
    }

    fun onFocusSelected(focus: ClipFocus) {
        _studioState.update { it.copy(selectedFocus = focus) }
    }

    fun onDurationSelected(preset: DurationPreset) {
        _studioState.update { it.copy(selectedDuration = preset) }
    }

    fun clearAnalyzeError() {
        _studioState.update { it.copy(analyzeState = AnalyzeUiState.Idle) }
    }

    fun analyzeYouTubeLink(onProjectReady: (Long) -> Unit) {
        val current = _studioState.value
        if (current.analyzeState is AnalyzeUiState.Loading) return

        viewModelScope.launch {
            _studioState.update {
                it.copy(analyzeState = AnalyzeUiState.Loading(AnalysisStage.FETCHING_METADATA))
            }
            try {
                val projectId = repository.analyzeAndSaveProject(
                    rawUrl = current.urlInput,
                    focus = current.selectedFocus,
                    durationPreset = current.selectedDuration,
                    onStageChanged = { stage ->
                        _studioState.update { it.copy(analyzeState = AnalyzeUiState.Loading(stage)) }
                    }
                )
                _studioState.update {
                    it.copy(analyzeState = AnalyzeUiState.Success(projectId))
                }
                onProjectReady(projectId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _studioState.update {
                    it.copy(
                        analyzeState = AnalyzeUiState.Error(
                            e.localizedMessage ?: "Failed to analyze YouTube link."
                        )
                    )
                }
            }
        }
    }

    fun attachLocalVideo(projectId: Long, uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                app.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Photo Picker grants temporary read access even if persistable flag isn't offered
            } catch (_: Exception) {
                // Ignore if provider doesn't support persistable URI grants
            }

            val displayName = resolveDisplayName(app, uri) ?: "Selected Device Video"
            repository.attachSourceMedia(projectId, uri.toString(), displayName)
            _snackbarMessage.value = "Attached source video: $displayName"
        }
    }

    fun attachDirectMp4Url(projectId: Long, rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            _snackbarMessage.value = "Enter a valid http:// or https:// MP4 URL."
            return
        }
        viewModelScope.launch {
            val label = trimmed.substringAfterLast('/').substringBefore('?').ifBlank { trimmed }
            repository.attachSourceMedia(projectId, trimmed, "Stream: $label")
            _snackbarMessage.value = "Attached direct video URL."
        }
    }

    fun updateClipRange(clipId: Long, startSeconds: Int, endSeconds: Int) {
        viewModelScope.launch {
            repository.updateClipRange(clipId, startSeconds, endSeconds)
            _snackbarMessage.value = "Updated clip range."
        }
    }

    fun exportClipMp4(sourceUriString: String, clip: VideoClipEntity) {
        if (_exportStatuses.value[clip.id] is ClipExportStatus.Exporting) return
        viewModelScope.launch {
            runSingleClipExport(Uri.parse(sourceUriString), clip)
        }
    }

    fun exportAllClips(sourceUriString: String, clips: List<VideoClipEntity>) {
        if (_isBatchExporting.value || clips.isEmpty()) return
        val sourceUri = Uri.parse(sourceUriString)
        viewModelScope.launch {
            _isBatchExporting.value = true
            var successCount = 0
            try {
                for (clip in clips) {
                    val ok = runSingleClipExport(sourceUri, clip)
                    if (ok) successCount++
                }
                _snackbarMessage.value =
                    "Exported $successCount of ${clips.size} clips to Movies/ClipForge."
            } finally {
                _isBatchExporting.value = false
            }
        }
    }

    private suspend fun runSingleClipExport(sourceUri: Uri, clip: VideoClipEntity): Boolean {
        return try {
            val completed = repository.exportClipMp4(
                sourceUri = sourceUri,
                clip = clip,
                onProgress = { event ->
                    val status = when (event) {
                        ClipExportEvent.Preparing -> ClipExportStatus.Exporting("Preparing…", 2)
                        is ClipExportEvent.Processing -> ClipExportStatus.Exporting(
                            "Clipping ${event.progressPercent}%",
                            event.progressPercent
                        )
                        ClipExportEvent.SavingToDevice -> ClipExportStatus.Exporting("Saving MP4…", 99)
                        is ClipExportEvent.Completed -> ClipExportStatus.Completed(event.fileName)
                    }
                    _exportStatuses.update { it + (clip.id to status) }
                }
            )
            _snackbarMessage.value = "Saved ${completed.fileName} to Movies/ClipForge"
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Export failed."
            _exportStatuses.update { it + (clip.id to ClipExportStatus.Failed(msg)) }
            _snackbarMessage.value = msg
            false
        }
    }

    fun downloadManifest(projectId: Long, format: ManifestFormat) {
        viewModelScope.launch {
            try {
                val (_, fileName) = repository.downloadManifest(projectId, format)
                _snackbarMessage.value = "Downloaded $fileName to Downloads/ClipForge"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Failed to save ${format.label}."
            }
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            _snackbarMessage.value = "Project deleted"
        }
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    private fun resolveDisplayName(app: Application, uri: Uri): String? {
        return try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }
}
