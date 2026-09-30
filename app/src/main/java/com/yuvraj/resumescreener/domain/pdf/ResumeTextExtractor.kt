package com.yuvraj.resumescreener.domain.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Why a PDF could not be turned into text. Reported per file, never fatal to the batch. */
sealed class PdfExtractionError(message: String) : Exception(message) {
    class TooLarge(val limitMb: Int) : PdfExtractionError("File exceeds the ${limitMb}MB limit.")
    class Unreadable(val detail: String) :
        PdfExtractionError("Could not read this PDF: $detail")
    class Empty : PdfExtractionError("No selectable text found. The PDF may be a scan.")
}

/**
 * Replaces PyMuPDF from the Python source.
 *
 * PDFs are untrusted input from a file picker, so extraction is bounded and
 * every failure mode is a value rather than an exception escaping into the UI.
 *
 * Split in two on purpose: [extract] owns the ContentResolver plumbing and the
 * size cap, while [extractFromBytes] owns the actual parsing. That seam is what
 * lets the parsing paths be tested against real PDFs without a device.
 */
@Singleton
class ResumeTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun extract(uri: Uri): String = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        val resolver = context.contentResolver

        val declaredSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        if (declaredSize != null && declaredSize > MAX_BYTES) {
            throw PdfExtractionError.TooLarge(MAX_MB)
        }

        val stream = resolver.openInputStream(uri)
            ?: throw PdfExtractionError.Unreadable("could not open the file")

        stream.use { input ->
            // The declared size can be missing or wrong, so cap the read itself.
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                total += read
                if (total > MAX_BYTES) throw PdfExtractionError.TooLarge(MAX_MB)
                buffer.write(chunk, 0, read)
            }
            extractFromBytes(buffer.toByteArray())
        }
    }

    /** Parse an in-memory PDF. Synchronous; the caller supplies the bytes. */
    internal fun extractFromBytes(bytes: ByteArray): String {
        val text = try {
            bytes.inputStream().use { stream ->
                PDDocument.load(stream).use { doc -> PDFTextStripper().getText(doc) }
            }
        } catch (e: IOException) {
            throw PdfExtractionError.Unreadable(e.message ?: "malformed file")
        } catch (e: IllegalArgumentException) {
            throw PdfExtractionError.Unreadable("not a valid PDF")
        } catch (e: RuntimeException) {
            // PDFBox throws assorted unchecked types on structural damage.
            throw PdfExtractionError.Unreadable(e.message ?: "malformed file")
        }

        if (text.isBlank()) throw PdfExtractionError.Empty()
        return text.trim()
    }

    companion object {
        const val MAX_MB = 20
        const val MAX_BYTES = MAX_MB * 1024L * 1024L
    }
}
