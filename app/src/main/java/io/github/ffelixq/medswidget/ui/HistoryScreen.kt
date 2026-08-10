package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.AdherenceSummary
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.HistoryEntry
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleLargeTitle
import io.github.ffelixq.medswidget.ui.design.AppleStatusPill
import io.github.ffelixq.medswidget.util.TimeFormatting
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming")
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                    ),
            )
        },
    ) { padding ->
        HistoryBody(state, Modifier.padding(padding))
    }
}

@Suppress("FunctionNaming")
@Composable
private fun HistoryBody(
    state: HistoryUiState,
    modifier: Modifier = Modifier,
) {
    var selectedDays by remember { mutableIntStateOf(30) }
    val summary =
        when (selectedDays) {
            7 -> state.sevenDay
            90 -> state.ninetyDay
            else -> state.thirtyDay
        }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppleLargeTitle(
                title = "History & adherence",
                subtitle = "Review what happened without losing the original audit trail.",
            )
        }
        if (state.isLoading) {
            item { HistoryLoading() }
        } else {
            item {
                AdherenceCard(
                    summary = summary,
                    selectedDays = selectedDays,
                    onDaysChange = { selectedDays = it },
                )
            }
        }
        state.errorMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        if (!state.isLoading && state.entries.isEmpty()) {
            item {
                AppleCard {
                    Text("No taken or skipped doses have been recorded yet.")
                }
            }
        }
        state.entries.groupBy { it.logicalDay }.forEach { (day, entries) ->
            item(key = "day_$day") {
                Text(
                    day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
            entries.forEach { entry ->
                item(key = entry.eventId) { HistoryEntryCard(entry) }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun HistoryLoading() {
    AppleCard(modifier = Modifier.testTag("history_loading")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Text("Loading history…", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun HistoryEntryCard(entry: HistoryEntry) {
    val context = LocalContext.current
    AppleCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.medicineName, style = MaterialTheme.typography.titleMedium)
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppleStatusPill(
                text = if (entry.action == DoseAction.SKIP) "Skipped" else "Taken",
                containerColor =
                    if (entry.action == DoseAction.SKIP) {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                    },
                contentColor =
                    if (entry.action == DoseAction.SKIP) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
            )
        }
        val verb = if (entry.action == DoseAction.SKIP) "Skipped" else "Taken"
        val eventTime = TimeFormatting.compact(context, entry.checkedAt, entry.checkedTimezone)
        Text(
            "$verb $eventTime from ${entry.checkedSource.displayName()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entry.skipReason?.let { reason ->
            Text("Reason: $reason", style = MaterialTheme.typography.bodySmall)
        }
        entry.undoneAt?.let { undoneAt ->
            val undoTime = TimeFormatting.compact(context, undoneAt, entry.undoTimezone)
            Text(
                "Undone $undoTime from ${entry.undoSource?.displayName().orEmpty()}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AdherenceCard(
    summary: AdherenceSummary,
    selectedDays: Int,
    onDaysChange: (Int) -> Unit,
) {
    AppleCard {
        Text("Adherence", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(7, 30, 90).forEach { days ->
                FilterChip(
                    selected = selectedDays == days,
                    onClick = { onDaysChange(days) },
                    label = { Text("${days}d") },
                )
            }
        }
        Text(
            "${"%.1f".format(summary.adherencePercent)}%",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${"%.1f".format(summary.adherencePercent)}% taken",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppleStatusPill(
                text = "${summary.taken} taken",
                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.secondary,
            )
            AppleStatusPill(
                text = "${summary.skipped} skipped",
                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.tertiary,
            )
        }
        Text(
            "${summary.taken} taken · ${summary.skipped} skipped · " +
                "${summary.missed} missed · ${summary.scheduled} due",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Adherence uses the current saved schedule and course dates. " +
                "It is a personal tracking summary, not a clinical measure.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun CheckSource.displayName(): String =
    when (this) {
        CheckSource.APP -> "app"
        CheckSource.APP_PREVIEW -> "app preview"
        CheckSource.WIDGET_2X2 -> "2×2 widget"
        CheckSource.WIDGET_4X2 -> "4×2 widget"
        CheckSource.WIDGET_4X4 -> "4×4 widget"
        CheckSource.NOTIFICATION -> "notification"
    }
