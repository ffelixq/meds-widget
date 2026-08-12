package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.HistoryEntry
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleLargeTitle
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader

@Suppress("FunctionNaming")
@Composable
internal fun PatientHistoryScreen(
    state: HistoryUiState,
    contentPadding: PaddingValues,
    onOpenDetailedHistory: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            AppleLargeTitle(
                title = "History",
                subtitle = "A simple record of medicines you marked as taken.",
            )
        }
        if (state.isLoading) {
            item { AppleCard { Text("Loading history…", style = MaterialTheme.typography.titleLarge) } }
        } else {
            item { PatientSevenDaySummary(state) }
            item {
                AppleSectionHeader(
                    title = "Recent activity",
                    supportingText = "Taken, not taken, and corrected entries.",
                )
            }
            if (state.entries.isEmpty()) {
                item {
                    AppleCard {
                        Text("No history yet", style = MaterialTheme.typography.titleLarge)
                        Text("Your recorded medicines will appear here.")
                    }
                }
            } else {
                item {
                    AppleCard {
                        state.entries.take(12).forEach { PatientHistoryRow(it) }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenDetailedHistory,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                ) {
                    Text("See full history")
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PatientSevenDaySummary(state: HistoryUiState) {
    val scheduled = state.sevenDay.scheduled
    val taken = state.sevenDay.taken
    AppleCard(
        modifier =
            Modifier.semantics {
                contentDescription = "$taken of $scheduled scheduled medicines recorded as taken in the last 7 days"
            },
    ) {
        Text("Last 7 days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "$taken of $scheduled medicines recorded as taken",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (state.sevenDay.skipped > 0 || state.sevenDay.missed > 0) {
            Text(
                "${state.sevenDay.skipped} marked not taken · ${state.sevenDay.missed} not recorded",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PatientHistoryRow(entry: HistoryEntry) {
    val status =
        when {
            entry.isUndone -> "Corrected"
            entry.isSkipped -> "Not taken"
            else -> "Taken"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .semantics {
                    contentDescription =
                        "${entry.medicineName}, ${entry.label}, $status, ${entry.logicalDay}"
                }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (status) {
                "Taken" -> "✓"
                "Not taken" -> "–"
                else -> "↶"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color =
                when (status) {
                    "Taken" -> MaterialTheme.colorScheme.secondary
                    "Not taken" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.medicineName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                entry.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
