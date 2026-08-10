package io.github.ffelixq.medswidget.util

import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseEvent
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.Medicine
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MedicationCsvExporterTest {
    @Test
    fun `export includes medicine configuration and dose history`() {
        val medicine =
            Medicine(
                id = "med-1",
                ownerUid = "user",
                name = "Vitamin D, 1000 IU",
                notes = "Take with food\nAvoid empty stomach",
                morningEnabled = true,
                morningLabel = "After breakfast",
                afternoonEnabled = false,
                nightEnabled = false,
                updatedAt = Instant.parse("2026-08-09T10:00:00Z"),
            )
        val event =
            DoseEvent(
                eventId = "event-1",
                ownerUid = "user",
                action = DoseAction.CHECK,
                logicalDay = LocalDate.of(2026, 8, 9),
                medicineId = medicine.id,
                medicineNameSnapshot = medicine.name,
                slot = DoseSlot.MORNING,
                labelSnapshot = "After breakfast",
                occurredAt = Instant.parse("2026-08-09T00:10:00Z"),
                timezoneId = "Asia/Singapore",
                source = CheckSource.APP,
                relatedStateId = "state-1",
            )

        val csv = MedicationCsvExporter.export(listOf(medicine), listOf(event))

        assertTrue(csv.startsWith("recordType,medicineId,name,slot,action"))
        assertTrue(csv.contains("\"Vitamin D, 1000 IU\""))
        assertTrue(csv.contains("\"Take with food\nAvoid empty stomach\""))
        assertTrue(csv.contains("dose_event,med-1,\"Vitamin D, 1000 IU\",morning,check"))
    }

    @Test
    fun `export doubles quotes inside CSV fields`() {
        val medicine =
            Medicine(
                id = "med-2",
                ownerUid = "user",
                name = "Tablet \"A\"",
                morningEnabled = false,
                afternoonEnabled = true,
                nightEnabled = false,
            )

        val csv = MedicationCsvExporter.export(listOf(medicine), emptyList())

        assertTrue(csv.contains("\"Tablet \"\"A\"\"\""))
    }

    @Test
    fun `export neutralizes spreadsheet formula prefixes`() {
        val medicine =
            Medicine(
                id = "med-3",
                ownerUid = "user",
                name = "=1+1",
                notes = "  @SUM(A1:A2)",
                morningEnabled = false,
                afternoonEnabled = true,
                nightEnabled = false,
            )

        val csv = MedicationCsvExporter.export(listOf(medicine), emptyList())

        assertTrue(csv.contains("medicine,med-3,'=1+1,"))
        assertTrue(csv.contains("'  @SUM(A1:A2)"))
    }
}
