package com.yuvraj.resumescreener.data.remote

import com.yuvraj.resumescreener.domain.ai.Prompts
import com.yuvraj.resumescreener.domain.ai.StructuredLlmClient
import com.yuvraj.resumescreener.domain.model.JobDescription
import com.yuvraj.resumescreener.domain.model.MatchResult
import com.yuvraj.resumescreener.domain.model.Resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google's Gemini REST API.
 *
 * One of the two providers. Its sibling, [OpenAiCompatibleClient], serves any
 * OpenAI-dialect endpoint, so switching provider never changes the pipeline.
 *
 * The key travels in the `x-goog-api-key` header rather than a query
 * parameter, so it cannot leak into server access logs, and it is supplied per
 * call rather than held here.
 */
@Singleton
class GeminiClient @Inject constructor(
    private val httpClient: OkHttpClient,
) : StructuredLlmClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Overridable so tests can aim at a mock server. Production always uses
    // BASE_URL, so a leaked key in CI can never reach the real API.
    private var baseUrl: String = BASE_URL

    override val providerId: String = LlmProviderIds.GEMINI
    override val displayName: String = "Google Gemini"

    override suspend fun generate(
        model: String,
        prompt: String,
        schema: JsonObject,
        apiKey: String,
    ): String = withContext(Dispatchers.IO) {
        val payload = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(prompt)))),
            generationConfig = GenerationConfig(responseSchema = schema),
        )
        val body = json.encodeToString(GenerateContentRequest.serializer(), payload)
            .toRequestBody(JSON_MEDIA)

        val request = Request.Builder()
            .url("$baseUrl/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body)
            .build()

        withRetries(request, model) { raw -> extractText(raw) }
    }

    override suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/models")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        execute(request) { body ->
            runCatching { json.decodeFromString<ModelListResponse>(body) }
                .getOrElse { throw LlmException.MalformedResponse("model list was not JSON") }
                .models.map { it.name.substringAfterLast('/') }
        }
    }

    override suspend fun verify(apiKey: String): Boolean =
        runCatching { listModels(apiKey).isNotEmpty() }.getOrDefault(false)

    /**
     * Two retries, matching `max_retries=2` in the source nodes. Only
     * retryable failures are retried: a rejected key or a retired model will
     * not succeed on a second attempt.
     */
    private suspend inline fun <T> withRetries(
        request: Request,
        model: String,
        crossinline parse: (String) -> T,
    ): T {
        var last: LlmException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return execute(request, model) { raw -> parse(raw) }
            } catch (e: LlmException) {
                last = e
                if (!e.isRetryable() || attempt == MAX_ATTEMPTS - 1) throw e
                delay(RETRY_BASE_DELAY_MS * (attempt + 1))
            }
        }
        throw last ?: LlmException.MalformedResponse("exhausted retries")
    }

    private fun LlmException.isRetryable(): Boolean = when (this) {
        is LlmException.RateLimited, is LlmException.ServerUnavailable, is LlmException.Network -> true
        else -> false
    }

    private inline fun <T> execute(
        request: Request,
        model: String = "",
        parse: (String) -> T,
    ): T {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw LlmException.Network(e)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw mapHttpError(it.code, body, model)
            return parse(body)
        }
    }

    private fun mapHttpError(code: Int, body: String, model: String): LlmException = when (code) {
        400, 401, 403 -> LlmException.InvalidKey(extractApiMessage(body).takeIf { it != "HTTP 404" })
        404 -> LlmException.ModelUnavailable(model, extractApiMessage(body))
        429 -> LlmException.RateLimited(extractApiMessage(body).takeIf { it != "HTTP 404" })
        in 500..599 -> LlmException.ServerUnavailable(code)
        else -> LlmException.MalformedResponse("HTTP $code: ${body.take(200)}")
    }

    /** Google's own wording, usually the most actionable part of an error. */
    private fun extractApiMessage(body: String): String =
        runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.content
        }.getOrNull()?.take(240) ?: "HTTP 404"

    private fun extractText(raw: String): String {
        // Without this guard a 200 with an unexpected body throws a raw
        // SerializationException past the error taxonomy, and the user sees a
        // crash instead of "the response was not what we expected".
        val parsed = runCatching { json.decodeFromString<GenerateContentResponse>(raw) }
            .getOrElse { throw LlmException.MalformedResponse("response was not JSON") }
        parsed.promptFeedback?.blockReason?.let { throw LlmException.Blocked(it) }
        val candidate = parsed.candidates.firstOrNull()
            ?: throw LlmException.MalformedResponse("no candidates returned")
        val text = candidate.content?.parts?.firstOrNull()?.text
        if (text.isNullOrBlank()) {
            throw LlmException.MalformedResponse("empty text, finishReason=${candidate.finishReason}")
        }
        return text
    }

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        /** Test seam: a client aimed at a mock server instead of the real API. */
        internal fun forBaseUrl(client: OkHttpClient, url: String): GeminiClient =
            GeminiClient(client).also { it.baseUrl = url }
        private const val MAX_ATTEMPTS = 3 // 1 initial + 2 retries
        private const val RETRY_BASE_DELAY_MS = 1_500L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
