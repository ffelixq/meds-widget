package io.github.ffelixq.medswidget.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil

object CountdownIds {
    fun stateId(
        logicalDay: LocalDate,
        medicineId: String,
        slot: DoseSlot,
    ): String = "${logicalDay}_${medicineId}_${slot.wireValue}"
}

enum class CountdownDisplayStatus {
    NOT_CONFIGURED,
    NOT_STARTED,
    RUNNING,
    READY,
    ENDED,
}

data class CountdownDisplay(
    val status: CountdownDisplayStatus,
    val text: String?,
    val remainingMinutes: Long? = null,
)

object CountdownLogic {
    fun targetAt(
        startedAt: Instant,
        durationMinutes: Int,
    ): Instant {
        require(durationMinutes in COUNTDOWN_MIN_MINUTES..COUNTDOWN_MAX_MINUTES)
        return startedAt.plus(Duration.ofMinutes(durationMinutes.toLong()))
    }

    fun display(
        configuredMinutes: Int?,
        state: CountdownState?,
        now: Instant,
    ): CountdownDisplay =
        when {
            state != null && state.status != CountdownStatus.RUNNING -> {
                CountdownDisplay(CountdownDisplayStatus.ENDED, null)
            }

            state != null && !now.isBefore(state.targetAt) -> {
                CountdownDisplay(CountdownDisplayStatus.READY, "READY", 0)
            }

            state != null -> {
                val millis = Duration.between(now, state.targetAt).toMillis()
                val minutes = ceil(millis / 60_000.0).toLong().coerceAtLeast(1)
                CountdownDisplay(
                    CountdownDisplayStatus.RUNNING,
                    formatRemaining(minutes),
                    minutes,
                )
            }

            configuredMinutes == null -> {
                CountdownDisplay(CountdownDisplayStatus.NOT_CONFIGURED, null)
            }

            else -> {
                CountdownDisplay(
                    CountdownDisplayStatus.NOT_STARTED,
                    "Start ${formatDuration(configuredMinutes)}",
                )
            }
        }

    fun formatDuration(minutes: Int): String =
        when {
            minutes % 60 == 0 -> "${minutes / 60}h"
            minutes > 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "${minutes}m"
        }

    fun formatRemaining(minutes: Long): String =
        when {
            minutes % 60 == 0L -> "${minutes / 60}h"
            minutes > 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "${minutes}m"
        }

    fun nextRefreshDelay(
        now: Instant,
        targetAt: Instant,
    ): Duration {
        if (!now.isBefore(targetAt)) return Duration.ZERO
        val remaining = Duration.between(now, targetAt)
        val cadence =
            when {
                remaining > Duration.ofMinutes(60) -> Duration.ofMinutes(10)
                remaining > Duration.ofMinutes(15) -> Duration.ofMinutes(5)
                else -> Duration.ofMinutes(1)
            }
        return minOf(remaining, cadence).coerceAtLeast(Duration.ofSeconds(1))
    }
}
