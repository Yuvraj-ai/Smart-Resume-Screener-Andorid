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
    class Unreadable(cause: String) : PdfExtractionError("Could not read this PDF: $cause")
    class Empty : PdfExtractionError("No selectable text found. The PDF may be a scan.")
}

/**
 * Replaces PyMuPDF from the Python source.
 *
 * PDFs are untrusted input from a file picker, so extraction is bounded and
 * every failure mode is a value rather than an exception escaping into the UI.
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

        val text = try {
            resolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    // PDPage has no text accessor; a stripper is the supported
                    // path, and it inserts page breaks for us.
                    PDFTextStripper().getText(doc)
                }
            } ?: throw PdfExtractionError.Unreadable("could not open the file")
        } catch (e: IOException) {
            throw PdfExtractionError.Unreadable(e.message ?: "malformed file")
        } catch (e: IllegalArgumentException) {
            throw PdfExtractionError.Unreadable("not a valid PDF")
        }

        if (text.isBlank()) throw PdfExtractionError.Empty()
        text.trim()
    }

    companion object {
        const val MAX_MB = 20
        const val MAX_BYTES = MAX_MB * 1024L * 1024L
    }
}
