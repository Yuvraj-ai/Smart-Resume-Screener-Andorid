package com.yuvraj.resumescreener.domain.ai

import kotlinx.serialization.json.JsonObject

/**
 * One structured call to a language model.
 *
 * The port originally spoke only to Gemini. Any OpenAI-compatible endpoint can
 * serve the same contract, which is why this exists: the pipeline must not know
 * or care which provider is configured, only that it can be handed a model, a
 * prompt and a schema and return JSON matching that schema.
 *
 * The API key is passed per call rather than held by the implementation. That
 * keeps it out of any object that could be captured into UI state, and means a
 * provider can be swapped without a credential ever sitting in a field.
 */
interface StructuredLlmClient {

    /** Stable id used in settings, persistence and error messages. */
    val providerId: String

    /** Human-readable name for the UI. */
    val displayName: String

    /**
     * Run one prompt constrained to [schema], returning the raw JSON text.
     *
     * Text rather than a parsed type keeps this interface free of generics, so
     * one implementation can serve three different response shapes.
     */
    suspend fun generate(model: String, prompt: String, schema: JsonObject, apiKey: String): String

    /** Model ids this endpoint offers, for the settings pickers. */
    suspend fun listModels(apiKey: String): List<String>

    /** Whether the credential works. Returns false rather than throwing. */
    suspend fun verify(apiKey: String): Boolean
}

/** Which provider the user has selected. Both are always available. */
enum class LlmProvider(val id: String, val label: String) {
    GEMINI("gemini", "Google Gemini"),
    OPENAI_COMPATIBLE("openai_compatible", "OpenAI-compatible endpoint"),
}
