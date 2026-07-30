package io.github.ffelixq.medswidget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class LogicalDayCalculatorTest {
    @Test
    fun `midnight reset uses the local calendar date`() {
        val zone = ZoneId.of("Asia/Singapore")

        assertEquals(
            LocalDate.of(2026, 7, 29),
            logicalDay("2026-07-29T00:00:00", zone, resetMinutes = 0),
        )
        assertEquals(
            LocalDate.of(2026, 7, 28),
            logicalDay("2026-07-28T23:59:59", zone, resetMinutes = 0),
        )
    }

    @Test
    fun `custom reset changes the day exactly at its boundary`() {
        val zone = ZoneId.of("Europe/London")
        val resetMinutes = 4 * 60

        assertEquals(
            LocalDate.of(2026, 4, 12),
            logicalDay("2026-04-13T03:59:59", zone, resetMinutes),
        )
        assertEquals(
            LocalDate.of(2026, 4, 13),
            logicalDay("2026-04-13T04:00:00", zone, resetMinutes),
        )
        assertEquals(
            LocalDate.of(2026, 4, 13),
            logicalDay("2026-04-13T23:59:59", zone, resetMinutes),
        )
    }

    @Test
    fun `custom reset crosses an end of month without deleting the prior day`() {
        val zone = ZoneOffset.UTC

        assertEquals(
            LocalDate.of(2026, 4, 30),
            logicalDay("2026-05-01T01:15:00", zone, resetMinutes = 120),
        )
        assertEquals(
            LocalDate.of(2026, 5, 1),
            logicalDay("2026-05-01T02:00:00", zone, resetMinutes = 120),
        )
    }

    @Test
    fun `custom reset crosses an end of year`() {
        val zone = ZoneOffset.UTC

        assertEquals(
            LocalDate.of(2025, 12, 31),
            logicalDay("2026-01-01T05:59:59", zone, resetMinutes = 360),
        )
        assertEquals(
            LocalDate.of(2026, 1, 1),
            logicalDay("2026-01-01T06:00:00", zone, resetMinutes = 360),
        )
    }

    @Test
    fun `leap day is retained as the previous logical day`() {
        val zone = ZoneOffset.UTC

        assertEquals(
            LocalDate.of(2024, 2, 29),
            logicalDay("2024-03-01T02:59:59", zone, resetMinutes = 180),
        )
        assertEquals(
            LocalDate.of(2024, 3, 1),
            logicalDay("2024-03-01T03:00:00", zone, resetMinutes = 180),
        )
    }

    @Test
    fun `DST forward gap resolves the reset to the first valid local time`() {
        val zone = ZoneId.of("America/New_York")
        val beforeGapDay = zoned("2024-03-09T12:00:00", zone).toInstant()

        val boundary =
            LogicalDayCalculator.nextResetBoundary(
                beforeGapDay,
                zone,
                resetMinutesAfterMidnight = 150,
            )

        assertEquals(LocalDateTime.parse("2024-03-10T03:00:00"), boundary.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-4), boundary.offset)
        assertEquals(
            LocalDate.of(2024, 3, 9),
            LogicalDayCalculator.logicalDay(boundary.toInstant().minusNanos(1), zone, 150),
        )
        assertEquals(
            LocalDate.of(2024, 3, 10),
            LogicalDayCalculator.logicalDay(boundary.toInstant(), zone, 150),
        )
    }

    @Test
    fun `DST backward repeated hour produces the same logical day in both offsets`() {
        val zone = ZoneId.of("America/New_York")
        val repeatedLocal = zoned("2024-11-03T01:45:00", zone)
        val earlierInstant = repeatedLocal.withEarlierOffsetAtOverlap().toInstant()
        val laterInstant = repeatedLocal.withLaterOffsetAtOverlap().toInstant()

        assertEquals(
            LocalDate.of(2024, 11, 3),
            LogicalDayCalculator.logicalDay(earlierInstant, zone, 90),
        )
        assertEquals(
            LocalDate.of(2024, 11, 3),
            LogicalDayCalculator.logicalDay(laterInstant, zone, 90),
        )

        val boundary =
            LogicalDayCalculator.nextResetBoundary(
                zoned("2024-11-02T12:00:00", zone).toInstant(),
                zone,
                90,
            )
        assertEquals(LocalDateTime.parse("2024-11-03T01:30:00"), boundary.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-4), boundary.offset)

        val rollbackBoundary =
            LogicalDayCalculator.nextResetBoundary(
                earlierInstant,
                zone,
                90,
            )
        assertEquals(LocalDateTime.parse("2024-11-03T01:00:00"), rollbackBoundary.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-5), rollbackBoundary.offset)
        assertEquals(
            LocalDate.of(2024, 11, 3),
            LogicalDayCalculator.logicalDay(rollbackBoundary.toInstant().minusNanos(1), zone, 90),
        )
        assertEquals(
            LocalDate.of(2024, 11, 2),
            LogicalDayCalculator.logicalDay(rollbackBoundary.toInstant(), zone, 90),
        )

        val afterRollback =
            zoned("2024-11-03T01:10:00", zone)
                .withLaterOffsetAtOverlap()
                .toInstant()
        val secondReset =
            LogicalDayCalculator.nextResetBoundary(
                afterRollback,
                zone,
                90,
            )
        assertEquals(LocalDateTime.parse("2024-11-03T01:30:00"), secondReset.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(-5), secondReset.offset)
        assertEquals(
            LocalDate.of(2024, 11, 3),
            LogicalDayCalculator.logicalDay(secondReset.toInstant(), zone, 90),
        )
    }

    @Test
    fun `Singapore reset is stable because the zone has no daylight saving`() {
        val zone = ZoneId.of("Asia/Singapore")

        assertEquals(
            LocalDate.of(2026, 7, 28),
            logicalDay("2026-07-29T00:59:59", zone, resetMinutes = 60),
        )
        assertEquals(
            LocalDate.of(2026, 7, 29),
            logicalDay("2026-07-29T01:00:00", zone, resetMinutes = 60),
        )
        assertEquals(
            24L,
            LogicalDayCalculator
                .delayUntilNextBoundary(
                    zoned("2026-07-29T01:00:00", zone).toInstant(),
                    zone,
                    60,
                ).toHours(),
        )
    }

    @Test
    fun `timezone change recomputes the logical date from the same instant`() {
        val instant = Instant.parse("2026-01-01T17:00:00Z")

        assertEquals(
            LocalDate.of(2026, 1, 2),
            LogicalDayCalculator.logicalDay(instant, ZoneId.of("Asia/Singapore"), 0),
        )
        assertEquals(
            LocalDate.of(2026, 1, 1),
            LogicalDayCalculator.logicalDay(instant, ZoneId.of("America/Los_Angeles"), 0),
        )
    }

    @Test
    fun `manual clock move across reset always recomputes rather than caching a day`() {
        val zone = ZoneId.of("Asia/Singapore")
        val afterReset = zoned("2026-07-29T07:00:00", zone).toInstant()
        val clockMovedBack = zoned("2026-07-29T05:00:00", zone).toInstant()

        assertEquals(
            LocalDate.of(2026, 7, 29),
            LogicalDayCalculator.logicalDay(afterReset, zone, 360),
        )
        assertEquals(
            LocalDate.of(2026, 7, 28),
            LogicalDayCalculator.logicalDay(clockMovedBack, zone, 360),
        )
    }

    @Test
    fun `invalid reset minutes are rejected consistently`() {
        val instant = Instant.parse("2026-07-29T00:00:00Z")

        assertThrows(IllegalArgumentException::class.java) {
            LogicalDayCalculator.logicalDay(instant, ZoneOffset.UTC, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LogicalDayCalculator.nextResetBoundary(
                instant,
                ZoneOffset.UTC,
                LogicalDayCalculator.MINUTES_PER_DAY,
            )
        }
    }

    private fun logicalDay(
        localDateTime: String,
        zone: ZoneId,
        resetMinutes: Int,
    ): LocalDate =
        LogicalDayCalculator.logicalDay(
            zoned(localDateTime, zone).toInstant(),
            zone,
            resetMinutes,
        )

    private fun zoned(
        localDateTime: String,
        zone: ZoneId,
    ): ZonedDateTime = LocalDateTime.parse(localDateTime).atZone(zone)
}
