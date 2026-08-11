package io.github.ffelixq.medswidget.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import io.github.ffelixq.medswidget.domain.CountdownDisplay
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.DoseRow
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Suppress("FunctionNaming")
@Composable
internal fun showcaseCountdown(row: DoseRow): CountdownDisplay {
    val now by produceState(initialValue = Instant.now(), row.countdown?.targetAt) {
        while (row.countdown != null) {
            val remaining = Duration.between(Instant.now(), row.countdown.targetAt)
            if (remaining.isNegative || remaining.isZero) {
                value = Instant.now()
                break
            }
            delay(minOf(remaining.toMillis().coerceAtLeast(1_000L), 60_000L))
            value = Instant.now()
        }
    }
    return CountdownLogic.display(row.countdownMinutes, row.countdown, now)
}
