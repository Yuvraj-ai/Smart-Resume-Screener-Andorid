package com.yuvraj.resumescreener.domain.usecase

import android.net.Uri
import com.yuvraj.resumescreener.data.remote.GeminiClient
import com.yuvraj.resumescreener.data.remote.GeminiException
import com.yuvraj.resumescreener.data.repository.ScreeningRepository
import com.yuvraj.resumescreener.data.settings.SettingsRepository
import com.yuvraj.resumescreener.domain.model.ScreeningOutcome
import com.yuvraj.resumescreener.domain.pdf.PdfExtractionError
import com.yuvraj.resumescreener.domain.pdf.ResumeTextExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Where a single file is in the pipeline, so the UI can show live progress. */
enum class FileStage { PENDING, EXTRACTING, PARSING_RESUME, PARSING_JOB, SCORING, SAVING, DONE, FAILED }

data class FileProgress(
    val uri: Uri,
    val fileName: String,
    val stage: FileStage = FileStage.PENDING,
    val error: String? = null,
    val outcome: ScreeningOutcome? = null,
    val resultId: Long? = null,
)

sealed interface BatchEvent {
    data class Progress(val progress: FileProgress) : BatchEvent
    data class Completed(val progress: FileProgress) : BatchEvent
}

/**
 * The three-node pipeline from the Python source, as a sequence.
 *
 * The source used LangGraph with a strictly linear chain
 * `START -> parseResume -> parseJd -> computeScore -> END` and no conditional
 * edges, so a graph engine bought nothing here. This is the same three calls,
 * in the same order, with the same prompts.
 *
 * Failures are per file: one unreadable PDF or rejected prompt must not discard
 * the results that did succeed. That is the behaviour `screen_files` had in the
 * Python source, and it is the reason each file is wrapped independently.
 */
@Singleton
class ScreeningPipeline @Inject constructor(
    private val gemini: GeminiClient,
    private val extractor: ResumeTextExtractor,
    private val repository: ScreeningRepository,
    private val settings: SettingsRepository,
) {
    /**
     * @param onMissingKey invoked when no API key is configured. The batch stops
     *   before doing any work rather than failing every file individually.
     */
    fun run(
        files: List<Pair<Uri, String>>,
        jobDescription: String,
        onMissingKey: suspend () -> Unit = {},
    ): Flow<BatchEvent> = flow {
        val apiKey = settings.apiKeyOrNull()
        if (apiKey == null) {
            onMissingKey()
            return@flow
        }
        if (jobDescription.isBlank()) return@flow

        val parseModel = settings.parseModel.value
        val scoreModel = settings.scoreModel.value

        for ((uri, fileName) in files) {
            val progress = FileProgress(uri, fileName)
            try {
                emit(BatchEvent.Progress(progress.copy(stage = FileStage.EXTRACTING)))
                val resumeText = extractor.extract(uri)

                emit(BatchEvent.Progress(progress.copy(stage = FileStage.PARSING_RESUME)))
                val resume = gemini.extractResume(resumeText, parseModel, apiKey)

                emit(BatchEvent.Progress(progress.copy(stage = FileStage.PARSING_JOB)))
                val job = gemini.extractJobDescription(jobDescription, parseModel, apiKey)

                emit(BatchEvent.Progress(progress.copy(stage = FileStage.SCORING)))
                val match = gemini.score(resume, job, scoreModel, apiKey)

                val outcome = ScreeningOutcome(resume, job, match)
                emit(BatchEvent.Progress(progress.copy(stage = FileStage.SAVING)))
                val id = repository.save(fileName, outcome)

                emit(BatchEvent.Completed(progress.copy(stage = FileStage.DONE, outcome = outcome, resultId = id)))
            } catch (e: GeminiException.InvalidKey) {
                // Not per-file: a bad key will fail every remaining call too.
                emit(BatchEvent.Progress(progress.copy(stage = FileStage.FAILED, error = e.message)))
                throw e
            } catch (e: Exception) {
                val reason = when (e) {
                    is PdfExtractionError -> e.message
                    is GeminiException -> e.message
                    else -> "${e::class.simpleName}: ${e.message}"
                } ?: "Unknown error"
                emit(BatchEvent.Progress(progress.copy(stage = FileStage.FAILED, error = reason)))
            }
        }
    }
}
