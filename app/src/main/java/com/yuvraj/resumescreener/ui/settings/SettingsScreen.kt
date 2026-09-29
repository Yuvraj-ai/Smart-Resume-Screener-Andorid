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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
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
import com.yuvraj.resumescreener.ui.components.SectionLabel
import com.yuvraj.resumescreener.ui.theme.AppTheme
import com.yuvraj.resumescreener.ui.theme.TabularFamily
import com.yuvraj.resumescreener.ui.theme.scoreColor

/**
 * Settings, built natively from the Stitch design.
 *
 * Four corrections from that design are applied here, per design/HANDOFF.md:
 * model names are the real ids from the source pipeline rather than placeholders,
 * the version comes from BuildConfig, storage is described as Room rather than
 * raw SQLite, and the bottom nav is the agreed four sections.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draftKey by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }

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
            SectionLabel("Gemini API")
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "API key",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusPill(state.keyStatus)
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = draftKey,
                onValueChange = { draftKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste your API key") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                visualTransformation = if (revealKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { revealKey = !revealKey }) {
                        Icon(
                            imageVector = if (revealKey) Icons.Outlined.VisibilityOff
                            else Icons.Outlined.Visibility,
                            contentDescription = if (revealKey) "Hide key" else "Show key",
                        )
                    }
                },
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "Stored encrypted on this device. Sent only to Google Gemini, never anywhere else.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        viewModel.saveKey(draftKey)
                        draftKey = ""
                    },
                    enabled = draftKey.isNotBlank(),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Save") }

                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = state.keyStatus != KeyStatus.MISSING && state.test != TestState.RUNNING,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(if (state.test == TestState.RUNNING) "Testing…" else "Test connection")
                }
            }

            val testDetail = state.testDetail
            if (testDetail != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = testDetail,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFamily),
                    color = when (state.test) {
                        TestState.PASSED -> scoreColor(9f, AppTheme.isDark)
                        TestState.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (state.keyStatus != KeyStatus.MISSING) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = viewModel::clearKey) { Text("Remove key") }
            }
        }

        SettingsCard {
            SectionLabel("Models")
            Spacer(Modifier.height(12.dp))
            ModelRow(
                label = "Parsing model",
                help = "Reads the resume and the job description text.",
                options = SettingsRepository.PARSE_MODEL_OPTIONS,
                selected = state.parseModel,
                onSelect = viewModel::setParseModel,
            )
            Spacer(Modifier.height(16.dp))
            ModelRow(
                label = "Scoring model",
                help = "Produces the fit score, the breakdown and the justification.",
                options = SettingsRepository.SCORE_MODEL_OPTIONS,
                selected = state.scoreModel,
                onSelect = viewModel::setScoreModel,
            )
        }

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
            Text(
                "Resume Screener",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
        Box(Modifier.height(6.dp).background(color, CircleShape).then(Modifier.height(6.dp)))
        Spacer(Modifier.padding(horizontal = 3.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
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
        Text(
            help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = selected,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFamily),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                style = MaterialTheme.typography.bodyMedium
                                    .copy(fontFamily = TabularFamily),
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
