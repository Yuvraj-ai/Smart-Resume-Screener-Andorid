package com.yuvraj.resumescreener.domain.ai

import com.yuvraj.resumescreener.data.remote.LlmException
import com.yuvraj.resumescreener.data.remote.ResponseSchemas
import com.yuvraj.resumescreener.domain.model.JobDescription
import com.yuvraj.resumescreener.domain.model.MatchResult
import com.yuvraj.resumescreener.domain.model.Resume
import com.yuvraj.resumescreener.domain.model.ScreeningOutcome
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The three pipeline steps, expressed once against [StructuredLlmClient].
 *
 * This is what keeps the pipeline provider-agnostic. Swapping Gemini for a
 * self-hosted Gemma changes only which client is passed in here; the prompts,
 * the schemas and the order are identical, and they are the parts that carry
 * product meaning.
 */
@Singleton
class ScreeningService @Inject constructor(
    private val client: StructuredLlmClient,
    private val parseModel: String,
    private val scoreModel: String,
    private val apiKey: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun extractResume(resumeText: String): Resume = decode(
        client.generate(parseModel, Prompts.extractResume(resumeText), ResponseSchemas.resume, apiKey),
        Resume.serializer(),
    )

    suspend fun extractJobDescription(jdText: String): JobDescription = decode(
        client.generate(
            parseModel, Prompts.extractJobDescription(jdText),
            ResponseSchemas.jobDescription, apiKey,
        ),
        JobDescription.serializer(),
    )

    suspend fun score(resume: Resume, job: JobDescription): MatchResult = decode(
        client.generate(scoreModel, Prompts.score(resume, job), ResponseSchemas.matchResult, apiKey),
        MatchResult.serializer(),
    )

    /** All three steps, in source order. */
    suspend fun run(resumeText: String, jdText: String): ScreeningOutcome {
        val resume = extractResume(resumeText)
        val job = extractJobDescription(jdText)
        val match = score(resume, job)
        return ScreeningOutcome(resume, job, match)
    }

    /**
     * Deserialize the model's JSON, tolerating prose or fences around it.
     *
     * A reasoning model served over an OpenAI-compatible endpoint may emit
     * visible thinking before the object even though the schema constrained the
     * answer, so extracting the first balanced object keeps a cosmetic wrapper
     * from surfacing to the user as a failure.
     */
    private fun <T> decode(raw: String, serializer: kotlinx.serialization.KSerializer<T>): T {
        val body = com.yuvraj.resumescreener.data.remote.JsonExtraction.firstObject(raw)
            ?: throw LlmException.MalformedResponse("no JSON object in the response")
        return runCatching { json.decodeFromString(serializer, body) }
            .getOrElse {
                throw LlmException.MalformedResponse(it.message ?: "did not match the schema")
            }
    }
}
