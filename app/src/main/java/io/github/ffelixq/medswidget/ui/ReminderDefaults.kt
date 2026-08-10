package io.github.ffelixq.medswidget.ui

import io.github.ffelixq.medswidget.domain.DoseSlot

internal fun defaultReminderMinutes(slot: DoseSlot): Int =
    when (slot) {
        DoseSlot.MORNING -> 8 * 60
        DoseSlot.AFTERNOON -> 13 * 60
        DoseSlot.EVENING -> 18 * 60
        DoseSlot.NIGHT -> 22 * 60
    }
