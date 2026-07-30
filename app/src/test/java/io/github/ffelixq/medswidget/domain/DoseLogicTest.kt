package io.github.ffelixq.medswidget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DoseLogicTest {
    private val day = LocalDate.of(2026, 7, 29)
    private val medicine =
        Medicine(
            id = "medicine-a",
            ownerUid = "user-a",
            name = "Medicine A",
            afternoonEnabled = true,
            afternoonLabel = "After lunch",
            nightEnabled = true,
            nightLabel = "Before bed",
        )

    @Test
    fun `dose state IDs are deterministic by day medicine and slot`() {
        assertEquals(
            "2026-07-29_medicine-a_afternoon",
            DoseIds.stateId(day, "medicine-a", DoseSlot.AFTERNOON),
        )
        assertEquals(
            DoseIds.stateId(day, "medicine-a", DoseSlot.NIGHT),
            DoseIds.stateId(day, "medicine-a", DoseSlot.NIGHT),
        )
    }

    @Test
    fun `checking an unchecked or missing state applies once`() {
        assertEquals(DoseCommandDecision.APPLY_CHECK, DoseActionPolicy.check(null))
        assertEquals(
            DoseCommandDecision.APPLY_CHECK,
            DoseActionPolicy.check(state(isTaken = false)),
        )
        assertEquals(
            DoseCommandDecision.NO_OP_ALREADY_TAKEN,
            DoseActionPolicy.check(state(isTaken = true)),
        )
    }

    @Test
    fun `every non-app undo source is rejected`() {
        assertEquals(
            DoseCommandDecision.REJECT_NON_APP_UNDO,
            DoseActionPolicy.undo(state(isTaken = true), CheckSource.WIDGET_2X2),
        )
        assertEquals(
            DoseCommandDecision.REJECT_NON_APP_UNDO,
            DoseActionPolicy.undo(state(isTaken = true), CheckSource.WIDGET_4X2),
        )
        assertEquals(
            DoseCommandDecision.REJECT_NON_APP_UNDO,
            DoseActionPolicy.undo(null, CheckSource.WIDGET_4X2),
        )
        assertEquals(
            DoseCommandDecision.REJECT_NON_APP_UNDO,
            DoseActionPolicy.undo(state(isTaken = true), CheckSource.APP_PREVIEW),
        )
    }

    @Test
    fun `app undo applies only to an active checked state`() {
        assertEquals(
            DoseCommandDecision.APPLY_UNDO,
            DoseActionPolicy.undo(state(isTaken = true), CheckSource.APP),
        )
        assertEquals(
            DoseCommandDecision.NO_OP_ALREADY_UNCHECKED,
            DoseActionPolicy.undo(state(isTaken = false), CheckSource.APP),
        )
        assertEquals(
            DoseCommandDecision.NO_OP_ALREADY_UNCHECKED,
            DoseActionPolicy.undo(null, CheckSource.APP),
        )
    }

    @Test
    fun `rows include every enabled slot and its historical label snapshot state`() {
        val checkedAt = Instant.parse("2026-07-29T13:05:00Z")
        val afternoonState =
            state(
                isTaken = true,
                slot = DoseSlot.AFTERNOON,
                checkedAt = checkedAt,
                labelSnapshot = "Old lunch label",
            )

        val rows = DoseRows.build(listOf(medicine), listOf(afternoonState), day)

        assertEquals(2, rows.size)
        val afternoon = rows.single { it.slot == DoseSlot.AFTERNOON }
        val night = rows.single { it.slot == DoseSlot.NIGHT }
        assertEquals("After lunch", afternoon.label)
        assertTrue(afternoon.isTaken)
        assertEquals(checkedAt, afternoon.checkedAt)
        assertEquals("Asia/Singapore", afternoon.checkedTimezone)
        assertEquals("2026-07-29_medicine-a_afternoon", afternoon.stateId)
        assertEquals("Before bed", night.label)
        assertFalse(night.isTaken)
        assertNull(night.checkedAt)
        assertNull(night.checkedTimezone)
    }

    @Test
    fun `undone state retains audit timestamp but row hides the old checked time`() {
        val previousCheck = Instant.parse("2026-07-29T13:05:00Z")
        val undoneState =
            state(
                isTaken = false,
                checkedAt = previousCheck,
                labelSnapshot = "Old lunch label",
            )

        val row =
            DoseRows
                .build(listOf(medicine), listOf(undoneState), day)
                .single { it.slot == DoseSlot.AFTERNOON }

        assertEquals(previousCheck, undoneState.checkedAt)
        assertFalse(row.isTaken)
        assertNull(row.checkedAt)
        assertNull(row.checkedTimezone)
    }

    @Test
    fun `archived medicines and disabled slots are excluded`() {
        val nightOnly = medicine.copy(id = "night-only", afternoonEnabled = false)
        val archived = medicine.copy(id = "archived", archived = true)

        val rows = DoseRows.build(listOf(nightOnly, archived), emptyList(), day)

        assertEquals(1, rows.size)
        assertEquals("night-only", rows.single().medicineId)
        assertEquals(DoseSlot.NIGHT, rows.single().slot)
    }

    @Test
    fun `progress counts checked rows and formats both display variants`() {
        val rows =
            listOf(
                row("a", true),
                row("b", false),
                row("c", true),
                row("d", false),
            )

        val progress = DoseRows.progress(rows)

        assertEquals(2, progress.completed)
        assertEquals(4, progress.total)
        assertEquals("2 of 4 completed", progress.display)
        assertEquals("2/4", progress.compactDisplay)
    }

    @Test
    fun `empty dose list has zero progress without division concerns`() {
        assertEquals(CompletionProgress(0, 0), DoseRows.progress(emptyList()))
    }

    @Test
    fun `long display names are safely truncated with one ellipsis`() {
        assertEquals("Short", DisplayTransform.truncate("Short", 10))
        assertEquals("123456789…", DisplayTransform.truncate("12345678901", 10))
        assertEquals("word…", DisplayTransform.truncate("word     tail", 6))
    }

    @Test
    fun `truncation rejects unusably small limits`() {
        assertThrows(IllegalArgumentException::class.java) {
            DisplayTransform.truncate("Medicine", 1)
        }
    }

    private fun state(
        isTaken: Boolean,
        slot: DoseSlot = DoseSlot.AFTERNOON,
        checkedAt: Instant? = if (isTaken) Instant.parse("2026-07-29T13:00:00Z") else null,
        labelSnapshot: String = medicine.label(slot),
    ): DoseState =
        DoseState(
            id = DoseIds.stateId(day, medicine.id, slot),
            ownerUid = medicine.ownerUid,
            logicalDay = day,
            medicineId = medicine.id,
            slot = slot,
            labelSnapshot = labelSnapshot,
            medicineNameSnapshot = medicine.name,
            isTaken = isTaken,
            checkedAt = checkedAt,
            checkedTimezone = if (checkedAt == null) null else "Asia/Singapore",
            checkedSource = if (checkedAt == null) null else CheckSource.APP,
            undoneAt = null,
            lastActionId = "action-1",
        )

    private fun row(
        id: String,
        taken: Boolean,
    ): DoseRow =
        DoseRow(
            medicineId = id,
            medicineName = "Medicine $id",
            slot = DoseSlot.AFTERNOON,
            label = "Afternoon",
            isTaken = taken,
            checkedAt = null,
            stateId = "${day}_${id}_afternoon",
        )
}
