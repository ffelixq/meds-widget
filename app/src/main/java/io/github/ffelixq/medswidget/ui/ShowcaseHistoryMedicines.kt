package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.AdherenceSummary
import io.github.ffelixq.medswidget.domain.HistoryEntry
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.ui.design.AppleCard
import io.github.ffelixq.medswidget.ui.design.AppleLargeTitle
import io.github.ffelixq.medswidget.ui.design.ApplePressableCard
import io.github.ffelixq.medswidget.ui.design.AppleSectionHeader
import io.github.ffelixq.medswidget.ui.design.AppleStatusPill

@Suppress("FunctionNaming")
@Composable
internal fun ShowcaseHistoryScreen(
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
            item {
                AppleCard { Text("Loading history…") }
            }
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

@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun ShowcaseMedicinesScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (Medicine) -> Unit,
    onRefill: (Medicine) -> Unit,
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
        when {
            state.isLoading -> {
                item {
                    AppleCard { Text("Loading medicines…") }
                }
            }

            state.medicines.isEmpty() -> {
                item { ShowcaseEmptyMedicines(onAdd) }
            }

            else -> {
                state.medicines.forEach { medicine ->
                    item(key = medicine.id) {
                        ShowcaseMedicineCard(medicine, onEdit, onRefill)
                    }
                }
                item {
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("  Add medicine")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseMedicineCard(
    medicine: Medicine,
    onEdit: (Medicine) -> Unit,
    onRefill: (Medicine) -> Unit,
) {
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
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (medicine.supplyEnabled && medicine.supplyInitialUnits != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${formatSupplyAmount(medicine.supplyInitialUnits)} " +
                        "${medicine.supplyUnitName} remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onRefill(medicine) }) {
                    Text("Refill")
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseMedicineGlyph() {
    Box(
        modifier =
            Modifier
                .size(46.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    CircleShape,
                ),
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

@Suppress("FunctionNaming")
@Composable
private fun ShowcaseEmptyMedicines(onAdd: () -> Unit) {
    AppleCard {
        Text("No medicines yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Add a medicine, choose its slots, and it will appear on Today and your widgets.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text("Add medicine")
        }
    }
}
