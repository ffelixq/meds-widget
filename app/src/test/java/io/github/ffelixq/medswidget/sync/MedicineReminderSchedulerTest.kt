package io.github.ffelixq.medswidget.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MedicineReminderSchedulerTest {
    private val zone = ZoneId.of("Asia/Singapore")

    @Test
    fun `reminder later today uses same-day delay`() {
        val now = ZonedDateTime.of(2026, 8, 9, 7, 30, 0, 0, zone).toInstant()

        val delay =
            MedicineReminderScheduler.delayUntilReminder(
                now = now,
                zone = zone,
                minutesAfterMidnight = 8 * 60,
            )

        assertEquals(Duration.ofMinutes(30), delay)
    }

    @Test
    fun `reminder already passed schedules next day`() {
        val now = ZonedDateTime.of(2026, 8, 9, 8, 30, 0, 0, zone).toInstant()

        val delay =
            MedicineReminderScheduler.delayUntilReminder(
                now = now,
                zone = zone,
                minutesAfterMidnight = 8 * 60,
            )

        assertEquals(Duration.ofHours(23).plusMinutes(30), delay)
    }

    @Test
    fun `future course start delays first reminder until start date`() {
        val now = ZonedDateTime.of(2026, 8, 9, 22, 0, 0, 0, zone).toInstant()

        val delay =
            MedicineReminderScheduler.delayUntilReminder(
                now = now,
                zone = zone,
                minutesAfterMidnight = 8 * 60,
                startDate = LocalDate.of(2026, 8, 11),
            )

        assertEquals(Duration.ofHours(34), delay)
    }
}
