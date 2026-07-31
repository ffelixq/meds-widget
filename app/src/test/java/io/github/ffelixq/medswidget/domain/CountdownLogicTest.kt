package io.github.ffelixq.medswidget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class CountdownLogicTest {
    private val startedAt = Instant.parse("2026-07-29T15:30:00Z")

    @Test
    fun `presets and custom duration create exact timestamp targets`() {
        listOf(30, 60, 90, 120, 1, 1_440).forEach { minutes ->
            assertEquals(startedAt.plusSeconds(minutes * 60L), CountdownLogic.targetAt(startedAt, minutes))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duration below one minute is rejected`() {
        CountdownLogic.targetAt(startedAt, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duration beyond one day is rejected`() {
        CountdownLogic.targetAt(startedAt, 1_441)
    }

    @Test
    fun `remaining display uses ceiling rounding and compact hours`() {
        val state = countdown(targetAt = startedAt.plusSeconds(7_200))
        assertEquals("2h", CountdownLogic.display(120, state, startedAt).text)
        assertEquals(
            "1h 42m",
            CountdownLogic.display(120, state, startedAt.plusSeconds(18 * 60 + 1)).text,
        )
        assertEquals("59m", CountdownLogic.display(120, state, startedAt.plusSeconds(61 * 60 + 1)).text)
        assertEquals("1m", CountdownLogic.display(120, state, state.targetAt.minusMillis(1)).text)
        assertEquals("READY", CountdownLogic.display(120, state, state.targetAt).text)
    }

    @Test
    fun `remaining time derives only from target timestamp across midnight and clock movement`() {
        val state = countdown(targetAt = startedAt.plusSeconds(90 * 60))
        assertEquals("1h 30m", CountdownLogic.display(90, state, startedAt).text)
        assertEquals("30m", CountdownLogic.display(90, state, startedAt.plusSeconds(60 * 60)).text)
        assertEquals("1h 35m", CountdownLogic.display(90, state, startedAt.minusSeconds(5 * 60)).text)
    }

    @Test
    fun `adaptive refresh cadence is bounded by target`() {
        assertEquals(
            Duration.ofMinutes(10),
            CountdownLogic.nextRefreshDelay(startedAt, startedAt.plusSeconds(2 * 60 * 60)),
        )
        assertEquals(
            Duration.ofMinutes(5),
            CountdownLogic.nextRefreshDelay(startedAt, startedAt.plusSeconds(45 * 60)),
        )
        assertEquals(
            Duration.ofMinutes(1),
            CountdownLogic.nextRefreshDelay(startedAt, startedAt.plusSeconds(10 * 60)),
        )
        assertEquals(
            Duration.ofSeconds(20),
            CountdownLogic.nextRefreshDelay(startedAt, startedAt.plusSeconds(20)),
        )
    }

    @Test
    fun `ended states are not presented as running or ready`() {
        val cancelled = countdown().copy(status = CountdownStatus.CANCELLED, cancelledAt = startedAt)
        val consumed = countdown().copy(status = CountdownStatus.CONSUMED, completedAt = startedAt)
        assertEquals(CountdownDisplayStatus.ENDED, CountdownLogic.display(120, cancelled, startedAt).status)
        assertEquals(CountdownDisplayStatus.ENDED, CountdownLogic.display(120, consumed, startedAt).status)
    }

    @Test
    fun `running countdown remains visible when future starts are disabled`() {
        val state = countdown(targetAt = startedAt.plusSeconds(90 * 60))
        val display = CountdownLogic.display(configuredMinutes = null, state = state, now = startedAt)
        assertEquals(CountdownDisplayStatus.RUNNING, display.status)
        assertEquals("1h 30m", display.text)
    }

    @Test
    fun `deterministic id binds logical day medicine and slot`() {
        assertEquals(
            "2026-07-29_medicine-a_night",
            CountdownIds.stateId(LocalDate.of(2026, 7, 29), "medicine-a", DoseSlot.NIGHT),
        )
    }

    @Test
    fun `ready never implies dose completion`() {
        val display = CountdownLogic.display(120, countdown(targetAt = startedAt), startedAt)
        assertEquals(CountdownDisplayStatus.READY, display.status)
        assertTrue(display.remainingMinutes == 0L)
    }

    private fun countdown(targetAt: Instant = startedAt.plusSeconds(60)): CountdownState =
        CountdownState(
            id = "2026-07-29_medicine-a_afternoon",
            ownerUid = "user-a",
            logicalDay = LocalDate.of(2026, 7, 29),
            medicineId = "medicine-a",
            slot = DoseSlot.AFTERNOON,
            durationMinutes = 120,
            startedAt = startedAt,
            targetAt = targetAt,
            startedTimezone = "Asia/Singapore",
            startedSource = CheckSource.APP,
            status = CountdownStatus.RUNNING,
            cancelledAt = null,
            completedAt = null,
            lastActionId = "action-a",
        )
}
