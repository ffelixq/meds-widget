package io.github.ffelixq.medswidget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MedicineV2DomainTest {
    @Test
    fun `four daily slots are projected in stable order`() {
        val day = LocalDate.of(2026, 8, 9)
        val medicine =
            medicine(
                morningEnabled = true,
                afternoonEnabled = true,
                eveningEnabled = true,
                nightEnabled = true,
            )

        val rows = DoseRows.build(listOf(medicine), emptyList(), day)

        assertEquals(
            listOf(
                DoseSlot.MORNING,
                DoseSlot.AFTERNOON,
                DoseSlot.EVENING,
                DoseSlot.NIGHT,
            ),
            rows.map(DoseRow::slot),
        )
    }

    @Test
    fun `course dates include only active logical days`() {
        val medicine =
            medicine(
                startDate = LocalDate.of(2026, 8, 10),
                endDate = LocalDate.of(2026, 8, 12),
            )

        assertTrue(DoseRows.build(listOf(medicine), emptyList(), LocalDate.of(2026, 8, 9)).isEmpty())
        assertFalse(DoseRows.build(listOf(medicine), emptyList(), LocalDate.of(2026, 8, 10)).isEmpty())
        assertFalse(DoseRows.build(listOf(medicine), emptyList(), LocalDate.of(2026, 8, 12)).isEmpty())
        assertTrue(DoseRows.build(listOf(medicine), emptyList(), LocalDate.of(2026, 8, 13)).isEmpty())
    }

    @Test
    fun `widget privacy uses nickname or generic name`() {
        val named = medicine(nickname = "Night med", widgetNameMode = WidgetNameMode.NICKNAME)
        val hidden = medicine(widgetNameMode = WidgetNameMode.HIDDEN)

        assertEquals("Night med", named.widgetDisplayName())
        assertEquals("Medicine", hidden.widgetDisplayName())
    }

    @Test
    fun `medicine validation accepts independent reminder and countdown slots`() {
        val result =
            MedicineValidator.validate(
                MedicineDraft(
                    name = "Medicine",
                    morningEnabled = true,
                    morningLabel = "After breakfast",
                    morningCountdownMinutes = 30,
                    morningReminderMinutes = 8 * 60,
                    afternoonEnabled = false,
                    eveningEnabled = true,
                    eveningLabel = "After dinner",
                    eveningCountdownMinutes = 120,
                    eveningReminderMinutes = 20 * 60,
                    nightEnabled = false,
                    startDate = LocalDate.of(2026, 8, 9),
                    endDate = LocalDate.of(2026, 8, 20),
                ),
            )

        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(30, result.normalized.morningCountdownMinutes)
        assertEquals(120, result.normalized.eveningCountdownMinutes)
    }

    @Test
    fun `skip state is resolved and cannot be checked again until undo`() {
        val skipped =
            DoseState(
                id = "2026-08-09_med_night",
                ownerUid = "user",
                logicalDay = LocalDate.of(2026, 8, 9),
                medicineId = "med",
                slot = DoseSlot.NIGHT,
                labelSnapshot = "Night",
                medicineNameSnapshot = "Medicine",
                isTaken = false,
                checkedAt = null,
                checkedTimezone = null,
                checkedSource = null,
                skippedAt = Instant.parse("2026-08-09T14:00:00Z"),
                skipReason = "Away from home",
                undoneAt = null,
                lastActionId = "skip-action",
            )

        assertTrue(skipped.isSkipped)
        assertEquals(DoseCommandDecision.NO_OP_ALREADY_RESOLVED, DoseActionPolicy.check(skipped))
        assertEquals(DoseCommandDecision.APPLY_UNDO, DoseActionPolicy.undo(skipped, CheckSource.APP))
        assertEquals(DoseCommandDecision.REJECT_NON_APP_UNDO, DoseActionPolicy.undo(skipped, CheckSource.WIDGET_4X2))
    }

    @Test
    fun `adherence counts taken skipped and missed past doses`() {
        val today = LocalDate.of(2026, 8, 9)
        val medicine =
            medicine(
                morningEnabled = true,
                afternoonEnabled = false,
                eveningEnabled = false,
                nightEnabled = true,
                startDate = today.minusDays(1),
            )
        val yesterday = today.minusDays(1)
        val events =
            listOf(
                event(
                    id = "taken",
                    day = yesterday,
                    slot = DoseSlot.MORNING,
                    action = DoseAction.CHECK,
                ),
                event(
                    id = "skipped",
                    day = yesterday,
                    slot = DoseSlot.NIGHT,
                    action = DoseAction.SKIP,
                ),
            )

        val summary = AdherenceCalculator.summarize(events, listOf(medicine), today, 2)

        assertEquals(2, summary.scheduled)
        assertEquals(1, summary.taken)
        assertEquals(1, summary.skipped)
        assertEquals(0, summary.missed)
        assertEquals(50.0, summary.adherencePercent, 0.001)
    }

    private fun medicine(
        nickname: String = "",
        widgetNameMode: WidgetNameMode = WidgetNameMode.FULL,
        morningEnabled: Boolean = false,
        afternoonEnabled: Boolean = false,
        eveningEnabled: Boolean = false,
        nightEnabled: Boolean = true,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): Medicine =
        Medicine(
            id = "med",
            ownerUid = "user",
            name = "Medicine",
            nickname = nickname,
            widgetNameMode = widgetNameMode,
            morningEnabled = morningEnabled,
            morningLabel = "Morning",
            afternoonEnabled = afternoonEnabled,
            afternoonLabel = "Afternoon",
            eveningEnabled = eveningEnabled,
            eveningLabel = "Evening",
            nightEnabled = nightEnabled,
            nightLabel = "Night",
            startDate = startDate,
            endDate = endDate,
        )

    private fun event(
        id: String,
        day: LocalDate,
        slot: DoseSlot,
        action: DoseAction,
    ): DoseEvent =
        DoseEvent(
            eventId = id,
            ownerUid = "user",
            action = action,
            logicalDay = day,
            medicineId = "med",
            medicineNameSnapshot = "Medicine",
            slot = slot,
            labelSnapshot = slot.defaultLabel,
            occurredAt = Instant.parse("2026-08-08T10:00:00Z"),
            timezoneId = "Asia/Singapore",
            source = CheckSource.APP,
            relatedStateId = DoseIds.stateId(day, "med", slot),
            skipReason = if (action == DoseAction.SKIP) "Skipped" else null,
        )
}