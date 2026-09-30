package com.yuvraj.resumescreener.data.remote

import com.yuvraj.resumescreener.domain.ai.LlmProvider
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the second provider. The bugs that mattered here were returning the
 * raw `{"choices":...}` envelope instead of the inner JSON, and losing the model
 * id from error messages, so both are pinned.
 */
class OpenAiCompatibleClientTest {

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

    private fun client(base: String = server.url("/v1").toString()) =
        OpenAiCompatibleClient(OkHttpClient(), base)

    /** The shape a real endpoint returns: content wrapped in the choices array. */
    private fun chatReply(content: String) = """
        {"id":"x","object":"chat.completion","model":"gemma-4-31B-it",
         "choices":[{"index":0,"finish_reason":"stop",
         "message":{"role":"assistant","content":${quote(content)}}}]}
    """.trimIndent()

    private fun quote(s: String) = kotlinx.serialization.json.JsonPrimitive(s).toString()

    @Test
    fun `generate returns the assistant content, not the envelope`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chatReply("""{"name":"Jane"}""")))
        val out = client().generate("gemma-4-31B-it", "prompt", ResponseSchemas.resume, "k")
        assertEquals(
            "must unwrap choices[0].message.content",
            """{"name":"Jane"}""",
            out,
        )
    }

    @Test
    fun `it posts to chat completions with bearer auth`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chatReply("""{"a":"b"}""")))
        client().generate("gemma-4-31B-it", "p", ResponseSchemas.resume, "secret-token")

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer secret-token", recorded.getHeader("Authorization"))
        assertFalse("key must not appear in the URL", recorded.path!!.contains("secret-token"))
    }

    @Test
    fun `it sends a json_schema response format`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chatReply("""{"a":"b"}""")))
        client().generate("gemma-4-31B-it", "p", ResponseSchemas.matchResult, "k")

        val body = server.takeRequest().body.readUtf8()
        assertTrue("json_schema mode must be requested", body.contains("\"json_schema\""))
        assertTrue("strict should be set", body.contains("\"strict\":true"))
        assertTrue("the d7 breakdown schema should be sent", body.contains("breakdown"))
        assertTrue("the model id should be sent", body.contains("gemma-4-31B-it"))
    }

    @Test
    fun `a base url with a trailing slash does not double up`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chatReply("""{"a":"b"}""")))
        client(server.url("/v1").toString().trimEnd('/') + "/")
            .generate("m", "p", ResponseSchemas.resume, "k")
        assertEquals("/v1/chat/completions", server.takeRequest().path)
    }

    @Test
    fun `401 maps to InvalidKey`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val e = runCatching { client().generate("m", "p", ResponseSchemas.resume, "k") }.exceptionOrNull()
        assertTrue(e is LlmException.InvalidKey)
    }

    @Test
    fun `404 on a model names that model`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"error":{"message":"The model does not exist"}}""")
        )
        val e = runCatching { client().generate("gemma-x", "p", ResponseSchemas.resume, "k") }.exceptionOrNull()
        assertTrue(e is LlmException.ModelUnavailable)
        assertTrue("should name the model", e!!.message!!.contains("gemma-x"))
    }

    @Test
    fun `404 listing models points at the base url`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        val e = runCatching { client().listModels("k") }.exceptionOrNull()
        // vLLM and friends often lack /models; the message must say so.
        assertTrue(e is LlmException.Unreachable)
        assertTrue(e!!.message!!.contains("base URL"))
    }

    @Test
    fun `429 maps to RateLimited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        val e = runCatching { client().generate("m", "p", ResponseSchemas.resume, "k") }.exceptionOrNull()
        assertTrue(e is LlmException.RateLimited)
    }

    @Test
    fun `an empty choices array is malformed, not a silent success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[]}"""))
        val e = runCatching { client().generate("m", "p", ResponseSchemas.resume, "k") }.exceptionOrNull()
        assertTrue(e is LlmException.MalformedResponse)
    }

    @Test
    fun `a blank base url is rejected at construction, not as a crash later`() = runTest {
        // Building a Request from a relative URL throws IllegalArgumentException,
        // which would reach the user as a crash rather than a settings hint.
        val e = runCatching { client("") }.exceptionOrNull()
        assertTrue(
            "expected a construction-time rejection, got $e",
            e is IllegalArgumentException,
        )
        assertTrue(e!!.message!!.contains("Base URL"))
    }

    @Test
    fun `listModels reads the data array`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":[{"id":"gemma-4-31B-it"},{"id":"qwen3-32b"}]}""")
        )
        assertEquals(listOf("gemma-4-31B-it", "qwen3-32b"), client().listModels("k"))
    }

    @Test
    fun `normaliseBaseUrl adds a scheme when one is missing`() {
        assertEquals("https://my-host:8000/v1", OpenAiCompatibleClient.normaliseBaseUrl("my-host:8000/v1"))
        assertEquals("https://h/v1", OpenAiCompatibleClient.normaliseBaseUrl("https://h/v1/"))
        assertEquals("", OpenAiCompatibleClient.normaliseBaseUrl("  "))
    }

    @Test
    fun `both providers report distinct ids`() {
        // Guards against a copy-paste making the two indistinguishable.
        assertTrue(LlmProvider.GEMINI.id != LlmProvider.OPENAI_COMPATIBLE.id)
    }

    @Test
    fun `a fenced response still yields the inner object`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(chatReply("Here you go:\n```json\n{\"name\":\"Fenced\"}\n```"))
        )
        val out = client().generate("m", "p", ResponseSchemas.resume, "k")
        assertEquals("""{"name":"Fenced"}""", JsonExtraction.firstObject(out))
    }
}
