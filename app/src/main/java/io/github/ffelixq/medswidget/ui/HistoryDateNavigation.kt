package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.HistoryEntry
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleGroupedRow
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader
import io.github.ffelixq.medswidget.ui.design.AppleStatusPill
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun HistoryDateNavigator(
    selectedDay: LocalDate,
    logicalDay: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onChooseDate: () -> Unit,
) {
    val relativeLabel =
        when (selectedDay) {
            logicalDay -> "Today"
            logicalDay.minusDays(1) -> "Yesterday"
            else -> selectedDay.format(DateTimeFormatter.ofPattern("EEEE"))
        }
    AppleCard {
        AppleSectionHeader(
            title = "Browse by day",
            supportingText = "Move nearby one day at a time, or jump farther with the calendar.",
        )
        DayStepRow(selectedDay, logicalDay, relativeLabel, onPrevious, onNext)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onChooseDate,
                modifier = Modifier.weight(1f).testTag("history_choose_date"),
            ) {
                Text("Choose date")
            }
            if (selectedDay != logicalDay) {
                TextButton(
                    onClick = onToday,
                    modifier = Modifier.weight(1f).testTag("history_today"),
                ) {
                    Text("Back to today")
                }
            }
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun DayStepRow(
    selectedDay: LocalDate,
    logicalDay: LocalDate,
    relativeLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    AppleGroupedRow {
        TextButton(
            onClick = onPrevious,
            modifier = Modifier.testTag("history_previous_day"),
        ) {
            Text("Previous")
        }
        Column(
            modifier = Modifier.weight(1f).testTag("history_selected_day"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                relativeLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                selectedDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            enabled = selectedDay < logicalDay,
            onClick = onNext,
            modifier = Modifier.testTag("history_next_day"),
        ) {
            Text("Next")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming")
@Composable
internal fun HistoryDatePickerDialog(
    selectedDay: LocalDate,
    logicalDay: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedMillis = selectedDay.toUtcMillis()
    val latestMillis = logicalDay.toUtcMillis()
    val selectableDates =
        remember(latestMillis, logicalDay.year) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= latestMillis

                override fun isSelectableYear(year: Int): Boolean = year <= logicalDay.year
            }
        }
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = selectedMillis,
            initialDisplayedMonthMillis = selectedMillis,
            selectableDates = selectableDates,
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    state.selectedDateMillis?.let { onDateSelected(it.toUtcLocalDate()) }
                    onDismiss()
                },
            ) {
                Text("Choose")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(state = state, showModeToggle = true)
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun DaySummary(entries: List<HistoryEntry>) {
    val taken = entries.count { it.action != DoseAction.SKIP }
    val skipped = entries.count { it.action == DoseAction.SKIP }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppleStatusPill(
            text = "$taken taken",
            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.secondary,
        )
        AppleStatusPill(
            text = "$skipped skipped",
            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.tertiary,
        )
    }
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate =
    Instant
        .ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
