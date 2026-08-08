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
        val undoByActionId = latestUndoByActionId(events)
        return events
            .asSequence()
            .filter { it.action == DoseAction.CHECK || it.action == DoseAction.SKIP }
            .map { event -> event.toHistoryEntry(undoByActionId[event.eventId]) }
            .sortedByDescending(HistoryEntry::checkedAt)
            .toList()
    }

    private fun latestUndoByActionId(events: List<DoseEvent>): Map<String, DoseEvent> =
        events
            .asSequence()
            .filter { it.action == DoseAction.UNDO }
            .mapNotNull { undo -> undo.previousActionId?.let { actionId -> actionId to undo } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, undos) ->
                undos.maxWith(
                    compareBy(DoseEvent::occurredAt, DoseEvent::syncedAt, DoseEvent::eventId),
                )
            }

    private fun DoseEvent.toHistoryEntry(undo: DoseEvent?): HistoryEntry =
        HistoryEntry(
            eventId = eventId,
            logicalDay = logicalDay,
            medicineName = medicineNameSnapshot,
            label = labelSnapshot,
            slot = slot,
            checkedAt = occurredAt,
            checkedTimezone = timezoneId,
            checkedSource = source,
            action = action,
            skipReason = skipReason,
            undoneAt = undo?.occurredAt,
            undoTimezone = undo?.timezoneId,
            undoSource = undo?.source,
        )
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
        val dueDoses = expectedDoses(medicines, startDay, logicalToday, finalActions)
        return AdherenceSummary(
            scheduled = dueDoses.size,
            taken = dueDoses.count { it.action == DoseAction.CHECK },
            skipped = dueDoses.count { it.action == DoseAction.SKIP },
            missed = dueDoses.count { it.action == null || it.action == DoseAction.UNDO },
        )
    }

    private fun expectedDoses(
        medicines: List<Medicine>,
        startDay: LocalDate,
        logicalToday: LocalDate,
        finalActions: Map<String, DoseEvent>,
    ): List<ExpectedDose> {
        val expected = mutableListOf<ExpectedDose>()
        var day = startDay
        while (!day.isAfter(logicalToday)) {
            appendExpectedDosesForDay(expected, medicines, day, logicalToday, finalActions)
            day = day.plusDays(1)
        }
        return expected
    }

    private fun appendExpectedDosesForDay(
        destination: MutableList<ExpectedDose>,
        medicines: List<Medicine>,
        day: LocalDate,
        logicalToday: LocalDate,
        finalActions: Map<String, DoseEvent>,
    ) {
        medicines
            .asSequence()
            .filter { it.isActiveOn(day) }
            .flatMap { medicine ->
                medicine.enabledSlots().asSequence().map { slot -> medicine.id to slot }
            }.forEach { (medicineId, slot) ->
                val stateId = DoseIds.stateId(day, medicineId, slot)
                val action = finalActions[stateId]?.action
                val isDue =
                    day.isBefore(logicalToday) ||
                        action == DoseAction.CHECK ||
                        action == DoseAction.SKIP
                if (isDue) destination += ExpectedDose(action)
            }
    }

    private fun finalActions(events: List<DoseEvent>): Map<String, DoseEvent> =
        events
            .groupBy(DoseEvent::relatedStateId)
            .mapValues { (_, stateEvents) ->
                stateEvents.maxWith(
                    compareBy(DoseEvent::occurredAt, DoseEvent::syncedAt, DoseEvent::eventId),
                )
            }

    private data class ExpectedDose(
        val action: DoseAction?,
    )
}
