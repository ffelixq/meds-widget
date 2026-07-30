package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
        topBar = {
            TopAppBar(
                title = { Text("History") },
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
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("history_loading"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                        )
                        Text("Loading history…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else if (state.entries.isEmpty()) {
                item { Text("No checks have been recorded yet.") }
            }
            state.errorMessage?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
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
                            Column(Modifier.padding(14.dp)) {
                                Text(entry.medicineName, style = MaterialTheme.typography.titleSmall)
                                Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Checked ${TimeFormatting.compact(
                                        androidx.compose.ui.platform.LocalContext.current,
                                        entry.checkedAt,
                                        entry.checkedTimezone,
                                    )} " +
                                        "from ${entry.checkedSource.displayName()}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "Timezone: ${entry.checkedTimezone}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                entry.undoneAt?.let {
                                    Text(
                                        "Undone ${
                                            TimeFormatting.compact(
                                                androidx.compose.ui.platform.LocalContext.current,
                                                it,
                                                entry.undoTimezone,
                                            )
                                        } from ${entry.undoSource?.displayName().orEmpty()}",
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

private fun io.github.ffelixq.medswidget.domain.CheckSource.displayName(): String =
    when (this) {
        io.github.ffelixq.medswidget.domain.CheckSource.APP -> "app"
        io.github.ffelixq.medswidget.domain.CheckSource.APP_PREVIEW -> "app preview"
        io.github.ffelixq.medswidget.domain.CheckSource.WIDGET_2X2 -> "2×2 widget"
        io.github.ffelixq.medswidget.domain.CheckSource.WIDGET_4X2 -> "4×2 widget"
    }
