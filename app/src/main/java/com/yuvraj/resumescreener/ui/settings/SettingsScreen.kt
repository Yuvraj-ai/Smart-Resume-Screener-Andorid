package com.yuvraj.resumescreener.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuvraj.resumescreener.BuildConfig
import com.yuvraj.resumescreener.data.settings.KeyStatus
import com.yuvraj.resumescreener.data.settings.SettingsRepository
import com.yuvraj.resumescreener.domain.ai.LlmProvider
import com.yuvraj.resumescreener.ui.components.SectionLabel
import com.yuvraj.resumescreener.ui.theme.AppTheme
import com.yuvraj.resumescreener.ui.theme.PillShape
import com.yuvraj.resumescreener.ui.theme.TabularFamily
import com.yuvraj.resumescreener.ui.theme.scoreColor

/**
 * Settings, including provider selection.
 *
 * Both providers are configured side by side and neither one's stored key is
 * touched when you switch. The active provider decides which credential the
 * pipeline uses; the other's settings stay exactly as you left them.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            SectionLabel("Configuration")
            Spacer(Modifier.height(6.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Configuration and credentials are stored on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard {
            SectionLabel("Model provider")
            Spacer(Modifier.height(12.dp))
            Text(
                "Which service does the screening pipeline call.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LlmProvider.entries.forEach { provider ->
                    ProviderChip(
                        label = provider.label,
                        selected = state.provider == provider,
                        onClick = { viewModel.setProvider(provider) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = when (state.provider) {
                    LlmProvider.GEMINI ->
                        "Calls Google's Gemini API directly. Endpoint and models are fixed by Google."
                    LlmProvider.OPENAI_COMPATIBLE ->
                        "Calls any OpenAI-compatible endpoint, such as a self-hosted or hosted Gemma. " +
                            "Your resume text goes only to the endpoint you enter below."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // --- the active provider's credentials ------------------------------
        SettingsCard {
            SectionLabel(
                if (state.provider == LlmProvider.GEMINI) "Gemini API" else "Endpoint"
            )
            Spacer(Modifier.height(12.dp))

            if (state.provider == LlmProvider.OPENAI_COMPATIBLE) {
                OutlinedTextField(
                    value = state.openAiBaseUrl,
                    onValueChange = viewModel::setOpenAiBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("https://host/v1") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Include the full path, for example https://my-host:8000/v1. " +
                        "HTTPS only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }

            KeyRow(
                status = state.activeKeyStatus,
                label = "API key",
                onSave = viewModel::saveGeminiKey,
                onClear = if (state.provider == LlmProvider.GEMINI) viewModel::clearGeminiKey
                else viewModel::clearOpenAiKey,
                saveEnabled = { it.isNotBlank() },
            )
        }

        SettingsCard {
            SectionLabel("Models")
            Spacer(Modifier.height(12.dp))

            if (state.provider == LlmProvider.OPENAI_COMPATIBLE) {
                OutlinedButton(
                    onClick = viewModel::loadOpenAiModels,
                    enabled = !state.loadingModels,
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (state.loadingModels) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("Loading…")
                    } else {
                        Text("Load models from endpoint")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Fetches the endpoint's model list so you pick a real id instead of " +
                        "guessing one. Also confirms the key works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
            }

            ModelRow(
                label = "Parsing model",
                help = "Reads the resume and the job description text.",
                options = if (state.provider == LlmProvider.OPENAI_COMPATIBLE) {
                    state.openAiModelOptions.ifEmpty { listOf(state.parseModel) }
                } else {
                    SettingsRepository.PARSE_MODEL_OPTIONS
                },
                selected = state.parseModel,
                onSelect = viewModel::setParseModel,
            )
            Spacer(Modifier.height(16.dp))
            ModelRow(
                label = "Scoring model",
                help = "Produces the fit score, the breakdown and the justification.",
                options = if (state.provider == LlmProvider.OPENAI_COMPATIBLE) {
                    state.openAiModelOptions.ifEmpty { listOf(state.scoreModel) }
                } else {
                    SettingsRepository.SCORE_MODEL_OPTIONS
                },
                selected = state.scoreModel,
                onSelect = viewModel::setScoreModel,
            )
        }

        if (state.provider == LlmProvider.GEMINI) {
            SettingsCard {
                SectionLabel("Connection")
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::testConnection,
                    enabled = state.keyStatus != KeyStatus.MISSING && state.test != TestState.RUNNING,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(if (state.test == TestState.RUNNING) "Testing…" else "Test connection")
                }
            }
        }

        TestResultCard(state, viewModel)

        SettingsCard {
            SectionLabel("Storage")
            Spacer(Modifier.height(10.dp))
            Text("Local database (Room)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "All screening results are stored in an on-device Room database. " +
                    "Nothing is uploaded or synced anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "0 cloud sync · 100% on-device",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFamily),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Column {
            Text("Resume Screener", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun KeyRow(
    status: KeyStatus,
    label: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    saveEnabled: (String) -> Boolean,
) {
    var draft by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusPill(status)
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Paste your API key") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { reveal = !reveal }) {
                Icon(
                    imageVector = if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (reveal) "Hide key" else "Show key",
                )
            }
        },
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Stored encrypted on this device. Sent only to the provider you selected, never anywhere else.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onSave(draft); draft = "" },
            enabled = saveEnabled(draft),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Save") }
        if (status != KeyStatus.MISSING) {
            TextButton(onClick = onClear) { Text("Remove") }
        }
    }
}

@Composable
private fun TestResultCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    val detail = state.testDetail
    if (detail == null && state.test == TestState.IDLE) return
    val accent = when (state.test) {
        TestState.PASSED -> scoreColor(9f, AppTheme.isDark)
        TestState.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    SettingsCard {
        Text(
            text = when (state.test) {
                TestState.PASSED -> "Connection works"
                TestState.FAILED -> "Problem"
                TestState.RUNNING -> "Checking…"
                TestState.IDLE -> ""
            },
            style = MaterialTheme.typography.titleMedium,
            color = accent,
        )
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFamily),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::dismissDetail) { Text("Dismiss") }
        }
    }
}

@Composable
private fun ProviderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                PillShape,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                PillShape,
            )
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(18.dp)
    ) { content() }
}

@Composable
private fun StatusPill(status: KeyStatus) {
    val (label, color) = when (status) {
        KeyStatus.MISSING -> "Not configured" to MaterialTheme.colorScheme.onSurfaceVariant
        KeyStatus.UNVERIFIED -> "Saved" to scoreColor(6f, AppTheme.isDark)
        KeyStatus.VERIFIED -> "Verified" to scoreColor(9f, AppTheme.isDark)
    }
    Row(
        Modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun ModelRow(
    label: String,
    help: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, shape = MaterialTheme.shapes.small) {
                Text(
                    selected,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFamily),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(option, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFamily))
                        },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
    }
}
