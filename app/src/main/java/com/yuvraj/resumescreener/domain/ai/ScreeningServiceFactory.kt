package com.yuvraj.resumescreener.domain.ai

import com.yuvraj.resumescreener.data.remote.GeminiClient
import com.yuvraj.resumescreener.data.remote.OpenAiCompatibleClient
import com.yuvraj.resumescreener.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Builds a [ScreeningService] for whichever provider is selected.
 *
 * Returning null when the active provider has no key or base URL is what makes
 * the app fail closed: callers stop the batch and point the user at Settings,
 * rather than silently using the other provider's credential.
 */
@Singleton
class ScreeningServiceFactory @Inject constructor(
    private val settings: SettingsRepository,
    private val gemini: GeminiClient,
    private val httpClient: OkHttpClient,
) {
    fun create(): ScreeningService? {
        val key = settings.activeApiKeyOrNull() ?: return null
        return when (settings.provider.value) {
            LlmProvider.GEMINI -> ScreeningService(
                client = gemini,
                parseModel = settings.parseModel.value,
                scoreModel = settings.scoreModel.value,
                apiKey = key,
            )

            LlmProvider.OPENAI_COMPATIBLE -> {
                val base = settings.openAiBaseUrlOrNull() ?: return null
                ScreeningService(
                    client = OpenAiCompatibleClient(httpClient, base),
                    parseModel = settings.parseModel.value,
                    scoreModel = settings.scoreModel.value,
                    apiKey = key,
                )
            }
        }
    }
}
