package com.yuvraj.resumescreener.domain.usecase

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Why a job-description link could not be turned into text. */
sealed class JdFetchError(message: String) : Exception(message) {
    class NotAUrl : JdFetchError("That does not look like a web link.")
    class InsecureUrl : JdFetchError("Only https links are accepted.")
    class TooLarge(val limitMb: Int) : JdFetchError("The page or PDF exceeds the ${limitMb}MB limit.")
    class Unreachable(val detail: String) : JdFetchError("Could not open that link: $detail")
    class UnsupportedContentType(val type: String?) :
        JdFetchError("That link returned ${type ?: "an unknown content type"}. Only web pages and PDFs are supported.")
    class Unreadable(val detail: String) :
        JdFetchError("The text could not be extracted from that link: $detail")
    class Empty : JdFetchError("No readable job description text was found at that link.")
}

/**
 * Fetches a job description from a pasted link.
 *
 * Job postings are published both ways, so this handles two content types:
 * HTML pages, which are stripped down to their visible text, and PDFs, which
 * go through the same [com.tom_roush.pdfbox] path as resumes.
 *
 * The link is untrusted input, so the scheme is restricted to https and the
 * response is size-capped. There is no SSRF concern in the usual sense: the
 * request comes from the user's own device to a link they chose.
 */
@Singleton
class JobDescriptionFetcher @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    suspend fun fetch(rawUrl: String): String = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (url.isEmpty()) throw JdFetchError.NotAUrl()

        val parsed = runCatching { java.net.URI(url) }.getOrNull()
            ?: throw JdFetchError.NotAUrl()
        if (!parsed.scheme.equals("https", ignoreCase = true)) {
            throw JdFetchError.InsecureUrl()
        }
        if (parsed.host.isNullOrBlank()) throw JdFetchError.NotAUrl()

        val request = Request.Builder()
            .url(parsed.toString())
            // Identify honestly rather than as a desktop browser.
            .header("User-Agent", "ResumeScreener/0.1 (Android)")
            .header("Accept", "text/html,application/pdf;q=0.9,*/*;q=0.5")
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw JdFetchError.Unreachable(e.message ?: "network error")
        }

        response.use {
            if (!it.isSuccessful) {
                throw JdFetchError.Unreachable("HTTP ${it.code}")
            }
            val body = it.body ?: throw JdFetchError.Unreachable("empty response")
            if (body.contentLength() > MAX_BYTES) {
                throw JdFetchError.TooLarge(MAX_MB)
            }
            val bytes = body.byteStream().use { stream ->
                // Guard against a lying Content-Length by capping the read too.
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = stream.read(chunk)
                    if (read == -1) break
                    total += read
                    if (total > MAX_BYTES) throw JdFetchError.TooLarge(MAX_MB)
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }

            val contentType = it.header("Content-Type").orEmpty()
            val text = when {
                contentType.contains("pdf", ignoreCase = true) || looksLikePdf(bytes) ->
                    extractPdf(bytes)
                else -> extractHtml(String(bytes, Charsets.UTF_8))
            }

            if (text.isBlank()) throw JdFetchError.Empty()
            text
        }
    }

    /** Trust the magic bytes too: many job boards mislabel a PDF as text/html. */
    private fun looksLikePdf(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()

    private fun extractPdf(bytes: ByteArray): String = try {
        bytes.inputStream().use { PDDocument.load(it).use { PDFTextStripper().getText(it) } }
    } catch (e: IOException) {
        throw JdFetchError.Unreadable("the PDF could not be parsed")
    }

    /**
     * Reduce HTML to visible text without pulling in a parser dependency.
     *
     * Drops script and style bodies, then all remaining tags, and decodes the
     * handful of entities that actually show up in job postings.
     */
    private fun extractHtml(html: String): String = html
        .replace(SCRIPT_OR_STYLE, " ")
        .replace(COMMENT, " ")
        .replace(TAG, " ")
        .replace(BLOCK_BREAK, "\n")
        .replace(ENTITIES) { decodeEntity(it.value) }
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
        .replace(EXCESS_BLANK_LINES, "\n")
        .trim()

    /** Only the entities that actually appear in job postings. */
    private fun decodeEntity(entity: String): String = when (entity) {
        "&nbsp;" -> " "
        "&amp;" -> "&"
        "&lt;" -> "<"
        "&gt;" -> ">"
        "&quot;" -> "\""
        "&#39;", "&apos;" -> "'"
        "&hellip;" -> "\u2026"
        "&mdash;" -> "\u2014"
        "&ndash;" -> "\u2013"
        "&rsquo;", "&lsquo;" -> "\u2018"
        "&rdquo;", "&ldquo;" -> "\u201C"
        else -> entity
    }

    companion object {
        const val MAX_MB = 20
        const val MAX_BYTES = MAX_MB * 1024L * 1024L

        private val SCRIPT_OR_STYLE = Regex(
            "<(script|style|noscript|svg)[^>]*>.*?</\\1>", RegexOption.DOT_MATCHES_ALL
        )
        private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        private val TAG = Regex("<[^>]+>")
        private val BLOCK_BREAK = Regex("</(p|div|li|tr|h[1-6]|section|article|br)\\s*/?>")
        private val ENTITIES = Regex("&(nbsp|amp|lt|gt|quot|#39|apos|hellip|mdash|ndash|quot|rsquo|lsquo|rdquo|ldquo);")
        private val EXCESS_BLANK_LINES = Regex("\\n{3,}")
    }
}
