package com.yuvraj.resumescreener.data.remote

import com.yuvraj.resumescreener.domain.ai.ScreeningService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live check of the OpenAI-compatible path against a real Gemma endpoint.
 *
 * Skipped unless both OPENAI_BASE_URL and OPENAI_API_KEY are set:
 *
 *   OPENAI_BASE_URL=https://host/v1 OPENAI_API_KEY=... \
 *     ./gradlew testDebugUnitTest --tests '*OpenAiCompatibleLiveTest*'
 *
 * This exists because the failures it catches are invisible to a mock. A mock
 * cannot omit a field the way a real model can, and that is exactly what
 * happened: with `breakdown` left out of the schema's required list, the live
 * endpoint returned a score and summary with no breakdown at all, and
 * MatchResult's default silently produced three zeroed bars in the UI.
 */
class OpenAiCompatibleLiveTest {

    private fun baseUrl(): String? =
        System.getenv("OPENAI_BASE_URL")?.takeIf { it.isNotBlank() }

    private fun apiKey(): String? =
        System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() }

    private val model: String
        get() = System.getenv("OPENAI_MODEL")?.takeIf { it.isNotBlank() } ?: "gemma-4-31B-it"

    private val resumeText = """
        Jane Doe, Senior Backend Engineer, jane.doe@example.com, +1 415 555 0142.
        Experience: Staff Software Engineer at Northwind Systems 2021-present,
        led the migration of a monolithic billing service to 12 Kotlin
        microservices on Postgres and cut p99 latency 43 percent. Backend
        Engineer at Cobalt Labs 2018-2021, built a Python ingestion pipeline
        handling 40k events per second via Kafka.
        Skills: Python, Kotlin, PostgreSQL, Kafka, Docker, Kubernetes, AWS.
        Education: BSc Computer Science, University of Washington 2017.
    """.trimIndent()

    private val jdText = """
        Staff Backend Engineer, Platform. Six or more years building distributed
        backend systems in production. Deep experience with PostgreSQL and Kafka.
        Strong Python or Kotlin, JVM preferred. Experience leading technical
        design across multiple teams. Kubernetes and infrastructure-as-code.
    """.trimIndent()

    private fun service(): ScreeningService = ScreeningService(
        client = OpenAiCompatibleClient(
            httpClient = okhttp3.OkHttpClient(),
            baseUrl = baseUrl()!!,
        ),
        parseModel = model,
        scoreModel = model,
        apiKey = apiKey()!!,
    )

    /** Skip rather than fail: quota and transient 5xx are key properties, not defects. */
    private suspend fun withService(block: suspend ScreeningService.() -> Unit) {
        if (baseUrl() == null || apiKey() == null) return
        try {
            service().block()
        } catch (e: LlmException.RateLimited) {
            println("SKIP quota: ${e.message}")
        } catch (e: LlmException.ServerUnavailable) {
            println("SKIP server ${e.code}: ${e.message}")
        } catch (e: LlmException.Network) {
            println("SKIP network: ${e.message}")
        }
    }

    @Test
    fun `the endpoint lists models`() = runTest { withService {
        val models = OpenAiCompatibleClient(okhttp3.OkHttpClient(), baseUrl()!!).listModels(apiKey()!!)
        println("LIVE models: $models")
        assertTrue("expected the configured model to be offered: $models", models.contains(model))
    } }

    @Test
    fun `resume extraction returns every field`() = runTest { withService {
        val resume = extractResume(resumeText)
        println("LIVE name=${resume.name} phone=${resume.phone}")
        assertTrue("name should be populated", resume.name.isNotBlank())
        assertTrue(
            "phone must survive as a string, got '${resume.phone}'",
            resume.phone.isNotBlank(),
        )
        assertTrue("education should be populated", resume.education.isNotBlank())
    } }

    @Test
    fun `the scoring call returns a populated breakdown`() = runTest { withService {
        val resume = extractResume(resumeText)
        val job = extractJobDescription(jdText)
        val match = score(resume, job)

        println("LIVE score=${match.score} breakdown=${match.breakdown}")

        assertTrue("score ${match.score} outside 1-10", match.score in 1.0..10.0)
        assertTrue(
            "the breakdown must be populated, not defaulted to zero: ${match.breakdown}",
            match.breakdown.skills in 1..10 &&
                match.breakdown.experience in 1..10 &&
                match.breakdown.education in 1..10,
        )
        assertTrue("summary should be substantive", match.summary.length > 20)

        // Strong fit for this fixture; a low score would suggest the rubric or
        // the structured inputs broke in translation.
        assertTrue("expected a strong fit, got ${match.score}", match.score >= 6.0)
    } }
}
