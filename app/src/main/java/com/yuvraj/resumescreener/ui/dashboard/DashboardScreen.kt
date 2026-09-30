package com.yuvraj.resumescreener.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuvraj.resumescreener.data.local.ScreeningEntity
import com.yuvraj.resumescreener.ui.components.DistributionBar
import com.yuvraj.resumescreener.ui.components.EmptyState
import com.yuvraj.resumescreener.ui.components.ScoreBadge
import com.yuvraj.resumescreener.ui.components.SectionLabel
import com.yuvraj.resumescreener.ui.components.StatTile
import com.yuvraj.resumescreener.ui.theme.AppTheme
import com.yuvraj.resumescreener.ui.theme.TabularFamily
import com.yuvraj.resumescreener.ui.theme.scoreColor
import java.util.concurrent.TimeUnit

@Composable
fun DashboardScreen(
    onStartScreening: () -> Unit,
    onOpenCandidate: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isEmpty) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.Description,
                title = "No candidates screened yet",
                body = "Upload a resume and a job description, and the fit score, " +
                    "justification and per-criterion breakdown will appear here.",
                action = { Button(onClick = onStartScreening) { Text("Run your first screening") } },
            )
        }
        return
    }

    LazyColumn(
        // Tagged so instrumented tests can scroll an item into view: a
        // LazyColumn does not compose off-screen children, so performScrollTo
        // on the child alone fails with "node not found".
        modifier = Modifier.fillMaxSize().testTag("dashboard:list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column {
                SectionLabel("Screening overview")
                Spacer(Modifier.height(6.dp))
                Text("Your pipeline at a glance", style = MaterialTheme.typography.headlineMedium)
            }
        }

        item {
            val s = state.stats
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "Candidates screened",
                        value = s.total.toString(),
                        caption = "all time",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Average score",
                        value = s.averageScore?.let { String.format("%.1f", it) } ?: "—",
                        caption = if (s.averageScore == null) "no scores yet" else "across all roles",
                        modifier = Modifier.weight(1f),
                        valueColor = s.averageScore?.let { scoreColor(it.toFloat(), AppTheme.isDark) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = "Best score",
                        value = s.bestScore?.let { String.format("%.1f", it) } ?: "—",
                        caption = "your top match",
                        modifier = Modifier.weight(1f),
                        valueColor = s.bestScore?.let { scoreColor(it.toFloat(), AppTheme.isDark) },
                    )
                    StatTile(
                        label = "This week",
                        value = s.screenedThisWeek.toString(),
                        caption = "screenings",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            val peak = state.stats.distribution.maxOrNull() ?: 0
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag("dashboard:distribution")
                    .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                    .padding(18.dp)
            ) {
                Text("Score distribution", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Candidates by fit score, 1 to 10",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                if (peak > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        state.stats.distribution.forEachIndexed { index, count ->
                            DistributionBar(
                                band = index + 1,
                                count = count,
                                maxCount = peak,
                                isPeak = count == peak,
                            )
                        }
                    }
                } else {
                    Text("No scored candidates yet.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (state.top.isNotEmpty()) {
            item { SectionLabel("Top matches") }
            items(state.top, key = { "top-${it.id}" }) { row ->
                CandidateRow(row, onOpenCandidate)
            }
        }

        if (state.recent.isNotEmpty()) {
            item { SectionLabel("Recent activity") }
            items(state.recent, key = { "recent-${it.id}" }) { row ->
                CandidateRow(row, onOpenCandidate)
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun CandidateRow(row: ScreeningEntity, onOpen: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .clickable { onOpen(row.id) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreBadge(row.score, diameter = 48)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.candidateName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = row.jobTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = relativeTime(row.createdAt),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun relativeTime(epochMillis: Long): String {
    val delta = System.currentTimeMillis() - epochMillis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}
