package com.yuvraj.resumescreener.ui.candidates

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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuvraj.resumescreener.data.local.ScreeningEntity
import com.yuvraj.resumescreener.data.repository.CandidateSort
import com.yuvraj.resumescreener.ui.components.EmptyState
import com.yuvraj.resumescreener.ui.components.ScoreBadge
import com.yuvraj.resumescreener.ui.components.SectionLabel
import com.yuvraj.resumescreener.ui.dashboard.relativeTime
import com.yuvraj.resumescreener.ui.theme.TabularFamily
import com.yuvraj.resumescreener.ui.theme.scoreColor

@Composable
fun CandidatesScreen(
    onOpenCandidate: (Long) -> Unit,
    viewModel: CandidatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        SectionLabel("Candidate database")
        Spacer(Modifier.height(6.dp))
        Text("Screened candidates", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search name or role") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortChip(
                label = "Most recent",
                selected = state.sort == CandidateSort.RECENT,
                onClick = { viewModel.onSortChange(CandidateSort.RECENT) },
            )
            SortChip(
                label = "Highest score",
                selected = state.sort == CandidateSort.SCORE_HIGH,
                onClick = { viewModel.onSortChange(CandidateSort.SCORE_HIGH) },
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = if (state.isFiltered) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                    title = if (state.isFiltered) "No matches" else "Nothing screened yet",
                    body = if (state.isFiltered) {
                        "No candidate matches \"${state.query}\". Try a different name or role."
                    } else {
                        "Candidates you screen will be listed here, newest first."
                    },
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.rows, key = { it.id }) { row ->
                    CandidateCard(row, onOpenCandidate)
                }
            }
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun CandidateCard(row: ScreeningEntity, onOpen: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .clickable { onOpen(row.id) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreBadge(row.score, diameter = 52)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.candidateName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.jobTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Screened ${relativeTime(row.createdAt)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
