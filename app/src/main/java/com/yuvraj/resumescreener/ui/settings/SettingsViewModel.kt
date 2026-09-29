package com.yuvraj.resumescreener.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuvraj.resumescreener.data.remote.GeminiClient
import com.yuvraj.resumescreener.data.settings.KeyStatus
import com.yuvraj.resumescreener.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TestState { IDLE, RUNNING, PASSED, FAILED }

data class SettingsUiState(
    val keyStatus: KeyStatus = KeyStatus.MISSING,
    val parseModel: String = SettingsRepository.DEFAULT_PARSE_MODEL,
    val scoreModel: String = SettingsRepository.DEFAULT_SCORE_MODEL,
    val test: TestState = TestState.IDLE,
    val testDetail: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val gemini: GeminiClient,
) : ViewModel() {

    private val test = MutableStateFlow(TestState.IDLE)
    private val detail = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        repository.keyStatus,
        combine(repository.parseModel, repository.scoreModel, ::Pair),
        test,
        detail,
    ) { status, models, t, d ->
        SettingsUiState(
            keyStatus = status,
            parseModel = models.first,
            scoreModel = models.second,
            test = t,
            testDetail = d,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun saveKey(raw: String) {
        repository.saveApiKey(raw)
        test.value = TestState.IDLE
        detail.value = null
    }

    fun clearKey() {
        repository.clearApiKey()
        test.value = TestState.IDLE
        detail.value = null
    }

    fun testConnection() {
        val key = repository.apiKeyOrNull()
        if (key == null) {
            test.value = TestState.FAILED
            detail.value = "No key saved yet."
            return
        }
        viewModelScope.launch {
            test.value = TestState.RUNNING
            detail.value = null
            runCatching { gemini.verifyKey(key) }
                .onSuccess { models ->
                    repository.markVerified()
                    test.value = TestState.PASSED
                    detail.value = "${models.size} models available"
                }
                .onFailure { e ->
                    test.value = TestState.FAILED
                    detail.value = e.message
                }
        }
    }

    fun setParseModel(model: String) = repository.setParseModel(model)

    fun setScoreModel(model: String) = repository.setScoreModel(model)
}
