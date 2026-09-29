package com.yuvraj.resumescreener.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuvraj.resumescreener.data.repository.ScreeningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: ScreeningRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1L

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val entity = repository.byId(id)
            _state.value = if (entity == null) {
                DetailUiState(notFound = true)
            } else {
                DetailUiState(entity = entity, rawJson = buildRawJson(entity))
            }
        }
    }

    fun confirmDelete() {
        val entity = _state.value.entity ?: return
        viewModelScope.launch {
            repository.delete(entity)
            _state.value = DetailUiState(deleted = true)
        }
    }

    fun dismissDelete() {
        _state.value = _state.value.copy(confirmingDelete = false)
    }

    fun askDelete() {
        _state.value = _state.value.copy(confirmingDelete = true)
    }

    /** Mirrors the Python app's `st.json` section: the exact stored document. */
    private fun buildRawJson(entity: com.yuvraj.resumescreener.data.local.ScreeningEntity) = buildString {
        append("{\n")
        append("  \"candidate_details\": ").append(pretty(entity.candidateDetailsJson)).append(",\n")
        append("  \"job_details\": ").append(pretty(entity.jobDetailsJson)).append(",\n")
        append("  \"match_analysis\": ").append(pretty(entity.matchAnalysisJson)).append("\n")
        append("}")
    }

    private fun pretty(json: String): String = json
}
