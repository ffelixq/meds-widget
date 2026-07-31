package io.github.ffelixq.medswidget.domain

import java.time.LocalDate

object DoseIds {
    fun stateId(
        logicalDay: LocalDate,
        medicineId: String,
        slot: DoseSlot,
    ): String = "${logicalDay}_${medicineId}_${slot.wireValue}"
}

enum class DoseCommandDecision {
    APPLY_CHECK,
    APPLY_UNDO,
    NO_OP_ALREADY_TAKEN,
    NO_OP_ALREADY_UNCHECKED,
    REJECT_NON_APP_UNDO,
}

object DoseActionPolicy {
    fun check(current: DoseState?): DoseCommandDecision =
        if (current?.isTaken == true) {
            DoseCommandDecision.NO_OP_ALREADY_TAKEN
        } else {
            DoseCommandDecision.APPLY_CHECK
        }

    fun undo(
        current: DoseState?,
        source: CheckSource,
    ): DoseCommandDecision =
        when {
            source != CheckSource.APP -> {
                DoseCommandDecision.REJECT_NON_APP_UNDO
            }

            current?.isTaken != true -> {
                DoseCommandDecision.NO_OP_ALREADY_UNCHECKED
            }

            else -> {
                DoseCommandDecision.APPLY_UNDO
            }
        }
}

object DoseRows {
    fun build(
        medicines: List<Medicine>,
        states: List<DoseState>,
        logicalDay: LocalDate,
        countdowns: List<CountdownState> = emptyList(),
    ): List<DoseRow> {
        val statesById = states.associateBy { it.id }
        val countdownsBySlot =
            countdowns
                .filter { it.status == CountdownStatus.RUNNING }
                .groupBy { it.medicineId to it.slot }
                .mapValues { (_, values) -> values.maxBy(CountdownState::startedAt) }
        return medicines
            .asSequence()
            .filterNot(Medicine::archived)
            .flatMap { medicine ->
                DoseSlot.entries
                    .asSequence()
                    .filter(medicine::isEnabled)
                    .map { slot ->
                        val stateId = DoseIds.stateId(logicalDay, medicine.id, slot)
                        val state = statesById[stateId]
                        DoseRow(
                            medicineId = medicine.id,
                            medicineName = medicine.name,
                            slot = slot,
                            label = medicine.label(slot),
                            isTaken = state?.isTaken == true,
                            checkedAt = state?.checkedAt?.takeIf { state.isTaken },
                            checkedTimezone = state?.checkedTimezone?.takeIf { state.isTaken },
                            stateId = stateId,
                            countdownMinutes = medicine.countdownMinutes(slot),
                            countdown =
                                countdownsBySlot[medicine.id to slot]
                                    ?.takeUnless { state?.isTaken == true },
                        )
                    }
            }.toList()
    }

    fun progress(rows: List<DoseRow>): CompletionProgress =
        CompletionProgress(
            completed = rows.count(DoseRow::isTaken),
            total = rows.size,
        )
}

object DisplayTransform {
    fun truncate(
        value: String,
        maximum: Int,
    ): String {
        require(maximum >= 2)
        return if (value.length <= maximum) value else value.take(maximum - 1).trimEnd() + "…"
    }
}
