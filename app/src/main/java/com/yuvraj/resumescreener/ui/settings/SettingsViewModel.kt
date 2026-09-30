package com.yuvraj.resumescreener.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuvraj.resumescreener.data.remote.GeminiClient
import com.yuvraj.resumescreener.data.remote.LlmException
import com.yuvraj.resumescreener.data.remote.OpenAiCompatibleClient
import com.yuvraj.resumescreener.data.settings.KeyStatus
import com.yuvraj.resumescreener.data.settings.SettingsRepository
import com.yuvraj.resumescreener.domain.ai.LlmProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

enum class TestState { IDLE, RUNNING, PASSED, FAILED }

data class SettingsUiState(
    val provider: LlmProvider = LlmProvider.GEMINI,
    val keyStatus: KeyStatus = KeyStatus.MISSING,
    val openAiKeyStatus: KeyStatus = KeyStatus.MISSING,
    val parseModel: String = SettingsRepository.DEFAULT_PARSE_MODEL,
    val scoreModel: String = SettingsRepository.DEFAULT_SCORE_MODEL,
    val openAiBaseUrl: String = "",
    val openAiModelOptions: List<String> = emptyList(),
    val test: TestState = TestState.IDLE,
    val testDetail: String? = null,
    val loadingModels: Boolean = false,
) {
    /** Status of whichever provider is selected, for the field the user sees. */
    val activeKeyStatus: KeyStatus
        get() = when (provider) {
            LlmProvider.GEMINI -> keyStatus
            LlmProvider.OPENAI_COMPATIBLE -> openAiKeyStatus
        }

    /** The active provider's endpoint, or null for Gemini which has a fixed one. */
    val activeBaseUrl: String?
        get() = if (provider == LlmProvider.OPENAI_COMPATIBLE) openAiBaseUrl else null
}

/** Everything read from the repository, flattened into one stream. */
private data class SettingsSnapshot(
    val provider: LlmProvider,
    val geminiStatus: KeyStatus,
    val openAiStatus: KeyStatus,
    val parseModel: String,
    val scoreModel: String,
    val baseUrl: String,
    val modelOptions: List<String>,
)

/**
 * Both providers are always available. Switching only changes which fields the
 * screen shows and which key the pipeline uses; the other provider's stored
 * credential is untouched, so moving back and forth loses nothing.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val gemini: GeminiClient,
    private val httpClient: OkHttpClient,
) : ViewModel() {

    private val test = MutableStateFlow(TestState.IDLE)
    private val detail = MutableStateFlow<String?>(null)
    private val loadingModels = MutableStateFlow(false)

    private val snapshot = combine(
        combine(
            repository.provider,
            repository.keyStatus,
            repository.openAiKeyStatus,
        ) { p, g, o -> Triple(p, g, o) },
        combine(repository.parseModel, repository.scoreModel) { p, s -> p to s },
        combine(
            repository.openAiBaseUrl,
            repository.openAiModelOptions,
        ) { u, m -> u to m },
    ) { providerKeys, models, endpoint ->
        SettingsSnapshot(
            provider = providerKeys.first,
            geminiStatus = providerKeys.second,
            openAiStatus = providerKeys.third,
            parseModel = models.first,
            scoreModel = models.second,
            baseUrl = endpoint.first,
            modelOptions = endpoint.second,
        )
    }

    val state: StateFlow<SettingsUiState> = combine(
        snapshot,
        test,
        detail,
        loadingModels,
    ) { snap, t, d, loading ->
        SettingsUiState(
            provider = snap.provider,
            keyStatus = snap.geminiStatus,
            openAiKeyStatus = snap.openAiStatus,
            parseModel = snap.parseModel,
            scoreModel = snap.scoreModel,
            openAiBaseUrl = snap.baseUrl,
            openAiModelOptions = snap.modelOptions,
            test = t,
            testDetail = d,
            loadingModels = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // ---- provider ----------------------------------------------------------
    fun setProvider(value: LlmProvider) {
        repository.setProvider(value)
        resetTest()
    }

    // ---- gemini ------------------------------------------------------------
    fun saveGeminiKey(raw: String) {
        repository.saveApiKey(raw)
        resetTest()
    }

    fun clearGeminiKey() {
        repository.clearApiKey()
        resetTest()
    }

    // ---- openai-compatible -------------------------------------------------
    fun setOpenAiBaseUrl(value: String) = repository.setOpenAiBaseUrl(value)

    fun saveOpenAiKey(raw: String) {
        repository.saveOpenAiKey(raw)
        resetTest()
    }

    fun clearOpenAiKey() {
        repository.clearOpenAiKey()
        resetTest()
    }

    /**
     * Fetch the endpoint's model list so the user picks real ids rather than
     * typing one and getting a 404. Doubles as the credential check.
     */
    fun loadOpenAiModels() {
        val base = repository.openAiBaseUrlOrNull()
        val key = repository.activeKeyFor(LlmProvider.OPENAI_COMPATIBLE)
        if (base == null || key == null) {
            test.value = TestState.FAILED
            detail.value = "Set a base URL and an API key first."
            return
        }
        viewModelScope.launch {
            loadingModels.value = true
            runCatching { OpenAiCompatibleClient(httpClient, base).listModels(key) }
                .onSuccess { models ->
                    repository.setOpenAiModelOptions(models)
                    repository.markOpenAiVerified()
                    test.value = TestState.PASSED
                    detail.value = "${models.size} models available"
                }
                .onFailure { e ->
                    test.value = TestState.FAILED
                    detail.value = friendly(e)
                }
            loadingModels.value = false
        }
    }

    // ---- shared ------------------------------------------------------------
    fun testConnection() {
        val key = repository.activeKeyOrNull()
        if (repository.provider.value == LlmProvider.OPENAI_COMPATIBLE) {
            loadOpenAiModels()
            return
        }
        if (key == null) {
            test.value = TestState.FAILED
            detail.value = "No key saved yet."
            return
        }
        viewModelScope.launch {
            test.value = TestState.RUNNING
            runCatching { gemini.listModels(key) }
                .onSuccess { models ->
                    repository.markVerified()
                    test.value = TestState.PASSED
                    detail.value = "${models.size} models available"
                }
                .onFailure { e ->
                    test.value = TestState.FAILED
                    detail.value = friendly(e)
                }
        }
    }

    fun setParseModel(model: String) = repository.setParseModel(model)
    fun setScoreModel(model: String) = repository.setScoreModel(model)
    fun dismissDetail() = resetTest()

    private fun resetTest() {
        test.value = TestState.IDLE
        detail.value = null
    }

    /** Turn a transport failure into something the user can act on. */
    private fun friendly(e: Throwable): String = when (e) {
        is LlmException.Unreachable ->
            "${e.message}. Check the base URL includes the full path, " +
                "for example https://host/v1"
        else -> e.message ?: "Unexpected error"
    }
}
