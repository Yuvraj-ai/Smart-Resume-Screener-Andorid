package com.yuvraj.resumescreener.data.remote

import com.yuvraj.resumescreener.domain.ai.Prompts
import com.yuvraj.resumescreener.domain.model.JobDescription
import com.yuvraj.resumescreener.domain.model.MatchResult
import com.yuvraj.resumescreener.domain.model.Resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls the Gemini REST API directly from the device.
 *
 * This is the Android replacement for `langchain_google_genai`. The Python
 * source's three `with_structured_output` calls become three
 * `generateStructured` calls differing only in model, prompt, and schema.
 *
 * The key travels in the `x-goog-api-key` header rather than a query parameter,
 * so it cannot leak into server access logs.
 */
@Singleton
class GeminiClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    // Overridable so tests can point at a mock server. Production always uses BASE_URL.
    internal var baseUrl: String = BASE_URL

    internal fun baseUrlForTest(): String = baseUrl

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun extractResume(resumeText: String, model: String, apiKey: String): Resume =
        generateStructured(
            prompt = Prompts.extractResume(resumeText),
            model = model,
            schema = ResponseSchemas.resume,
            apiKey = apiKey,
        )

    suspend fun extractJobDescription(jdText: String, model: String, apiKey: String): JobDescription =
        generateStructured(
            prompt = Prompts.extractJobDescription(jdText),
            model = model,
            schema = ResponseSchemas.jobDescription,
            apiKey = apiKey,
        )

    suspend fun score(resume: Resume, job: JobDescription, model: String, apiKey: String): MatchResult =
        generateStructured(
            prompt = Prompts.score(resume, job),
            model = model,
            schema = ResponseSchemas.matchResult,
            apiKey = apiKey,
        )

    /** Cheapest possible authenticated call, used by the Settings "test connection" action. */
    suspend fun verifyKey(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/models")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        execute(request) { body ->
            json.decodeFromString<ModelListResponse>(body).models.map { it.name }
        }
    }

    private suspend inline fun <reified T> generateStructured(
        prompt: String,
        model: String,
        schema: JsonObject,
        apiKey: String,
    ): T = withContext(Dispatchers.IO) {
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

        executeWithRetry(request) { raw ->
            val text = extractText(raw)
            json.decodeFromString<T>(text)
        }
    }

    /**
     * Two retries, matching `max_retries=2` in the source nodes. Only
     * retryable failures are retried: a rejected key or a blocked prompt will
     * never succeed on a second attempt.
     */
    private suspend inline fun <T> executeWithRetry(
        request: Request,
        crossinline parse: (String) -> T,
    ): T {
        var last: GeminiException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return execute(request) { raw -> parse(raw) }
            } catch (e: GeminiException) {
                last = e
                if (!e.isRetryable() || attempt == MAX_ATTEMPTS - 1) throw e
                delay(RETRY_BASE_DELAY_MS * (attempt + 1))
            }
        }
        throw last ?: GeminiException.MalformedResponse("exhausted retries")
    }

    private fun GeminiException.isRetryable(): Boolean = when (this) {
        is GeminiException.RateLimited, is GeminiException.ServerUnavailable, is GeminiException.Network -> true
        else -> false
    }

    /** Runs the request and maps every failure onto [GeminiException]. */
    private inline fun <T> execute(request: Request, parse: (String) -> T): T {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw GeminiException.Network(e)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw mapHttpError(it.code, body)
            return parse(body)
        }
    }

    private fun mapHttpError(code: Int, body: String): GeminiException = when (code) {
        400, 401, 403 -> GeminiException.InvalidKey()
        429 -> GeminiException.RateLimited()
        in 500..599 -> GeminiException.ServerUnavailable(code)
        else -> GeminiException.MalformedResponse("HTTP $code: ${body.take(200)}")
    }

    private fun extractText(raw: String): String {
        val parsed = json.decodeFromString<GenerateContentResponse>(raw)
        parsed.promptFeedback?.blockReason?.let { throw GeminiException.Blocked(it) }
        val candidate = parsed.candidates.firstOrNull()
            ?: throw GeminiException.MalformedResponse("no candidates returned")
        val text = candidate.content?.parts?.firstOrNull()?.text
        if (text.isNullOrBlank()) {
            throw GeminiException.MalformedResponse(
                "empty text, finishReason=${candidate.finishReason}"
            )
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
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
