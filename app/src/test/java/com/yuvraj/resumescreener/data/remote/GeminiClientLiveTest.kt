package com.yuvraj.resumescreener.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live end-to-end check against the real Gemini API.
 *
 * Every other test uses a mock server, which proves the request shape but not
 * that Gemini honours the responseSchema, nor that the ported scoring prompt
 * still produces something usable. Only the live API can answer that.
 *
 * Skipped unless GEMINI_API_KEY is set, so the normal suite stays hermetic:
 *
 *   GEMINI_API_KEY=... ./gradlew testDebugUnitTest --tests '*Live*'
 *
 * The key is read from the environment and never written to disk or logs.
 *
 * Quota walls and retired models cause a skip, not a failure: both are
 * properties of the key rather than defects, and a red suite here would train
 * people to ignore this test. That distinction matters in practice, because
 * it is exactly how `gemini-2.5-pro` was caught retiring.
 */
class GeminiClientLiveTest {

    private fun key(): String? =
        System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }

    private val client by lazy { GeminiClient(GeminiClient.defaultHttpClient()) }

    /** The app's own path: client plus schema plus deserialization. */
    private fun service(parseModel: String, scoreModel: String) =
        com.yuvraj.resumescreener.domain.ai.ScreeningService(
            client = client,
            parseModel = parseModel,
            scoreModel = scoreModel,
            apiKey = key().orEmpty(),
        )

    private val resumeText = """
        Jane Doe, Senior Backend Engineer, jane.doe@example.com, +1 415 555 0142.
        Experience: Staff Software Engineer at Northwind Systems 2021-present,
        led the migration of a monolithic billing service to 12 Kotlin
        microservices on Postgres and cut p99 latency 43 percent. Backend
        Engineer at Cobalt Labs 2018-2021, built a Python ingestion pipeline
        handling 40k events per second via Kafka.
        Skills: Python, Kotlin, PostgreSQL, Kafka, Docker, Kubernetes, AWS,
        Terraform, GraphQL.
        Education: BSc Computer Science, University of Washington 2017.
    """.trimIndent()

    private val jobText = """
        Staff Backend Engineer, Platform. Six or more years building distributed
        backend systems in production. Deep experience with PostgreSQL and Kafka.
        Strong Python or Kotlin, JVM preferred. Experience leading technical
        design across multiple teams. Kubernetes and infrastructure-as-code.
    """.trimIndent()

    /** Runs [block], skipping rather than failing on quota or a retired model. */
    private inline fun runOrSkip(block: () -> Unit) {
        if (key() == null) return
        try {
            block()
        } catch (e: LlmException.RateLimited) {
            println("SKIP quota: ${e.message}")
        } catch (e: LlmException.ModelUnavailable) {
            println("SKIP model unavailable: ${e.message}")
        }
    }

    @Test
    fun `the key authenticates against the real api`() = runTest {
        runOrSkip {
            val models = client.listModels(key()!!)
            assertTrue("expected some models back", models.isNotEmpty())
            assertTrue(
                "the parsing model should be available",
                models.any { it.contains("gemini-2.5-flash") },
            )
        }
    }

    @Test
    fun `the default models still exist`() = runTest {
        runOrSkip {
            val models = client.listModels(key()!!).map { it.substringAfterLast('/') }
            // The defaults are rolling aliases precisely so a retired pinned
            // version cannot break the app, so assert they are present.
            assertTrue(
                "gemini-flash-latest missing from $models",
                models.any { it.startsWith("gemini-flash") },
            )
            assertTrue(
                "no pro-tier model available in $models",
                models.any { it.contains("pro") },
            )
        }
    }

    @Test
    fun `resume extraction honours the responseSchema`() = runTest {
        runOrSkip {
            val resume = service("gemini-flash-latest", "gemini-pro-latest").extractResume(resumeText)
            println("LIVE resume.name=${resume.name} phone=${resume.phone}")
            assertTrue("name should be populated, got '${resume.name}'", resume.name.isNotBlank())
            assertTrue(
                "the phone must survive as a string, got '${resume.phone}'",
                resume.phone.isNotBlank(),
            )
            // The schema demands all nine fields. A blank one means the model
            // ignored responseSchema and we are silently reading defaults.
            assertTrue("skills should be populated", resume.skills.isNotBlank())
            assertTrue("education should be populated", resume.education.isNotBlank())
        }
    }

    @Test
    fun `job description extraction honours the responseSchema`() = runTest {
        runOrSkip {
            val job = service("gemini-flash-latest", "gemini-pro-latest").extractJobDescription(jobText)
            println("LIVE job.title=${job.jobTitle}")
            assertTrue("job_title should be populated", job.jobTitle.isNotBlank())
            assertTrue("skills_reqd should be populated", job.skillsRequired.isNotBlank())
        }
    }

    @Test
    fun `the ported scoring prompt produces a usable result`() = runTest {
        runOrSkip {
            val k = key()!!
            val svc = service("gemini-flash-latest", "gemini-pro-latest")
            val resume = svc.extractResume(resumeText)
            val job = svc.extractJobDescription(jobText)
            val match = svc.score(resume, job)

            println("LIVE score=${match.score} breakdown=${match.breakdown}")
            println("LIVE summary=${match.summary.take(180)}")

            assertTrue(
                "score ${match.score} outside 1-10; the prompt should constrain this",
                match.score in 1.0..10.0,
            )
            assertTrue(
                "the d7 breakdown must come back populated, got ${match.breakdown}",
                match.breakdown.skills in 1..10 &&
                    match.breakdown.experience in 1..10 &&
                    match.breakdown.education in 1..10,
            )
            assertTrue("summary should be written", match.summary.length > 20)

            // This fixture is a strong match: JVM, Kafka, Postgres, leadership.
            // A poor fit would suggest the rubric or the structured inputs
            // broke somewhere in the translation.
            assertTrue(
                "expected a strong fit for this fixture, got ${match.score}",
                match.score >= 6.0,
            )
        }
    }
}
