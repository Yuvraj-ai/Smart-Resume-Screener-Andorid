package com.yuvraj.resumescreener.data.remote

import com.yuvraj.resumescreener.domain.ai.StructuredLlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Any endpoint speaking the OpenAI chat-completions protocol.
 *
 * This is what makes a self-hosted or third-party model, such as Gemma, usable
 * without touching the pipeline. Differences from [GeminiClient]:
 *
 * - Auth is `Authorization: Bearer`, not `x-goog-api-key`.
 * - The path is `/chat/completions`, not `/models/{m}:generateContent`.
 * - Schema enforcement is `response_format.json_schema`, the equivalent of
 *   Gemini's `generationConfig.responseSchema`, so the guarantee is the same.
 * - The reply arrives wrapped in `choices[0].message.content`.
 */
class OpenAiCompatibleClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : StructuredLlmClient {

    /** Trailing slashes are trimmed so `$base/chat/completions` is always valid. */
    private val base = baseUrl.trim().trimEnd('/')

    // Checked up front: building a Request from a relative URL throws an
    // IllegalArgumentException that would surface as a crash rather than the
    // actionable "check your base URL" message.
    init {
        require(base.isNotBlank()) { "Base URL must not be blank" }
    }

    override val providerId: String = LlmProviderIds.OPENAI_COMPATIBLE
    override val displayName: String = "OpenAI-compatible endpoint"

    override suspend fun generate(
        model: String,
        prompt: String,
        schema: JsonObject,
        apiKey: String,
    ): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("temperature", JsonPrimitive(0.0))
            put(
                "messages",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", JsonPrimitive(prompt))
                        }
                    )
                ),
            )
            put(
                "response_format",
                buildJsonObject {
                    put("type", JsonPrimitive("json_schema"))
                    put(
                        "json_schema",
                        buildJsonObject {
                            put("name", JsonPrimitive("screening_output"))
                            put("strict", JsonPrimitive(true))
                            put("schema", schema)
                        },
                    )
                },
            )
        }

        val request = Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val raw = execute(request, model, apiKey)
        assistantText(raw)
    }

    override suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$base/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        execute(request, model = "", apiKey = apiKey).let { raw ->
            json.parseToJsonElement(raw).jsonObject["data"]?.jsonArray
                ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                .orEmpty()
        }
    }

    override suspend fun verify(apiKey: String): Boolean =
        runCatching { listModels(apiKey).isNotEmpty() }.getOrDefault(false)

    /** Unwrap `choices[0].message.content`, tolerating extra prose around it. */
    private fun assistantText(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw LlmException.MalformedResponse("response was not JSON")
        val choices = root["choices"]?.jsonArray.orEmpty()
        val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw LlmException.MalformedResponse("no choices in response")
        val content = message["content"]?.jsonPrimitive?.content
        if (content.isNullOrBlank()) {
            throw LlmException.MalformedResponse("assistant content was empty")
        }
        return content
    }

    private fun execute(request: Request, model: String, apiKey: String): String {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw LlmException.Network(e)
        }
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw mapError(it.code, raw, model)
            if (raw.isBlank()) throw LlmException.MalformedResponse("empty body")
            return raw
        }
    }

    private fun mapError(code: Int, body: String, model: String): LlmException {
        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.content
        }.getOrNull()?.take(240)

        return when (code) {
            401, 403 -> LlmException.InvalidKey()
            404 -> if (model.isBlank()) {
                LlmException.Unreachable("the base URL did not answer at /models")
            } else {
                LlmException.ModelUnavailable(model, message ?: "not found")
            }
            429 -> LlmException.RateLimited()
            in 500..599 -> LlmException.ServerUnavailable(code)
            else -> LlmException.MalformedResponse("HTTP $code: ${message ?: body.take(160)}")
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** Accepts a bare host as well as a full `/v1` style base. */
        fun normaliseBaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return if (trimmed.isEmpty() || trimmed.startsWith("http")) trimmed
            else "https://$trimmed"
        }
    }
}

internal object LlmProviderIds {
    const val GEMINI = "gemini"
    const val OPENAI_COMPATIBLE = "openai_compatible"
}
