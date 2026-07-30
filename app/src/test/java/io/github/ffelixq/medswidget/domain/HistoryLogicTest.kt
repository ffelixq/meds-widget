package io.github.ffelixq.medswidget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HistoryLogicTest {
    private val day = LocalDate.of(2026, 7, 29)
    private val stateId = "2026-07-29_medicine-a_afternoon"

    @Test
    fun `check event preserves medicine and label snapshots`() {
        val check =
            event(
                id = "check-1",
                action = DoseAction.CHECK,
                at = "2026-07-29T05:00:00Z",
                medicineName = "Original medicine name",
                label = "After lunch",
            )

        val entry = HistoryAssembler.assemble(listOf(check)).single()

        assertEquals("Original medicine name", entry.medicineName)
        assertEquals("After lunch", entry.label)
        assertEquals(CheckSource.WIDGET_2X2, entry.checkedSource)
        assertNull(entry.undoneAt)
    }

    @Test
    fun `undo marks the check while retaining its original timestamp and source`() {
        val check =
            event(
                id = "check-1",
                action = DoseAction.CHECK,
                at = "2026-07-29T05:00:00Z",
                source = CheckSource.WIDGET_4X2,
            )
        val undo =
            event(
                id = "undo-1",
                action = DoseAction.UNDO,
                at = "2026-07-29T05:30:00Z",
                previousActionId = "check-1",
                source = CheckSource.APP,
                medicineName = "Renamed later",
                label = "Renamed later",
            )

        val entry = HistoryAssembler.assemble(listOf(undo, check)).single()

        assertEquals(check.occurredAt, entry.checkedAt)
        assertEquals(CheckSource.WIDGET_4X2, entry.checkedSource)
        assertEquals("Medicine A", entry.medicineName)
        assertEquals("After lunch", entry.label)
        assertEquals(undo.occurredAt, entry.undoneAt)
        assertEquals(CheckSource.APP, entry.undoSource)
    }

    @Test
    fun `rechecking after undo creates a new active history entry and retains audit`() {
        val firstCheck = event("check-1", DoseAction.CHECK, "2026-07-29T05:00:00Z")
        val undo =
            event(
                "undo-1",
                DoseAction.UNDO,
                "2026-07-29T05:10:00Z",
                previousActionId = "check-1",
            )
        val secondCheck =
            event(
                "check-2",
                DoseAction.CHECK,
                "2026-07-29T05:20:00Z",
                source = CheckSource.APP_PREVIEW,
            )

        val entries = HistoryAssembler.assemble(listOf(secondCheck, firstCheck, undo))

        assertEquals(2, entries.size)
        assertEquals("check-2", entries[0].eventId)
        assertNull(entries[0].undoneAt)
        assertEquals(CheckSource.APP_PREVIEW, entries[0].checkedSource)
        assertEquals("check-1", entries[1].eventId)
        assertEquals(undo.occurredAt, entries[1].undoneAt)
    }

    @Test
    fun `orphan undo does not fabricate history`() {
        val undo =
            event(
                "undo-1",
                DoseAction.UNDO,
                "2026-07-29T05:10:00Z",
                previousActionId = "missing-check",
            )

        assertTrue(HistoryAssembler.assemble(listOf(undo)).isEmpty())
    }

    @Test
    fun `a duplicate check closes the earlier active record instead of erasing it`() {
        val first = event("check-1", DoseAction.CHECK, "2026-07-29T05:00:00Z")
        val duplicate = event("check-2", DoseAction.CHECK, "2026-07-29T05:00:01Z")

        val entries = HistoryAssembler.assemble(listOf(duplicate, first))

        assertEquals(2, entries.size)
        assertEquals(setOf("check-1", "check-2"), entries.map { it.eventId }.toSet())
        assertFalse(entries.any { it.undoneAt != null })
    }

    @Test
    fun `history is sorted newest check first across logical days`() {
        val older = event("older", DoseAction.CHECK, "2026-07-28T05:00:00Z")
        val newer =
            event(
                id = "newer",
                action = DoseAction.CHECK,
                at = "2026-07-29T05:00:00Z",
                relatedStateId = "2026-07-30_medicine-b_night",
            )

        val entries = HistoryAssembler.assemble(listOf(older, newer))

        assertEquals(listOf("newer", "older"), entries.map(HistoryEntry::eventId))
    }

    @Test
    fun `manual clock rollback cannot detach an undo from its check`() {
        val check = event("check-1", DoseAction.CHECK, "2026-07-29T05:30:00Z")
        val undo =
            event(
                id = "undo-1",
                action = DoseAction.UNDO,
                at = "2026-07-29T04:30:00Z",
                previousActionId = check.eventId,
                source = CheckSource.APP,
            )

        val entry = HistoryAssembler.assemble(listOf(undo, check)).single()

        assertEquals(check.occurredAt, entry.checkedAt)
        assertEquals(undo.occurredAt, entry.undoneAt)
    }

    @Test
    fun `equal device timestamps retain causal undo pairing`() {
        val check = event("check-1", DoseAction.CHECK, "2026-07-29T05:00:00Z")
        val undo =
            event(
                id = "undo-1",
                action = DoseAction.UNDO,
                at = "2026-07-29T05:00:00Z",
                previousActionId = check.eventId,
                source = CheckSource.APP,
            )

        val entry = HistoryAssembler.assemble(listOf(check, undo)).single()

        assertEquals(undo.occurredAt, entry.undoneAt)
    }

    private fun event(
        id: String,
        action: DoseAction,
        at: String,
        source: CheckSource = CheckSource.WIDGET_2X2,
        medicineName: String = "Medicine A",
        label: String = "After lunch",
        relatedStateId: String = stateId,
        previousActionId: String? = null,
    ): DoseEvent =
        DoseEvent(
            eventId = id,
            ownerUid = "user-a",
            action = action,
            logicalDay = day,
            medicineId = "medicine-a",
            medicineNameSnapshot = medicineName,
            slot = DoseSlot.AFTERNOON,
            labelSnapshot = label,
            occurredAt = Instant.parse(at),
            timezoneId = "Asia/Singapore",
            source = source,
            relatedStateId = relatedStateId,
            previousActionId = previousActionId,
        )
}
