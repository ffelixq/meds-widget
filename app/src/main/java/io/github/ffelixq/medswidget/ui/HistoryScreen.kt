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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.AdherenceSummary
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseAction
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
    var selectedDays by remember { mutableIntStateOf(30) }
    val summary =
        when (selectedDays) {
            7 -> state.sevenDay
            90 -> state.ninetyDay
            else -> state.thirtyDay
        }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History & adherence") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("history_loading"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Text("Loading history…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
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
                item { Text("No taken or skipped doses have been recorded yet.") }
            }
            state.entries.groupBy { it.logicalDay }.forEach { (day, entries) ->
                item(key = "day_$day") {
                    Text(
                        day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                entries.forEach { entry ->
                    item(key = entry.eventId) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(entry.medicineName, style = MaterialTheme.typography.titleSmall)
                                Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                                val verb = if (entry.action == DoseAction.SKIP) "Skipped" else "Taken"
                                Text(
                                    "$verb ${TimeFormatting.compact(
                                        androidx.compose.ui.platform.LocalContext.current,
                                        entry.checkedAt,
                                        entry.checkedTimezone,
                                    )} from ${entry.checkedSource.displayName()}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                entry.skipReason?.let {
                                    Text("Reason: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                entry.undoneAt?.let {
                                    Text(
                                        "Undone ${TimeFormatting.compact(
                                            androidx.compose.ui.platform.LocalContext.current,
                                            it,
                                            entry.undoTimezone,
                                        )} from ${entry.undoSource?.displayName().orEmpty()}",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Adherence", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 30, 90).forEach { days ->
                    FilterChip(
                        selected = selectedDays == days,
                        onClick = { onDaysChange(days) },
                        label = { Text("${days}d") },
                    )
                }
            }
            Text("${"%.1f".format(summary.adherencePercent)}% taken", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${summary.taken} taken · ${summary.skipped} skipped · ${summary.missed} missed · ${summary.scheduled} due",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Adherence uses the current saved schedule and course dates; it is a personal tracking summary, not a clinical measure.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
