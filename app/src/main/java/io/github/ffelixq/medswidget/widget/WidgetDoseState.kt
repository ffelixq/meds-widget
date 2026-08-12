package io.github.ffelixq.medswidget.widget

import io.github.ffelixq.medswidget.AppGraph
import io.github.ffelixq.medswidget.domain.DoseSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal fun widgetDoseKey(
    medicineId: String,
    slot: DoseSlot,
): String = "$medicineId|${slot.wireValue}"

internal suspend fun AppGraph.skippedWidgetDoseKeys(snapshot: WidgetSnapshot): Set<String> {
    val uid = snapshot.ownerUid
    return if (!snapshot.signedIn || uid == null) {
        emptySet()
    } else {
        withTimeoutOrNull(STATE_READ_TIMEOUT_MILLIS) {
            repositories.doses
                .observeDay(uid, snapshot.logicalDay)
                .first()
                .value
                .asSequence()
                .filter { it.isSkipped }
                .map { widgetDoseKey(it.medicineId, it.slot) }
                .toSet()
        }.orEmpty()
    }
}

private const val STATE_READ_TIMEOUT_MILLIS = 1_500L
