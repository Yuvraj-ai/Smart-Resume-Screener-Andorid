package com.yuvraj.resumescreener.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject

/**
 * Wire types for the Gemini `generateContent` REST endpoint.
 *
 * These replace `langchain_google_genai.with_structured_output`. The Python
 * source relied on that helper to get schema-constrained JSON back; here the
 * same constraint is expressed with `responseMimeType: application/json` plus
 * an explicit `responseSchema`, so the model cannot drift from the shape.
 */

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    @SerialName("generationConfig") val generationConfig: GenerationConfig,
)

@Serializable
data class Content(
    val role: String = "user",
    val parts: List<Part>,
)

@Serializable
data class Part(val text: String)

@Serializable
data class GenerationConfig(
    val temperature: Double = 0.0,
    @SerialName("responseMimeType") val responseMimeType: String = "application/json",
    @SerialName("responseSchema") val responseSchema: JsonObject,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int? = null,
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
    @SerialName("promptFeedback") val promptFeedback: PromptFeedback? = null,
)

@Serializable
data class Candidate(
    val content: Content? = null,
    @SerialName("finishReason") val finishReason: String? = null,
)

@Serializable
data class PromptFeedback(
    @SerialName("blockReason") val blockReason: String? = null,
)

@Serializable
data class ModelListResponse(val models: List<ModelInfo> = emptyList())

@Serializable
data class ModelInfo(
    val name: String,
    @SerialName("displayName") val displayName: String? = null,
)

/**
 * Response schemas, mirroring the Pydantic models in the Python source.
 *
 * Written as literal JSON so each schema can be read directly against its
 * Python counterpart. `breakdown` is the only addition (decision d7).
 */
object ResponseSchemas {

    private val json = Json { ignoreUnknownKeys = true }

    /** Matches `parse_resume_node.py::Resume`. All fields string, per the source. */
    val resume: JsonObject by lazy {
        json.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "name":         { "type": "string" },
                "email":        { "type": "string" },
                "githublink":   { "type": "string" },
                "linkedinlink": { "type": "string" },
                "phone":        { "type": "string" },
                "skills":       { "type": "string" },
                "education":    { "type": "string" },
                "experience":   { "type": "string" },
                "projects":     { "type": "string" }
              },
              "required": ["name","email","githublink","linkedinlink","phone","skills","education","experience","projects"]
            }
            """.trimIndent()
        ).jsonObject
    }

    /** Matches `parse_jd_node.py::jobDisc`. */
    val jobDescription: JsonObject by lazy {
        json.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "job_title":        { "type": "string" },
                "skills_reqd":      { "type": "string" },
                "experience_reqd":  { "type": "string" },
                "edu_reqd":         { "type": "string" },
                "responsibilities": { "type": "string" }
              },
              "required": ["job_title","skills_reqd","experience_reqd","edu_reqd","responsibilities"]
            }
            """.trimIndent()
        ).jsonObject
    }

    /** Matches `match_score_node.py::MatchResult`, plus the d7 breakdown. */
    val matchResult: JsonObject by lazy {
        json.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "score":   { "type": "number" },
                "summary": { "type": "string" },
                "breakdown": {
                  "type": "object",
                  "properties": {
                    "skills":     { "type": "integer" },
                    "experience": { "type": "integer" },
                    "education":  { "type": "integer" }
                  },
                  "required": ["skills","experience","education"]
                }
              },
              "required": ["score","summary"]
            }
            """.trimIndent()
        ).jsonObject
    }
}
