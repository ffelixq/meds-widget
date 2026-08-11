package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.AdherenceSummary
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownDisplay
import io.github.ffelixq.medswidget.domain.CountdownDisplayStatus
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.HistoryEntry
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleLargeTitle
import io.github.ffelixq.medswidget.ui.design.ApplePressableCard
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader
import io.github.ffelixq.medswidget.ui.design.AppleStatusPill
import io.github.ffelixq.medswidget.util.TimeFormatting
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

private enum class ShowcaseTab {
    TODAY,
    HISTORY,
    MEDICINES,
    MORE,
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun ShowcaseAppShell(
    mainState: MainUiState,
    historyState: HistoryUiState,
    onCheck: (DoseRow, CheckSource) -> Unit,
    onUndo: (DoseRow) -> Unit,
    onSkip: (DoseRow, String) -> Unit,
    onStartCountdown: (DoseRow, CheckSource) -> Unit,
    onCancelCountdown: (DoseRow) -> Unit,
    onRestartCountdown: (DoseRow) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Medicine) -> Unit,
    onOpenDetailedHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var selectedTabName by rememberSaveable { mutableStateOf(ShowcaseTab.TODAY.name) }
    var selectedDoseId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTab = ShowcaseTab.valueOf(selectedTabName)
    val selectedDose = mainState.rows.firstOrNull { it.stateId == selectedDoseId }

    if (selectedDose != null) {
        val medicine = mainState.medicines.firstOrNull { it.id == selectedDose.medicineId }
        ShowcaseDoseDetailScreen(
            row = selectedDose,
            medicine = medicine,
            onBack = { selectedDoseId = null },
            onCheck = { onCheck(selectedDose, CheckSource.APP) },
            onUndo = { onUndo(selectedDose) },
            onSkip = { onSkip(selectedDose, "") },
            onStartCountdown = { onStartCountdown(selectedDose, CheckSource.APP) },
            onCancelCountdown = { onCancelCountdown(selectedDose) },
            onRestartCountdown = { onRestartCountdown(selectedDose) },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ShowcaseBottomBar(
                selected = selectedTab,
                onSelected = { selectedTabName = it.name },
            )
        },
    ) { padding ->
        when (selectedTab) {
            ShowcaseTab.TODAY -> {
                ShowcaseTodayScreen(
                    state = mainState,
                    contentPadding = padding,
                    onCheck = onCheck,
                    onStartCountdown = onStartCountdown,
                    onOpenDose = { selectedDoseId = it.stateId },
                    onAdd = onAdd,
                )
            }

            ShowcaseTab.HISTORY -> {
                ShowcaseHistoryScreen(
                    state = historyState,
                    contentPadding = padding,
                    onOpenDetailedHistory = onOpenDetailedHistory,
                )
            }

            ShowcaseTab.MEDICINES -> {
                ShowcaseMedicinesScreen(
                    state = mainState,
                    contentPadding = padding,
                    onAdd = onAdd,
                    onEdit = onEdit,
                )
            }

            ShowcaseTab.MORE -> {
                ShowcaseMoreScreen(
                    state = mainState,
                    contentPadding = padding,
                    onOpenSettings = onOpenSettings,
                    onCheckPreview = { onCheck(it, CheckSource.APP_PREVIEW) },
                    onStartCountdownPreview = {
                        onStartCountdown(it, CheckSource.APP_PREVIEW)
                    },
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseBottomBar(
    selected: ShowcaseTab,
    onSelected: (ShowcaseTab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
    ) {
        ShowcaseTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                icon = {
                    Icon(
                        imageVector =
                            when (tab) {
                                ShowcaseTab.TODAY -> Icons.Outlined.Home
                                ShowcaseTab.HISTORY -> Icons.Outlined.List
                                ShowcaseTab.MEDICINES -> Icons.Outlined.Add
                                ShowcaseTab.MORE -> Icons.Outlined.MoreVert
                            },
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        when (tab) {
                            ShowcaseTab.TODAY -> "Today"
                            ShowcaseTab.HISTORY -> "History"
                            ShowcaseTab.MEDICINES -> "Medicines"
                            ShowcaseTab.MORE -> "More"
                        },
                    )
                },
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ShowcaseTodayScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onCheck: (DoseRow, CheckSource) -> Unit,
    onStartCountdown: (DoseRow, CheckSource) -> Unit,
    onOpenDose: (DoseRow) -> Unit,
    onAdd: () -> Unit,
) {
    val nextDose = state.rows.firstOrNull { !it.isTaken && !it.isSkipped }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AppleLargeTitle(
                title = "Today",
                subtitle = state.logicalDay.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
            )
        }
        item { ShowcaseProgressCard(state) }
        when {
            state.isLoading -> {
                item { ShowcaseLoadingCard("Loading your medicines…") }
            }

            state.medicines.isEmpty() -> {
                item { ShowcaseEmptyToday(onAdd) }
            }

            nextDose != null -> {
                item {
                    ShowcaseHeroDoseCard(
                        row = nextDose,
                        onTake = { onCheck(nextDose, CheckSource.APP) },
                        onStartCountdown = {
                            onStartCountdown(nextDose, CheckSource.APP)
                        },
                        onOpenDose = { onOpenDose(nextDose) },
                    )
                }
            }

            state.rows.isNotEmpty() -> {
                item { ShowcaseAllDoneCard() }
            }
        }
        if (state.rows.isNotEmpty()) {
            item {
                AppleSectionHeader(
                    title = "Today’s plan",
                    supportingText = "Tap a dose for details, notes, timer controls, or undo.",
                )
            }
            item {
                AppleCard {
                    state.rows.forEach { row ->
                        ShowcaseDoseListRow(
                            row = row,
                            onOpen = { onOpenDose(row) },
                            onTake = { onCheck(row, CheckSource.APP) },
                            onStartCountdown = {
                                onStartCountdown(row, CheckSource.APP)
                            },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseProgressCard(state: MainUiState) {
    AppleCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "${state.progress.completed} of ${state.progress.total}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "doses completed",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            AppleStatusPill(
                text = "${state.progress.pending} pending",
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }
        if (state.progress.total > 0) {
            LinearProgressIndicator(
                progress = {
                    state.progress.completed.toFloat() / state.progress.total.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        if (state.progress.skipped > 0) {
            Text(
                "${state.progress.skipped} skipped today",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when {
            state.hasPendingWrites -> {
                Text(
                    "Syncing changes…",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.isCached -> {
                Text(
                    "Showing cached data",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseHeroDoseCard(
    row: DoseRow,
    onTake: () -> Unit,
    onStartCountdown: () -> Unit,
    onOpenDose: () -> Unit,
) {
    val countdown = showcaseCountdown(row)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        row.medicineName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onOpenDose) {
                    Icon(
                        Icons.Outlined.KeyboardArrowRight,
                        contentDescription = "Dose details",
                    )
                }
            }
            when (countdown.status) {
                CountdownDisplayStatus.RUNNING,
                CountdownDisplayStatus.READY,
                -> {
                    Text(
                        countdown.text.orEmpty(),
                        style = MaterialTheme.typography.headlineLarge,
                        color =
                            if (countdown.status == CountdownDisplayStatus.READY) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                    Text(
                        if (countdown.status == CountdownDisplayStatus.READY) {
                            "Timer complete — take it when you are ready."
                        } else {
                            "remaining"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                CountdownDisplayStatus.NOT_STARTED -> {
                    Text(
                        "Wait ${CountdownLogic.formatDuration(row.countdownMinutes ?: 1)} before taking",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                else -> {
                    Text(
                        "Next dose",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onTake,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Take now")
            }
            if (countdown.status == CountdownDisplayStatus.NOT_STARTED) {
                OutlinedButton(
                    onClick = onStartCountdown,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(countdown.text ?: "Start timer")
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseDoseListRow(
    row: DoseRow,
    onOpen: () -> Unit,
    onTake: () -> Unit,
    onStartCountdown: () -> Unit,
) {
    val countdown = showcaseCountdown(row)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShowcaseStatusDot(row)
        Column(Modifier.weight(1f)) {
            Text(
                row.medicineName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            row.isTaken -> {
                AppleStatusPill(
                    text = "Taken",
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.secondary,
                )
            }

            row.isSkipped -> {
                AppleStatusPill(
                    text = "Skipped",
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.tertiary,
                )
            }

            countdown.status == CountdownDisplayStatus.RUNNING ||
                countdown.status == CountdownDisplayStatus.READY -> {
                AppleStatusPill(
                    text = countdown.text.orEmpty(),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }

            countdown.status == CountdownDisplayStatus.NOT_STARTED -> {
                TextButton(onClick = onStartCountdown) {
                    Text(countdown.text ?: "Start")
                }
            }

            else -> {
                TextButton(onClick = onTake) { Text("Take") }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseStatusDot(row: DoseRow) {
    val background =
        when {
            row.isTaken -> MaterialTheme.colorScheme.secondary
            row.isSkipped -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    val foreground =
        when {
            row.isTaken -> MaterialTheme.colorScheme.onSecondary
            row.isSkipped -> MaterialTheme.colorScheme.onTertiary
            else -> Color.Transparent
        }
    Box(
        modifier = Modifier.size(34.dp).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (row.isTaken) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(20.dp),
            )
        } else if (row.isSkipped) {
            Text("–", color = foreground, fontWeight = FontWeight.Bold)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseHistoryScreen(
    state: HistoryUiState,
    contentPadding: PaddingValues,
    onOpenDetailedHistory: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AppleLargeTitle(
                title = "History",
                subtitle = "Your medication record and adherence at a glance.",
            )
        }
        if (state.isLoading) {
            item { ShowcaseLoadingCard("Loading history…") }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShowcaseAdherenceCard("7 days", state.sevenDay, Modifier.weight(1f))
                    ShowcaseAdherenceCard("30 days", state.thirtyDay, Modifier.weight(1f))
                    ShowcaseAdherenceCard("90 days", state.ninetyDay, Modifier.weight(1f))
                }
            }
            item {
                AppleSectionHeader(
                    title = "Recent activity",
                    supportingText = "Checks, skips, and undo history stay in your audit trail.",
                )
            }
            if (state.entries.isEmpty()) {
                item {
                    AppleCard {
                        Text("No history yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Taken and skipped doses will appear here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    AppleCard {
                        state.entries.take(18).forEach { ShowcaseHistoryRow(it) }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenDetailedHistory,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Open detailed history")
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseAdherenceCard(
    label: String,
    summary: AdherenceSummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "${summary.adherencePercent.toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseHistoryRow(entry: HistoryEntry) {
    val status =
        when {
            entry.isUndone -> "Undone"
            entry.isSkipped -> "Skipped"
            else -> "Taken"
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.medicineName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${entry.label} · ${entry.logicalDay}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppleStatusPill(
            text = status,
            containerColor =
                when {
                    entry.isUndone -> MaterialTheme.colorScheme.surfaceVariant
                    entry.isSkipped -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                },
            contentColor =
                when {
                    entry.isUndone -> MaterialTheme.colorScheme.onSurfaceVariant
                    entry.isSkipped -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.secondary
                },
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseMedicinesScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (Medicine) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                AppleLargeTitle(
                    title = "Medicines",
                    subtitle = "Schedules, reminders, timers, notes, and supply.",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add medicine")
                }
            }
        }
        if (state.isLoading) {
            item { ShowcaseLoadingCard("Loading medicines…") }
        } else if (state.medicines.isEmpty()) {
            item { ShowcaseEmptyToday(onAdd) }
        } else {
            state.medicines.forEach { medicine ->
                item(key = medicine.id) {
                    ApplePressableCard(onClick = { onEdit(medicine) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ShowcaseMedicineGlyph()
                            Column(Modifier.weight(1f)) {
                                Text(
                                    medicine.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val slots = medicine.enabledSlots().joinToString(" · ") { it.defaultLabel }
                                Text(
                                    slots.ifBlank { "No active slots" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (medicine.nickname.isNotBlank()) {
                                    Text(
                                        medicine.nickname,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Icon(
                                Icons.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (medicine.supplyEnabled && medicine.supplyInitialUnits != null) {
                            Text(
                                "${formatSupplyAmount(medicine.supplyInitialUnits)} " +
                                    "${medicine.supplyUnitName} remaining",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("  Add medicine")
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseMedicineGlyph() {
    Box(
        modifier =
            Modifier
                .size(46.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Rx",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ShowcaseMoreScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onCheckPreview: (DoseRow) -> Unit,
    onStartCountdownPreview: (DoseRow) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AppleLargeTitle(
                title = "More",
                subtitle = "Widgets, appearance, privacy, account, and data tools.",
            )
        }
        item {
            ShowcaseMoreLink(
                title = "Settings & appearance",
                subtitle = "Theme, daily reset time, account, reminders, and export",
                onClick = onOpenSettings,
            )
        }
        item {
            ShowcaseMoreLink(
                title = "Privacy & security",
                subtitle = "Review lock-screen, export, widget, network, and account protections",
                onClick = onOpenSettings,
            )
        }
        item {
            AppleSectionHeader(
                title = "Widget Studio",
                supportingText =
                    "The widget preview moved here so Today can stay focused on taking medicine.",
            )
        }
        if (state.rows.isEmpty()) {
            item {
                AppleCard {
                    Text("Add a medicine to preview your widgets.")
                }
            }
        } else {
            item {
                WidgetPreviews(
                    state = state,
                    onCheck = onCheckPreview,
                    onStartCountdown = onStartCountdownPreview,
                )
            }
        }
        item {
            AppleCard {
                AppleSectionHeader(
                    title = "Meds Widget",
                    supportingText = "Designed around clear actions, calm hierarchy, and explicit privacy.",
                )
                Text(
                    "Your home-screen widgets still use the same tested Glance callbacks, " +
                        "cloud sync, countdowns, and audit history as before.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseMoreLink(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ApplePressableCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ShowcaseDoseDetailScreen(
    row: DoseRow,
    medicine: Medicine?,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onUndo: () -> Unit,
    onSkip: () -> Unit,
    onStartCountdown: () -> Unit,
    onCancelCountdown: () -> Unit,
    onRestartCountdown: () -> Unit,
) {
    val countdown = showcaseCountdown(row)
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShowcaseDetailStatusCircle(row)
                Text(
                    row.label,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    row.medicineName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        row.isTaken -> "Taken"
                        row.isSkipped -> "Skipped"
                        countdown.status == CountdownDisplayStatus.READY -> "Ready"
                        countdown.status == CountdownDisplayStatus.RUNNING -> countdown.text.orEmpty()
                        else -> "Pending"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        when {
                            row.isTaken -> MaterialTheme.colorScheme.secondary
                            row.isSkipped -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                )
            }
        }
        item {
            when {
                row.isTaken || row.isSkipped -> {
                    OutlinedButton(
                        onClick = onUndo,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Undo")
                    }
                }

                else -> {
                    Button(
                        onClick = onCheck,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Take now")
                    }
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Skip dose")
                    }
                }
            }
        }
        if (!row.isTaken && !row.isSkipped && row.countdownMinutes != null) {
            item {
                AppleCard {
                    AppleSectionHeader(
                        title = "Wait timer",
                        supportingText =
                            "Configured for ${CountdownLogic.formatDuration(row.countdownMinutes)}.",
                    )
                    when (countdown.status) {
                        CountdownDisplayStatus.NOT_STARTED -> {
                            Button(onClick = onStartCountdown, modifier = Modifier.fillMaxWidth()) {
                                Text(countdown.text ?: "Start timer")
                            }
                        }

                        CountdownDisplayStatus.RUNNING,
                        CountdownDisplayStatus.READY,
                        -> {
                            Text(
                                countdown.text.orEmpty(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onCancelCountdown, modifier = Modifier.weight(1f)) {
                                    Text("Cancel")
                                }
                                OutlinedButton(onClick = onRestartCountdown, modifier = Modifier.weight(1f)) {
                                    Text("Restart")
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
        item {
            AppleCard {
                AppleSectionHeader(title = "Details")
                ShowcaseDetailLine("Slot", row.slot.defaultLabel)
                row.checkedAt?.let {
                    ShowcaseDetailLine(
                        "Taken at",
                        TimeFormatting.compact(context, it, row.checkedTimezone),
                    )
                }
                row.skippedAt?.let {
                    ShowcaseDetailLine("Skipped at", TimeFormatting.compact(context, it))
                }
                row.countdownMinutes?.let {
                    ShowcaseDetailLine("Wait timer", CountdownLogic.formatDuration(it))
                }
                medicine?.notes?.takeIf(String::isNotBlank)?.let {
                    ShowcaseDetailLine("Notes", it)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseDetailStatusCircle(row: DoseRow) {
    val color =
        when {
            row.isTaken -> MaterialTheme.colorScheme.secondary
            row.isSkipped -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }
    Box(
        modifier = Modifier.size(118.dp).background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(82.dp).background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (row.isTaken) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Text(
                    if (row.isSkipped) "–" else "Rx",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseDetailLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseLoadingCard(message: String) {
    AppleCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(message)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseEmptyToday(onAdd: () -> Unit) {
    AppleCard {
        Text("No medicines yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Add your first medicine and its daily slots to build your Today plan.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("  Add medicine")
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseAllDoneCard() {
    AppleCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(46.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White)
            }
            Column {
                Text("Today is complete", style = MaterialTheme.typography.titleLarge)
                Text(
                    "All scheduled doses have been handled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun showcaseCountdown(row: DoseRow): CountdownDisplay {
    val now by produceState(initialValue = Instant.now(), row.countdown?.targetAt) {
        while (row.countdown != null) {
            val remaining = Duration.between(Instant.now(), row.countdown.targetAt)
            if (remaining.isNegative || remaining.isZero) {
                value = Instant.now()
                break
            }
            delay(minOf(remaining.toMillis().coerceAtLeast(1_000L), 60_000L))
            value = Instant.now()
        }
    }
    return CountdownLogic.display(row.countdownMinutes, row.countdown, now)
}
