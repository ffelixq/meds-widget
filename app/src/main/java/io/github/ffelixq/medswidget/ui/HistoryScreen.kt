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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader
import io.github.ffelixq.medswidget.ui.design.AppleStatusPill
import io.github.ffelixq.medswidget.util.TimeFormatting
import java.time.LocalDate
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
    var selectedDay by rememberSaveable(state.logicalDay) { mutableStateOf(state.logicalDay) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val selectedEntries =
        remember(state.entries, selectedDay) {
            state.entries.filter { it.logicalDay == selectedDay }
        }

    HistoryList(
        state = state,
        selectedDays = selectedDays,
        selectedDay = selectedDay,
        selectedEntries = selectedEntries,
        onDaysChange = { selectedDays = it },
        onPrevious = { selectedDay = selectedDay.minusDays(1) },
        onNext = {
            if (selectedDay < state.logicalDay) selectedDay = selectedDay.plusDays(1)
        },
        onToday = { selectedDay = state.logicalDay },
        onChooseDate = { showCalendar = true },
        modifier = modifier,
    )

    if (showCalendar) {
        HistoryDatePickerDialog(
            selectedDay = selectedDay,
            logicalDay = state.logicalDay,
            onDateSelected = { selectedDay = it },
            onDismiss = { showCalendar = false },
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun HistoryList(
    state: HistoryUiState,
    selectedDays: Int,
    selectedDay: LocalDate,
    selectedEntries: List<HistoryEntry>,
    onDaysChange: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onChooseDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                subtitle = "Choose a day instead of scrolling through one long audit trail.",
            )
        }
        if (state.isLoading) {
            item { HistoryLoading() }
        } else {
            item {
                HistoryDateNavigator(
                    selectedDay = selectedDay,
                    logicalDay = state.logicalDay,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onToday = onToday,
                    onChooseDate = onChooseDate,
                )
            }
            state.errorMessage?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            historyActivityItems(state.entries, selectedEntries, selectedDay)
            item {
                AppleSectionHeader(
                    title = "Trends",
                    supportingText = "A longer-range view of your current saved schedule.",
                )
            }
            item {
                AdherenceCard(summary, selectedDays, onDaysChange)
            }
        }
    }
}

private fun LazyListScope.historyActivityItems(
    allEntries: List<HistoryEntry>,
    selectedEntries: List<HistoryEntry>,
    selectedDay: LocalDate,
) {
    item {
        AppleSectionHeader(
            title = "Activity",
            supportingText = selectedDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
        )
    }
    if (selectedEntries.isEmpty()) {
        item { HistoryEmptyDayCard(hasAnyHistory = allEntries.isNotEmpty()) }
    } else {
        item { DaySummary(selectedEntries) }
        selectedEntries.forEach { entry ->
            item(key = entry.eventId) { HistoryEntryCard(entry) }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun HistoryEmptyDayCard(hasAnyHistory: Boolean) {
    AppleCard {
        Text(
            if (hasAnyHistory) {
                "No taken or skipped doses were recorded for this day."
            } else {
                "No taken or skipped doses have been recorded yet."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (hasAnyHistory) {
            Text(
                "Use Previous, Next, or Choose date to review another day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
