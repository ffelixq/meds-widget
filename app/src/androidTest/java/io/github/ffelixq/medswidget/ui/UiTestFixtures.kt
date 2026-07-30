package io.github.ffelixq.medswidget.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.github.ffelixq.medswidget.domain.CompletionProgress
import io.github.ffelixq.medswidget.domain.DoseIds
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.Medicine
import java.time.Instant
import java.time.LocalDate

@Composable
internal fun UiTestTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

internal fun testMedicine(
    id: String = "medicine-a",
    name: String = "Medicine A",
    afternoonEnabled: Boolean = true,
    nightEnabled: Boolean = true,
): Medicine =
    Medicine(
        id = id,
        ownerUid = "user-a",
        name = name,
        afternoonEnabled = afternoonEnabled,
        afternoonLabel = "After lunch",
        nightEnabled = nightEnabled,
        nightLabel = "Before bed",
        createdAt = Instant.parse("2026-07-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-07-29T00:00:00Z"),
    )

internal fun testDoseRow(
    medicine: Medicine = testMedicine(),
    slot: DoseSlot = DoseSlot.AFTERNOON,
    isTaken: Boolean = false,
    checkedAt: Instant? = if (isTaken) Instant.parse("2026-07-29T05:15:00Z") else null,
    day: LocalDate = LocalDate.of(2026, 7, 29),
): DoseRow =
    DoseRow(
        medicineId = medicine.id,
        medicineName = medicine.name,
        slot = slot,
        label = medicine.label(slot),
        isTaken = isTaken,
        checkedAt = checkedAt,
        stateId = DoseIds.stateId(day, medicine.id, slot),
    )

internal fun testMainState(
    medicine: Medicine = testMedicine(),
    rows: List<DoseRow> =
        listOf(
            testDoseRow(medicine, DoseSlot.AFTERNOON),
            testDoseRow(medicine, DoseSlot.NIGHT),
        ),
): MainUiState =
    MainUiState(
        isLoading = false,
        logicalDay = LocalDate.of(2026, 7, 29),
        medicines = listOf(medicine),
        rows = rows,
        progress = CompletionProgress(rows.count(DoseRow::isTaken), rows.size),
    )
