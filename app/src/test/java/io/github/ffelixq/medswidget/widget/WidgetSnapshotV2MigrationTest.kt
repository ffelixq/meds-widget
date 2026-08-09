package io.github.ffelixq.medswidget.widget

import io.github.ffelixq.medswidget.domain.DoseSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetSnapshotV2MigrationTest {
    @Test
    fun `codec preserves all four slots privacy display and supply metadata`() {
        val medicine =
            WidgetMedicine(
                id = "med",
                name = "Private real name",
                displayName = "Night med",
                morningEnabled = true,
                morningLabel = "After breakfast",
                morningCountdownMinutes = 30,
                afternoonEnabled = true,
                afternoonLabel = "After lunch",
                afternoonCountdownMinutes = 60,
                eveningEnabled = true,
                eveningLabel = "After dinner",
                eveningCountdownMinutes = 90,
                nightEnabled = true,
                nightLabel = "Before bed",
                nightCountdownMinutes = 120,
                supplyEnabled = true,
                supplyRemainingUnits = 14.5,
                unitsPerDose = 1.5,
            )
        val snapshot =
            WidgetSnapshot(
                ownerUid = "user",
                signedIn = true,
                logicalDay = LocalDate.of(2026, 8, 9),
                medicines = listOf(medicine),
            )

        val decoded = WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(snapshot))
        val restored = decoded.medicines.single()

        assertEquals("Private real name", restored.name)
        assertEquals("Night med", restored.displayName)
        assertEquals(DoseSlot.entries, DoseSlot.entries.filter(restored::isEnabled))
        assertEquals(30, restored.morningCountdownMinutes)
        assertEquals(90, restored.eveningCountdownMinutes)
        assertTrue(restored.supplyEnabled)
        assertEquals(14.5, restored.supplyRemainingUnits ?: 0.0, 0.001)
        assertEquals(1.5, restored.unitsPerDose, 0.001)
    }

    @Test
    fun `legacy V1 snapshot decodes with safe V2 defaults`() {
        val legacy =
            """{
              "ownerUid":"user",
              "signedIn":true,
              "isLoading":false,
              "logicalDay":"2026-08-09",
              "medicines":[{
                "id":"med",
                "name":"Medicine",
                "afternoonEnabled":true,
                "afternoonLabel":"After lunch",
                "nightEnabled":true,
                "nightLabel":"Night"
              }],
              "rows":[],
              "pendingActions":[],
              "pendingCountdownActions":[],
              "fromCache":false,
              "hasPendingWrites":false,
              "repositoryHasPendingWrites":false
            }""".trimIndent()

        val restored = WidgetSnapshotCodec.decode(legacy).medicines.single()

        assertEquals("Medicine", restored.displayName)
        assertFalse(restored.morningEnabled)
        assertTrue(restored.afternoonEnabled)
        assertFalse(restored.eveningEnabled)
        assertTrue(restored.nightEnabled)
        assertFalse(restored.supplyEnabled)
        assertNull(restored.supplyRemainingUnits)
        assertEquals(1.0, restored.unitsPerDose, 0.001)
    }
}
