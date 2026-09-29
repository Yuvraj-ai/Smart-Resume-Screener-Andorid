package com.yuvraj.resumescreener.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuvraj.resumescreener.domain.model.ScoreBreakdown
import com.yuvraj.resumescreener.ui.components.EmptyState
import com.yuvraj.resumescreener.ui.components.ScoreHeader
import com.yuvraj.resumescreener.ui.components.SectionLabel
import com.yuvraj.resumescreener.ui.components.WeightedScoreBar
import com.yuvraj.resumescreener.ui.dashboard.relativeTime
import com.yuvraj.resumescreener.ui.theme.TabularFamily
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun CandidateDetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entity = state.entity

    if (state.deleted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.Delete,
                title = "Screening deleted",
                body = "This result and its analysis have been removed from this device.",
                action = { TextButton(onClick = onBack) { Text("Back to candidates") } },
            )
        }
        return
    }

    if (state.notFound || entity == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.Delete,
                title = "Not found",
                body = "This screening is no longer stored on the device.",
                action = { TextButton(onClick = onBack) { Text("Go back") } },
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Candidate evaluation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    entity.candidateName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = viewModel::askDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete screening",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Card {
                SectionLabel("Overall fit score")
                Spacer(Modifier.height(10.dp))
                ScoreHeader(entity.score)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Target role: ${entity.jobTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Screened ${relativeTime(entity.createdAt)} from ${entity.sourceFileName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Sub-scores are null for rows stored before decision d7.
            if (entity.skillScore != null) {
                Card {
                    SectionLabel("Rubric breakdown")
                    Spacer(Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        WeightedScoreBar(
                            label = "Skills match",
                            score = entity.skillScore!!,
                            weight = (ScoreBreakdown.SKILL_WEIGHT * 100).toInt(),
                        )
                        WeightedScoreBar(
                            label = "Experience depth",
                            score = entity.experienceScore ?: 0,
                            weight = (ScoreBreakdown.EXPERIENCE_WEIGHT * 100).toInt(),
                        )
                        WeightedScoreBar(
                            label = "Education",
                            score = entity.educationScore ?: 0,
                            weight = (ScoreBreakdown.EDUCATION_WEIGHT * 100).toInt(),
                        )
                    }
                }
            }

            Card {
                SectionLabel("Match justification")
                Spacer(Modifier.height(10.dp))
                Text(
                    entity.summary.ifBlank { "No summary available." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Card {
                SectionLabel("Candidate details")
                Spacer(Modifier.height(12.dp))
                KeyValues(entity.candidateDetailsJson)
            }

            Card {
                SectionLabel("Job details")
                Spacer(Modifier.height(12.dp))
                KeyValues(entity.jobDetailsJson)
            }

            Card {
                SectionLabel("Stored record")
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.rawJson,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
    }

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete this screening?") },
            text = {
                Text(
                    "The record for ${entity?.candidateName} and its analysis will be " +
                        "removed from this device. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(18.dp)
    ) { content() }
}

/** Renders the stored JSON as a readable label/value list. */
@Composable
private fun KeyValues(json: String) {
    val json = remember(json) {
        runCatching {
            Json.parseToJsonElement(json).jsonObject.entries
                .map { (k, v) -> k to (v.jsonPrimitive.contentOrNullSafe()) }
        }.getOrDefault(emptyList())
    }
    if (json.isEmpty()) {
        Text("No details stored.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        json.forEach { (key, value) ->
            if (value.isNotBlank()) {
                Column {
                    Text(
                        key.replace('_', ' ').replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String =
    runCatching { content }.getOrDefault("")
