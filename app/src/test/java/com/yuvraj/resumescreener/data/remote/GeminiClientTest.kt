package com.yuvraj.resumescreener.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Covers the error taxonomy the UI branches on, and confirms the key travels in
 * the header rather than the URL.
 */
class GeminiClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Client pointed at the mock server instead of the real Gemini host. */
    private fun client(): GeminiClient {
        val http = OkHttpClient.Builder()
            .callTimeout(2, TimeUnit.SECONDS)
            .build()
        return GeminiClient.forBaseUrl(http, server.url("/v1beta/").toString())
    }

    private fun successBody(payload: String) = """
        {"candidates":[{"content":{"parts":[{"text":${quote(payload)}}]},"finishReason":"STOP"}]}
    """.trimIndent()

    private fun quote(s: String) = kotlinx.serialization.json.JsonPrimitive(s).toString()

    @Test
    fun `200 returns the deserialized payload`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody("""{"name":"Jane Doe"}""")))
        val resume = client().extractResume("Jane Doe", "gemini-2.5-flash", "test-key")
        assertEquals("Jane Doe", resume.name)
    }

    @Test
    fun `the key is sent as a header and never in the url`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody("""{"name":"x"}""")))
        client().extractResume("text", "gemini-2.5-flash", "super-secret-key")

        val recorded = server.takeRequest()
        assertEquals("super-secret-key", recorded.getHeader("x-goog-api-key"))
        assertTrue(
            "the key must not appear in the request URL",
            !recorded.path.orEmpty().contains("super-secret-key"),
        )
    }

    @Test
    fun `400 maps to InvalidKey`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"API key not valid"}}"""))
        val error = runCatching { client().extractResume("t", "m", "bad") }.exceptionOrNull()
        assertTrue(error is GeminiException.InvalidKey)
    }

    @Test
    fun `403 maps to InvalidKey`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
        val error = runCatching { client().extractResume("t", "m", "bad") }.exceptionOrNull()
        assertTrue(error is GeminiException.InvalidKey)
    }

    @Test
    fun `429 maps to RateLimited`() = runTest {
        // Retryable, so every attempt is answered; an empty queue would hang.
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429).setBody("{}")) }
        val error = runCatching { client().extractResume("t", "m", "k") }.exceptionOrNull()
        assertTrue(error is GeminiException.RateLimited)
    }

    @Test
    fun `5xx maps to ServerUnavailable`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503).setBody("{}")) }
        val error = runCatching { client().extractResume("t", "m", "k") }.exceptionOrNull()
        assertTrue(error is GeminiException.ServerUnavailable)
    }

    @Test
    fun `200 with no candidates is Malformed, not a silent success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"candidates":[]}"""))
        val error = runCatching { client().extractResume("t", "m", "k") }.exceptionOrNull()
        assertTrue(error is GeminiException.MalformedResponse)
    }

    @Test
    fun `a blocked prompt is reported as Blocked`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
        )
        val error = runCatching { client().extractResume("t", "m", "k") }.exceptionOrNull()
        assertTrue(error is GeminiException.Blocked)
    }

    @Test
    fun `a retryable failure is retried and can then succeed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody("""{"name":"Recovered"}""")))
        val resume = client().extractResume("t", "m", "k")
        assertEquals("Recovered", resume.name)
        assertEquals("should have made two attempts", 2, server.requestCount)
    }

    @Test
    fun `an invalid key is not retried`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(400).setBody("{}")) }
        runCatching { client().extractResume("t", "m", "bad") }
        assertEquals(
            "a rejected key will never succeed on retry",
            1, server.requestCount,
        )
    }

    @Test
    fun `verifyKey lists models on success`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"models":[{"name":"models/gemini-2.5-flash"}]}""")
        )
        val models = client().verifyKey("good-key")
        assertEquals(listOf("models/gemini-2.5-flash"), models)
    }

    @Test
    fun `the base url is overridable so tests never hit the real api`() {
        val http = OkHttpClient.Builder().build()
        val custom = GeminiClient.forBaseUrl(http, "https://example.test/v1beta/")
        assertTrue(custom.baseUrlForTest().startsWith("https://example.test"))
    }

}
