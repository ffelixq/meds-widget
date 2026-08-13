package io.github.ffelixq.medswidget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.Medicine
import kotlinx.coroutines.launch

@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
@Composable
fun ShowcaseAppShell(
    mainState: MainUiState,
    historyState: HistoryUiState,
    accessibilityState: AccessibilityPreferencesState,
    onCheck: (DoseRow, CheckSource) -> Unit,
    onUndo: (DoseRow) -> Unit,
    onSkip: (DoseRow, String) -> Unit,
    onStartCountdown: (DoseRow, CheckSource) -> Unit,
    onCancelCountdown: (DoseRow) -> Unit,
    onRestartCountdown: (DoseRow) -> Unit,
    onRemindLater: (DoseRow, Int) -> Unit,
    onExperienceMode: (ExperienceMode) -> Unit,
    onTextSize: (AppTextSize) -> Unit,
    onRefill: (Medicine, Double) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Medicine) -> Unit,
    onOpenDetailedHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWidgetSetup: () -> Unit,
) {
    var selectedTabName by rememberSaveable { mutableStateOf(ShowcaseTab.TODAY.name) }
    var selectedDoseId by rememberSaveable { mutableStateOf<String?>(null) }
    var skipCandidate by remember { mutableStateOf<DoseRow?>(null) }
    var snoozeCandidate by remember { mutableStateOf<DoseRow?>(null) }
    var skipReason by remember { mutableStateOf("") }
    var refillCandidate by remember { mutableStateOf<Medicine?>(null) }
    val selectedTab = ShowcaseTab.valueOf(selectedTabName)
    val selectedDose = mainState.rows.firstOrNull { it.stateId == selectedDoseId }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val patientMode = accessibilityState.experienceMode == ExperienceMode.PATIENT

    LaunchedEffect(patientMode) {
        if (patientMode && selectedTab == ShowcaseTab.MEDICINES) {
            selectedTabName = ShowcaseTab.TODAY.name
        }
    }

    BackHandler(enabled = selectedDose != null) {
        selectedDoseId = null
    }

    val recordTaken: (DoseRow, CheckSource) -> Unit = { row, source ->
        onCheck(row, source)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result =
                snackbarHostState.showSnackbar(
                    message = "Recorded: ${row.medicineName} taken",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) onUndo(row)
        }
    }

    if (selectedDose != null) {
        ShowcaseDoseDetailScreen(
            row = selectedDose,
            medicine = mainState.medicines.firstOrNull { it.id == selectedDose.medicineId },
            patientMode = patientMode,
            onBack = { selectedDoseId = null },
            onCheck = { recordTaken(selectedDose, CheckSource.APP) },
            onUndo = { onUndo(selectedDose) },
            onSkip = {
                skipReason = ""
                skipCandidate = selectedDose
            },
            onRemindLater = { snoozeCandidate = selectedDose },
            onStartCountdown = { onStartCountdown(selectedDose, CheckSource.APP) },
            onCancelCountdown = { onCancelCountdown(selectedDose) },
            onRestartCountdown = { onRestartCountdown(selectedDose) },
        )
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                ShowcaseBottomBar(
                    selected = selectedTab,
                    patientMode = patientMode,
                    onSelected = { selectedTabName = it.name },
                )
            },
        ) { padding ->
            ShowcaseTabContent(
                selectedTab = selectedTab,
                mainState = mainState,
                historyState = historyState,
                accessibilityState = accessibilityState,
                contentPadding = padding,
                onCheck = recordTaken,
                onStartCountdown = onStartCountdown,
                onOpenDose = { selectedDoseId = it.stateId },
                onRemindLater = { snoozeCandidate = it },
                onRefill = { refillCandidate = it },
                onAdd = onAdd,
                onEdit = onEdit,
                onOpenDetailedHistory = onOpenDetailedHistory,
                onOpenSettings = onOpenSettings,
                onOpenWidgetSetup = onOpenWidgetSetup,
                onExperienceMode = onExperienceMode,
                onTextSize = onTextSize,
            )
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
                scope.launch {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = "Recorded as not taken",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Long,
                        )
                    if (result == SnackbarResult.ActionPerformed) onUndo(row)
                }
            },
        )
    }
    snoozeCandidate?.let { row ->
        RemindLaterDialog(
            row = row,
            onDismiss = { snoozeCandidate = null },
            onSelect = { minutes ->
                onRemindLater(row, minutes)
                snoozeCandidate = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = if (minutes == 60) "Reminder set for 1 hour" else "Reminder set for $minutes minutes",
                        duration = SnackbarDuration.Short,
                    )
                }
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

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ShowcaseTabContent(
    selectedTab: ShowcaseTab,
    mainState: MainUiState,
    historyState: HistoryUiState,
    accessibilityState: AccessibilityPreferencesState,
    contentPadding: PaddingValues,
    onCheck: (DoseRow, CheckSource) -> Unit,
    onStartCountdown: (DoseRow, CheckSource) -> Unit,
    onOpenDose: (DoseRow) -> Unit,
    onRemindLater: (DoseRow) -> Unit,
    onRefill: (Medicine) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Medicine) -> Unit,
    onOpenDetailedHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWidgetSetup: () -> Unit,
    onExperienceMode: (ExperienceMode) -> Unit,
    onTextSize: (AppTextSize) -> Unit,
) {
    val patientMode = accessibilityState.experienceMode == ExperienceMode.PATIENT
    when (selectedTab) {
        ShowcaseTab.TODAY -> {
            if (patientMode) {
                PatientTodayScreen(
                    state = mainState,
                    contentPadding = contentPadding,
                    onTake = { onCheck(it, CheckSource.APP) },
                    onOpenDose = onOpenDose,
                    onRemindLater = onRemindLater,
                    onStartCountdown = { onStartCountdown(it, CheckSource.APP) },
                )
            } else {
                ShowcaseTodayScreen(
                    state = mainState,
                    contentPadding = contentPadding,
                    onCheck = onCheck,
                    onStartCountdown = onStartCountdown,
                    onOpenDose = onOpenDose,
                    onAdd = onAdd,
                    onRemindLater = onRemindLater,
                )
            }
        }

        ShowcaseTab.HISTORY -> {
            if (patientMode) {
                PatientHistoryScreen(
                    state = historyState,
                    contentPadding = contentPadding,
                    onOpenDetailedHistory = onOpenDetailedHistory,
                )
            } else {
                ShowcaseHistoryScreen(
                    state = historyState,
                    contentPadding = contentPadding,
                    onOpenDetailedHistory = onOpenDetailedHistory,
                )
            }
        }

        ShowcaseTab.MEDICINES -> {
            ShowcaseMedicinesScreen(
                state = mainState,
                contentPadding = contentPadding,
                onAdd = onAdd,
                onEdit = onEdit,
                onRefill = onRefill,
            )
        }

        ShowcaseTab.MORE -> {
            if (patientMode) {
                PatientMoreScreen(
                    accessibilityState = accessibilityState,
                    contentPadding = contentPadding,
                    onExperienceMode = onExperienceMode,
                    onTextSize = onTextSize,
                    onOpenSettings = onOpenSettings,
                )
            } else {
                ShowcaseMoreScreen(
                    state = mainState,
                    accessibilityState = accessibilityState,
                    contentPadding = contentPadding,
                    onExperienceMode = onExperienceMode,
                    onTextSize = onTextSize,
                    onOpenSettings = onOpenSettings,
                    onOpenWidgetSetup = onOpenWidgetSetup,
                    onCheckPreview = { onCheck(it, CheckSource.APP_PREVIEW) },
                    onStartCountdownPreview = { onStartCountdown(it, CheckSource.APP_PREVIEW) },
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun ShowcaseBottomBar(
    selected: ShowcaseTab,
    patientMode: Boolean = false,
    onSelected: (ShowcaseTab) -> Unit,
) {
    val tabs =
        if (patientMode) {
            listOf(ShowcaseTab.TODAY, ShowcaseTab.HISTORY, ShowcaseTab.MORE)
        } else {
            ShowcaseTab.entries
        }
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                icon = {
                    Icon(
                        imageVector =
                            when (tab) {
                                ShowcaseTab.TODAY -> Icons.Outlined.Home
                                ShowcaseTab.HISTORY -> Icons.AutoMirrored.Outlined.List
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
