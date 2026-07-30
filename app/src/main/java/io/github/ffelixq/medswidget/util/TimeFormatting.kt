package io.github.ffelixq.medswidget.util

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.util.Date
import java.util.TimeZone

object TimeFormatting {
    fun compact(
        context: Context,
        instant: Instant,
        timezoneId: String? = null,
    ): String {
        val formatter = DateFormat.getTimeFormat(context)
        if (timezoneId != null) {
            formatter.timeZone = TimeZone.getTimeZone(timezoneId)
        }
        return formatter.format(Date.from(instant))
    }
}
