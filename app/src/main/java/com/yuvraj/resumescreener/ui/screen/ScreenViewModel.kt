package com.yuvraj.resumescreener.ui.screen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuvraj.resumescreener.data.remote.GeminiException
import com.yuvraj.resumescreener.data.settings.SettingsRepository
import com.yuvraj.resumescreener.domain.usecase.BatchEvent
import com.yuvraj.resumescreener.domain.usecase.FileProgress
import com.yuvraj.resumescreener.domain.usecase.FileStage
import com.yuvraj.resumescreener.domain.usecase.ScreeningPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectedFile(val uri: Uri, val name: String)

data class ScreenUiState(
    val jobDescription: String = "",
    val files: List<SelectedFile> = emptyList(),
    val progress: Map<String, FileProgress> = emptyMap(),
    val running: Boolean = false,
    val error: String? = null,
    val missingKey: Boolean = false,
) {
    val canRun: Boolean get() = !running && files.isNotEmpty() && jobDescription.isNotBlank()
    val finished: Int get() = progress.values.count { it.stage == FileStage.DONE }
    val failed: Int get() = progress.values.count { it.stage == FileStage.FAILED }
}

@HiltViewModel
class ScreenViewModel @Inject constructor(
    private val pipeline: ScreeningPipeline,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenUiState())
    val state: StateFlow<ScreenUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onJobDescriptionChange(value: String) =
        _state.update { it.copy(jobDescription = value, error = null) }

    fun addFiles(added: List<SelectedFile>) = _state.update { current ->
        val existing = current.files.map { it.uri }.toSet()
        current.copy(
            files = current.files + added.filterNot { it.uri in existing },
            error = null,
        )
    }

    fun removeFile(uri: Uri) = _state.update { current ->
        current.copy(files = current.files.filterNot { it.uri == uri })
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun run() {
        val snapshot = _state.value
        if (!snapshot.canRun) return

        job = viewModelScope.launch {
            _state.update {
                it.copy(
                    running = true,
                    error = null,
                    missingKey = false,
                    progress = emptyMap(),
                )
            }
            try {
                pipeline.run(
                    files = snapshot.files.map { it.uri to it.name },
                    jobDescription = snapshot.jobDescription,
                    onMissingKey = {
                        _state.update { s -> s.copy(missingKey = true) }
                    },
                ).collect { event ->
                    when (event) {
                        is BatchEvent.Progress -> _state.update { s ->
                            s.copy(progress = s.progress + (event.progress.uri.toString() to event.progress))
                        }
                        is BatchEvent.Completed -> _state.update { s ->
                            s.copy(progress = s.progress + (event.progress.uri.toString() to event.progress))
                        }
                    }
                }
            } catch (e: GeminiException) {
                // InvalidKey is the one failure that affects every remaining
                // file, so it stops the batch rather than failing each in turn.
                _state.update { s ->
                    s.copy(error = e.message, missingKey = e is GeminiException.InvalidKey)
                }
            } finally {
                _state.update { s -> s.copy(running = false) }
            }
        }
    }

    fun clearFinished() = _state.update { it.copy(progress = emptyMap(), error = null) }
}
