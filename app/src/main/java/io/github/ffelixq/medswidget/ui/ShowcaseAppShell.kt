package io.github.ffelixq.medswidget.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.Medicine

internal enum class ShowcaseTab {
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
    onRefill: (Medicine, Double) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Medicine) -> Unit,
    onOpenDetailedHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var selectedTabName by rememberSaveable { mutableStateOf(ShowcaseTab.TODAY.name) }
    var selectedDoseId by rememberSaveable { mutableStateOf<String?>(null) }
    var skipCandidate by remember { mutableStateOf<DoseRow?>(null) }
    var skipReason by remember { mutableStateOf("") }
    var refillCandidate by remember { mutableStateOf<Medicine?>(null) }
    val selectedTab = ShowcaseTab.valueOf(selectedTabName)
    val selectedDose = mainState.rows.firstOrNull { it.stateId == selectedDoseId }

    if (selectedDose != null) {
        ShowcaseDoseDetailScreen(
            row = selectedDose,
            medicine = mainState.medicines.firstOrNull { it.id == selectedDose.medicineId },
            onBack = { selectedDoseId = null },
            onCheck = { onCheck(selectedDose, CheckSource.APP) },
            onUndo = { onUndo(selectedDose) },
            onSkip = {
                skipReason = ""
                skipCandidate = selectedDose
            },
            onStartCountdown = { onStartCountdown(selectedDose, CheckSource.APP) },
            onCancelCountdown = { onCancelCountdown(selectedDose) },
            onRestartCountdown = { onRestartCountdown(selectedDose) },
        )
    } else {
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
                        onRefill = { refillCandidate = it },
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

    skipCandidate?.let { row ->
        SkipDoseDialog(
            row = row,
            reason = skipReason,
            onReasonChange = { skipReason = it.take(120) },
            onDismiss = { skipCandidate = null },
            onConfirm = {
                onSkip(row, skipReason)
                skipCandidate = null
                skipReason = ""
                selectedDoseId = null
            },
        )
    }
    refillCandidate?.let { medicine ->
        RefillSupplyDialog(
            medicine = medicine,
            onDismiss = { refillCandidate = null },
            onConfirm = { amount ->
                onRefill(medicine, amount)
                refillCandidate = null
            },
        )
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun ShowcaseBottomBar(
    selected: ShowcaseTab,
    onSelected: (ShowcaseTab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = androidx.compose.ui.unit.dp(0f),
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
                label = { Text(tab.showcaseLabel()) },
            )
        }
    }
}

private fun ShowcaseTab.showcaseLabel(): String =
    when (this) {
        ShowcaseTab.TODAY -> "Today"
        ShowcaseTab.HISTORY -> "History"
        ShowcaseTab.MEDICINES -> "Medicines"
        ShowcaseTab.MORE -> "More"
    }
