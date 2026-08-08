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
    val action: DoseAction = DoseAction.CHECK,
    val skipReason: String? = null,
    val undoneAt: Instant? = null,
    val undoTimezone: String? = null,
    val undoSource: CheckSource? = null,
) {
    val isUndone: Boolean get() = undoneAt != null
    val isTaken: Boolean get() = action == DoseAction.CHECK && !isUndone
    val isSkipped: Boolean get() = action == DoseAction.SKIP && !isUndone
}

object HistoryAssembler {
    fun assemble(events: List<DoseEvent>): List<HistoryEntry> {
        val undoByActionId =
            events
                .asSequence()
                .filter { it.action == DoseAction.UNDO }
                .mapNotNull { undo -> undo.previousActionId?.let { resolvedId -> resolvedId to undo } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, undos) ->
                    undos.maxWith(compareBy(DoseEvent::occurredAt, DoseEvent::syncedAt, DoseEvent::eventId))
                }

        return events
            .asSequence()
            .filter { it.action == DoseAction.CHECK || it.action == DoseAction.SKIP }
            .map { event ->
                val undo = undoByActionId[event.eventId]
                HistoryEntry(
                    eventId = event.eventId,
                    logicalDay = event.logicalDay,
                    medicineName = event.medicineNameSnapshot,
                    label = event.labelSnapshot,
                    slot = event.slot,
                    checkedAt = event.occurredAt,
                    checkedTimezone = event.timezoneId,
                    checkedSource = event.source,
                    action = event.action,
                    skipReason = event.skipReason,
                    undoneAt = undo?.occurredAt,
                    undoTimezone = undo?.timezoneId,
                    undoSource = undo?.source,
                )
            }.sortedByDescending(HistoryEntry::checkedAt)
            .toList()
    }
}

object AdherenceCalculator {
    fun summarize(
        events: List<DoseEvent>,
        medicines: List<Medicine>,
        logicalToday: LocalDate,
        days: Int,
    ): AdherenceSummary {
        require(days > 0)
        val startDay = logicalToday.minusDays(days.toLong() - 1)
        val finalActions = finalActions(events)
        var scheduled = 0
        var taken = 0
        var skipped = 0
        var missed = 0

        var day = startDay
        while (!day.isAfter(logicalToday)) {
            medicines
                .asSequence()
                .filter { it.isActiveOn(day) }
                .forEach { medicine ->
                    medicine.enabledSlots().forEach { slot ->
                        val stateId = DoseIds.stateId(day, medicine.id, slot)
                        val action = finalActions[stateId]?.action
                        val isDue = day.isBefore(logicalToday) || action == DoseAction.CHECK || action == DoseAction.SKIP
                        if (isDue) {
                            scheduled += 1
                            when (action) {
                                DoseAction.CHECK -> taken += 1
                                DoseAction.SKIP -> skipped += 1
                                DoseAction.UNDO,
                                null,
                                -> missed += 1
                            }
                        }
                    }
                }
            day = day.plusDays(1)
        }
        return AdherenceSummary(scheduled, taken, skipped, missed)
    }

    private fun finalActions(events: List<DoseEvent>): Map<String, DoseEvent> =
        events
            .groupBy(DoseEvent::relatedStateId)
            .mapValues { (_, stateEvents) ->
                stateEvents.maxWith(compareBy(DoseEvent::occurredAt, DoseEvent::syncedAt, DoseEvent::eventId))
            }
}
