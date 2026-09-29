package com.yuvraj.resumescreener.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuvraj.resumescreener.domain.usecase.FileProgress
import com.yuvraj.resumescreener.domain.usecase.FileStage
import com.yuvraj.resumescreener.ui.components.ScoreBadge
import com.yuvraj.resumescreener.ui.components.SectionLabel
import com.yuvraj.resumescreener.ui.theme.TabularFamily
import com.yuvraj.resumescreener.ui.theme.scoreColor

@Composable
fun ScreenScreen(
    onOpenSettings: () -> Unit,
    onOpenCandidate: (Long) -> Unit,
    viewModel: ScreenViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            // Without this the URI becomes unreadable after a process restart,
            // so a saved batch could not be resumed.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        viewModel.addFiles(uris.map { uri -> SelectedFile(uri, displayName(uri)) })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column {
                SectionLabel("Screening batch")
                Spacer(Modifier.height(6.dp))
                Text("New screening", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paste the job description and attach the resumes to score.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.missingKey) {
            item {
                NoticeCard(
                    text = "Add your Gemini API key in Settings before screening.",
                    isError = true,
                    actionLabel = "Open Settings",
                    onAction = onOpenSettings,
                )
            }
        }

        item {
            Column {
                SectionLabel("Job description")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.jobDescription,
                    onValueChange = viewModel::onJobDescriptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    placeholder = { Text("Paste the full job description here") },
                    shape = MaterialTheme.shapes.medium,
                )
            }
        }

        item {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Candidate resumes")
                    Text(
                        text = if (state.files.isEmpty()) "PDF only" else "${state.files.size} selected",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedAttachButton(onClick = { picker.launch(arrayOf("application/pdf")) })
            }
        }

        if (state.files.isNotEmpty()) {
            items(state.files, key = { it.uri.toString() }) { file ->
                val progress = state.progress[file.uri.toString()]
                FileRow(
                    name = file.name,
                    stage = progress?.stage ?: FileStage.PENDING,
                    error = progress?.error,
                    onRemove = { viewModel.removeFile(file.uri) },
                    onOpenResult = progress?.resultId?.let { id -> { onOpenCandidate(id) } },
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = viewModel::run,
                    enabled = state.canRun,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        when {
                            state.running -> "Screening ${state.files.size} candidates…"
                            state.files.isEmpty() -> "Attach resumes to continue"
                            state.jobDescription.isBlank() -> "Paste a job description to continue"
                            else -> "Run screening (${state.files.size} candidate${if (state.files.size == 1) "" else "s"})"
                        }
                    )
                }

                if (state.finished > 0 || state.failed > 0) {
                    Text(
                        text = "${state.finished} screened" +
                            if (state.failed > 0) " · ${state.failed} failed" else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutlinedAttachButton(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
    ) {
        androidx.compose.material3.TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+  Pick PDF files")
        }
    }
}

@Composable
private fun FileRow(
    name: String,
    stage: FileStage,
    error: String?,
    onRemove: () -> Unit,
    onOpenResult: (() -> Unit)?,
) {
    val running = stage in setOf(
        FileStage.EXTRACTING, FileStage.PARSING_RESUME,
        FileStage.PARSING_JOB, FileStage.SCORING, FileStage.SAVING,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            stage == FileStage.DONE && onOpenResult != null -> {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            stage == FileStage.FAILED -> {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
            }
            else -> {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.size(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = error ?: stage.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (stage == FileStage.DONE && onOpenResult != null) {
            androidx.compose.material3.TextButton(onClick = onOpenResult) { Text("View") }
        } else if (stage == FileStage.PENDING) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove $name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(
    text: String,
    isError: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .border(1.dp, accent.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private val FileStage.label: String
    get() = when (this) {
        FileStage.PENDING -> "Ready"
        FileStage.EXTRACTING -> "Reading PDF…"
        FileStage.PARSING_RESUME -> "Parsing resume…"
        FileStage.PARSING_JOB -> "Parsing job description…"
        FileStage.SCORING -> "Scoring fit…"
        FileStage.SAVING -> "Saving…"
        FileStage.DONE -> "Screened"
        FileStage.FAILED -> "Failed"
    }

/** Best-effort display name for a picked document. */
private fun displayName(uri: android.net.Uri): String =
    uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Resume.pdf"
