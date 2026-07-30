package io.github.ffelixq.medswidget.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

object LogicalDayCalculator {
    const val MINUTES_PER_DAY = 24 * 60

    fun logicalDay(
        instant: Instant,
        zoneId: ZoneId,
        resetMinutesAfterMidnight: Int,
    ): LocalDate {
        require(resetMinutesAfterMidnight in 0 until MINUTES_PER_DAY)
        val local = instant.atZone(zoneId)
        val resetTime = LocalTime.ofSecondOfDay(resetMinutesAfterMidnight * 60L)
        return if (local.toLocalTime() >= resetTime) {
            local.toLocalDate()
        } else {
            local.toLocalDate().minusDays(1)
        }
    }

    fun nextResetBoundary(
        instant: Instant,
        zoneId: ZoneId,
        resetMinutesAfterMidnight: Int,
    ): ZonedDateTime {
        require(resetMinutesAfterMidnight in 0 until MINUTES_PER_DAY)
        val resetTime = LocalTime.ofSecondOfDay(resetMinutesAfterMidnight * 60L)
        val firstDate = instant.atZone(zoneId).toLocalDate()
        val boundary =
            (0L..2L)
                .asSequence()
                .flatMap { dayOffset ->
                    transitionCandidates(
                        date = firstDate.plusDays(dayOffset),
                        resetTime = resetTime,
                        zoneId = zoneId,
                    ).asSequence()
                }.filter { candidate -> candidate > instant }
                .filter { candidate ->
                    logicalDay(candidate.minusNanos(1), zoneId, resetMinutesAfterMidnight) !=
                        logicalDay(candidate, zoneId, resetMinutesAfterMidnight)
                }.minOrNull()
                ?: error("A logical-day boundary must exist within the next two local dates.")
        return boundary.atZone(zoneId)
    }

    fun delayUntilNextBoundary(
        instant: Instant,
        zoneId: ZoneId,
        resetMinutesAfterMidnight: Int,
    ): Duration = Duration.between(instant, nextResetBoundary(instant, zoneId, resetMinutesAfterMidnight).toInstant())

    private fun transitionCandidates(
        date: LocalDate,
        resetTime: LocalTime,
        zoneId: ZoneId,
    ): Set<Instant> {
        val localReset = date.atTime(resetTime)
        val rules = zoneId.rules
        val validOffsets = rules.getValidOffsets(localReset)
        val candidates =
            validOffsets
                .mapTo(mutableSetOf()) { offset: ZoneOffset ->
                    localReset.toInstant(offset)
                }
        // A reset inside a skipped hour occurs at the gap transition. A reset
        // inside a repeated hour changes the logical day at both reset
        // occurrences and at the clock rollback itself.
        rules.getTransition(localReset)?.instant?.let(candidates::add)
        return candidates
    }
}
