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

    /**
     * The same path the app uses, so these exercise the client and the schema
     * contract together rather than the transport in isolation.
     */
    private fun service(client: GeminiClient) =
        com.yuvraj.resumescreener.domain.ai.ScreeningService(
            client = client,
            parseModel = "m",
            scoreModel = "m",
            apiKey = "test-key",
        )

    /**
     * A client pointed at the mock server. The base URL is overridden so these
     * tests can never reach the real Gemini host even if a key leaked into CI.
     */
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
        val resume = service(client()).extractResume("Jane Doe")
        assertEquals("Jane Doe", resume.name)
    }

    @Test
    fun `the key is sent as a header and never in the url`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody("""{"name":"x"}""")))
        service(client()).extractResume("text")

        val recorded = server.takeRequest()
        assertEquals("test-key", recorded.getHeader("x-goog-api-key"))
        assertTrue(
            "the key must not appear in the request URL",
            !recorded.path.orEmpty().contains("test-key"),
        )
    }

    @Test
    fun `400 maps to InvalidKey`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"API key not valid"}}"""))
        val error = runCatching { service(client()).extractResume("t") }.exceptionOrNull()
        assertTrue(error is LlmException.InvalidKey)
    }

    @Test
    fun `403 maps to InvalidKey`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
        val error = runCatching { service(client()).extractResume("t") }.exceptionOrNull()
        assertTrue(error is LlmException.InvalidKey)
    }

    @Test
    fun `404 maps to ModelUnavailable and keeps Google's own message`() = runTest {
        // Regression guard: gemini-2.5-pro, the id the Python source pinned,
        // now returns 404 "no longer available to new users". That must not be
        // reported as a malformed response, because the fix is different.
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"error":{"code":404,"message":"This model models/gemini-2.5-pro is no longer available to new users."}}"""
            )
        )
        val error = runCatching { service(client()).extractResume("t") }.exceptionOrNull()
        assertTrue(error is LlmException.ModelUnavailable)
        assertTrue(error!!.message!!.contains("no longer available"))
    }

    @Test
    fun `429 maps to RateLimited`() = runTest {
        // Retryable, so every attempt is answered; an empty queue would hang.
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429).setBody("{}")) }
        val error = runCatching { service(client()).extractResume("t") }.exceptionOrNull()
        assertTrue(error is LlmException.RateLimited)
    }

    @Test
    fun `5xx maps to ServerUnavailable`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503).setBody("{}")) }
        val error = runCatching { service(client()).extractResume("t") }.exceptionOrNull()
        assertTrue(error is LlmException.ServerUnavailable)
    }

    @Test
    fun `200 with no candidates is Malformed, not a silent success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"candidates":[]}"""))
        val error = runCatching { service(client()).extractResume("t") }.exceptionOrNull()
        assertTrue(error is LlmException.MalformedResponse)
    }

    @Test
    fun `a blocked prompt is reported as Blocked`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
        )
        val error = runCatching { service(client()).extractResume("t") }.exceptionOrNull()
        assertTrue(error is LlmException.Blocked)
    }

    @Test
    fun `a retryable failure is retried and can then succeed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody("""{"name":"Recovered"}""")))
        val resume = service(client()).extractResume("t")
        assertEquals("Recovered", resume.name)
        assertEquals("should have made two attempts", 2, server.requestCount)
    }

    @Test
    fun `an invalid key is not retried`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(400).setBody("{}")) }
        runCatching { service(client()).extractResume("t") }
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
        val models = client().listModels("good-key")
        assertEquals(listOf("gemini-2.5-flash"), models)
    }

    @Test
    fun `a 200 with an unparseable envelope is malformed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json at all"))
        val e = runCatching { service(client()).extractResume("t") }.exceptionOrNull()
        assertTrue(e is LlmException.MalformedResponse)
    }

}
