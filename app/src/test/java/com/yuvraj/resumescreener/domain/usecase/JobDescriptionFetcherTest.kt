package com.yuvraj.resumescreener.domain.usecase

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Covers the job-description-from-link feature. */
class JobDescriptionFetcherTest {

    private lateinit var server: MockWebServer

    private lateinit var httpClient: OkHttpClient

    @Before
    fun setUp() {
        // A real HTTPS server, so the https-only policy in the fetcher is
        // exercised as written instead of being bypassed for tests.
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        server = MockWebServer()
        server.useHttps(okhttp3.tls.HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build().sslSocketFactory(), false)
        server.start()
        val trusted = okhttp3.tls.HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        httpClient = OkHttpClient.Builder()
            .sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fetcher() = JobDescriptionFetcher(httpClient)

    @Test
    fun `html is reduced to visible text`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(
                    """
                    <html><head><style>.a{color:red}</style>
                    <script>console.log("tracking pixel")</script></head>
                    <body>
                      <h1>Staff Product Designer</h1>
                      <p>Lead design across the enterprise surface.</p>
                      <p>Requirements:</p>
                      <ul><li>Figma</li><li>Design systems</li></ul>
                    </body></html>
                    """.trimIndent()
                )
        )
        val text = fetcher().fetch(server.url("/job").toString())

        assertTrue(text.contains("Staff Product Designer"))
        assertTrue(text.contains("Figma"))
        // Script and style bodies must not leak into the prompt.
        assertTrue("script content leaked", !text.contains("tracking pixel"))
        assertTrue("style content leaked", !text.contains("color:red"))
        assertTrue("tags leaked", !text.contains("<"))
    }

    @Test
    fun `html entities are decoded`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<p>Senior &amp; Staff &nbsp; Engineer</p>")
        )
        val text = fetcher().fetch(server.url("/j").toString())
        // Collapse runs of whitespace: &nbsp; and a literal space both become
        // one, and the exact count is not something callers depend on.
        assertEquals("Senior & Staff Engineer", text.replace(Regex("\\s+"), " "))
    }

    @Test
    fun `a pdf served as text-html is routed to the pdf path`() = runTest {
        // Many job boards mislabel a PDF as text/html, so the magic bytes decide.
        // The body is deliberately not a valid PDF: routing to the PDF path is
        // exactly what an Unreadable error proves, where the HTML path would
        // have happily returned the tag soup as text.
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("%PDF-1.4\nnot actually a parseable document")
        )
        val error = runCatching { fetcher().fetch(server.url("/posting.pdf").toString()) }.exceptionOrNull()
        assertTrue(
            "expected the PDF path to be taken, got $error",
            error is JdFetchError.Unreadable,
        )
    }

    @Test
    fun `http links are rejected`() = runTest {
        val error = runCatching { fetcher().fetch("http://example.com/job") }.exceptionOrNull()
        assertTrue(error is JdFetchError.InsecureUrl)
    }

    @Test
    fun `non urls are rejected`() = runTest {
        val error = runCatching { fetcher().fetch("just some pasted text") }.exceptionOrNull()
        assertTrue(error is JdFetchError.NotAUrl)
    }

    @Test
    fun `a 404 is reported rather than returning html`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        val error = runCatching { fetcher().fetch(server.url("/gone").toString()) }.exceptionOrNull()
        assertTrue(error is JdFetchError.Unreachable)
    }

    @Test
    fun `an empty page is reported`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<html><body><div>  </div></body></html>")
        )
        val error = runCatching { fetcher().fetch(server.url("/blank").toString()) }.exceptionOrNull()
        assertTrue(error is JdFetchError.Empty)
    }
}
