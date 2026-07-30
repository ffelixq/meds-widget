package io.github.ffelixq.medswidget.domain

import java.time.Instant
import java.time.LocalDate

data class HistoryEntry(
    val eventId: String,
    val logicalDay: LocalDate,
    val medicineName: String,
    val label: String,
    val slot: DoseSlot,
    val checkedAt: Instant,
    val checkedTimezone: String,
    val checkedSource: CheckSource,
    val undoneAt: Instant? = null,
    val undoTimezone: String? = null,
    val undoSource: CheckSource? = null,
)

object HistoryAssembler {
    fun assemble(events: List<DoseEvent>): List<HistoryEntry> {
        val undoByCheckId =
            events
                .asSequence()
                .filter { it.action == DoseAction.UNDO }
                .mapNotNull { undo -> undo.previousActionId?.let { checkId -> checkId to undo } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, undos) ->
                    undos.maxWith(compareBy(DoseEvent::syncedAt, DoseEvent::occurredAt, DoseEvent::eventId))
                }

        return events
            .asSequence()
            .filter { it.action == DoseAction.CHECK }
            .map { check ->
                val undo = undoByCheckId[check.eventId]
                HistoryEntry(
                    eventId = check.eventId,
                    logicalDay = check.logicalDay,
                    medicineName = check.medicineNameSnapshot,
                    label = check.labelSnapshot,
                    slot = check.slot,
                    checkedAt = check.occurredAt,
                    checkedTimezone = check.timezoneId,
                    checkedSource = check.source,
                    undoneAt = undo?.occurredAt,
                    undoTimezone = undo?.timezoneId,
                    undoSource = undo?.source,
                )
            }.sortedByDescending(HistoryEntry::checkedAt)
            .toList()
    }
}
