package com.yuvraj.resumescreener.domain.pdf

import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The PDF extractor handles untrusted input from a file picker, and had no
 * tests at all. These cover the paths that matter: a readable document, a
 * scan with no text layer, and structurally broken bytes.
 *
 * Under Robolectric because PDFBox-Android resolves its bundled font resources
 * through an Android asset loader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResumeTextExtractorTest {

    private lateinit var extractor: ResumeTextExtractor

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
        extractor = ResumeTextExtractor(ApplicationProvider.getApplicationContext())
    }

    private fun pdfWithText(vararg lines: String): ByteArray {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.LETTER)
        doc.addPage(page)
        PDPageContentStream(doc, page).use { stream ->
            stream.beginText()
            stream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11f)
            stream.newLineAtOffset(56f, 700f)
            lines.forEach { line ->
                stream.showText(line)
                stream.newLineAtOffset(0f, -16f)
            }
            stream.endText()
        }
        val out = java.io.ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    /**
     * A page carrying graphics but no text operators: what the extractor sees
     * when a resume is a scan. The glyphs are pixels in an image, not text, so
     * there is nothing for a text stripper to read.
     */
    private fun scannedPdf(): ByteArray {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.LETTER)
        doc.addPage(page)
        PDPageContentStream(doc, page).use { stream ->
            stream.setNonStrokingColor(0f, 0f, 0f)
            stream.addRect(60f, 700f, 320f, 12f)
            stream.addRect(60f, 660f, 480f, 10f)
            stream.addRect(60f, 630f, 400f, 10f)
            stream.fill()
        }
        val out = java.io.ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    @Test
    fun `a readable pdf yields its text`() {
        val text = extractor.extractFromBytes(
            pdfWithText("Jane Doe", "Senior Backend Engineer", "jane.doe@example.com")
        )
        assertTrue("expected the name in: $text", text.contains("Jane Doe"))
        assertTrue(text.contains("Senior Backend Engineer"))
        assertTrue(text.contains("jane.doe@example.com"))
    }

    @Test
    fun `the bundled fixture resume parses`() {
        val fixture = java.io.File("src/test/resources/sample_resume.pdf")
        assertTrue("missing fixture ${fixture.path}", fixture.exists())
        val text = extractor.extractFromBytes(fixture.readBytes())
        assertTrue("expected the candidate name in: $text", text.contains("Jane Doe"))
        assertTrue("expected the phone in: $text", text.contains("415 555 0142"))
    }

    @Test
    fun `a scan with no text layer reports Empty rather than returning nothing`() {
        // This is the case that matters in production: a scanned resume has no
        // text layer, and silently returning "" would send an empty prompt.
        val error = runCatching { extractor.extractFromBytes(scannedPdf()) }.exceptionOrNull()
        assertTrue("expected Empty, got $error", error is PdfExtractionError.Empty)
        assertTrue(error!!.message!!.contains("scan"))
    }

    @Test
    fun `random bytes are reported as unreadable`() {
        val error = runCatching {
            extractor.extractFromBytes("this is definitely not a pdf".toByteArray())
        }.exceptionOrNull()
        assertTrue("expected Unreadable, got $error", error is PdfExtractionError.Unreadable)
    }

    @Test
    fun `an empty file is reported as unreadable`() {
        val error = runCatching { extractor.extractFromBytes(ByteArray(0)) }.exceptionOrNull()
        assertTrue("expected Unreadable, got $error", error is PdfExtractionError.Unreadable)
    }

    @Test
    fun `a truncated pdf header does not crash the batch`() {
        // Only the header, no body: the shape a half-uploaded file takes.
        val truncated = "%PDF-1.7\n".toByteArray()
        val error = runCatching { extractor.extractFromBytes(truncated) }.exceptionOrNull()
        assertTrue("expected an error, got $error", error is PdfExtractionError)
    }

    @Test
    fun `a document with a blank page reports Empty`() {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.LETTER))
        val out = java.io.ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        val error = runCatching { extractor.extractFromBytes(out.toByteArray()) }.exceptionOrNull()
        assertTrue("expected Empty, got $error", error is PdfExtractionError.Empty)
    }

    @Test
    fun `the size cap is what we claim`() {
        assertEquals(20, ResumeTextExtractor.MAX_MB)
        assertEquals(20L * 1024 * 1024, ResumeTextExtractor.MAX_BYTES)
    }

    @Test
    fun `every failure is a PdfExtractionError so the batch can isolate it`() {
        // The pipeline catches PdfExtractionError per file; a raw parser
        // exception would abort the whole batch instead of skipping one file.
        listOf(
            "garbage".toByteArray(),
            ByteArray(0),
            "%PDF-1.7".toByteArray(),
        ).forEach { bytes ->
            val error = runCatching { extractor.extractFromBytes(bytes) }.exceptionOrNull()
            assertTrue(
                "leaked ${error?.let { it::class.simpleName }} for ${bytes.size} bytes",
                error is PdfExtractionError,
            )
        }
    }
}
