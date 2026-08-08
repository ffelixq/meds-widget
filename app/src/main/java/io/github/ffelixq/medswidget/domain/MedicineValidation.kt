package io.github.ffelixq.medswidget.domain

import java.time.LocalDate

data class MedicineDraft(
    val id: String? = null,
    val name: String = "",
    val nickname: String = "",
    val notes: String = "",
    val widgetNameMode: WidgetNameMode = WidgetNameMode.FULL,
    val morningEnabled: Boolean = false,
    val morningLabel: String = DoseSlot.MORNING.defaultLabel,
    val morningCountdownMinutes: Int? = null,
    val morningReminderMinutes: Int? = null,
    val afternoonEnabled: Boolean = true,
    val afternoonLabel: String = DoseSlot.AFTERNOON.defaultLabel,
    val afternoonCountdownMinutes: Int? = null,
    val afternoonReminderMinutes: Int? = null,
    val eveningEnabled: Boolean = false,
    val eveningLabel: String = DoseSlot.EVENING.defaultLabel,
    val eveningCountdownMinutes: Int? = null,
    val eveningReminderMinutes: Int? = null,
    val nightEnabled: Boolean = true,
    val nightLabel: String = DoseSlot.NIGHT.defaultLabel,
    val nightCountdownMinutes: Int? = null,
    val nightReminderMinutes: Int? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val supplyEnabled: Boolean = false,
    val supplyInitialUnits: Double? = null,
    val unitsPerDose: Double = 1.0,
    val lowSupplyThreshold: Double? = null,
    val supplyUnitName: String = "units",
    val restartChangedCountdowns: Boolean = false,
) {
    fun isSlotEnabled(slot: DoseSlot): Boolean =
        when (slot) {
            DoseSlot.MORNING -> morningEnabled
            DoseSlot.AFTERNOON -> afternoonEnabled
            DoseSlot.EVENING -> eveningEnabled
            DoseSlot.NIGHT -> nightEnabled
        }

    fun countdownMinutes(slot: DoseSlot): Int? =
        when (slot) {
            DoseSlot.MORNING -> morningCountdownMinutes
            DoseSlot.AFTERNOON -> afternoonCountdownMinutes
            DoseSlot.EVENING -> eveningCountdownMinutes
            DoseSlot.NIGHT -> nightCountdownMinutes
        }
}

data class ValidationResult(
    val normalized: MedicineDraft,
    val errors: Map<String, String>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object MedicineValidator {
    fun validate(draft: MedicineDraft): ValidationResult {
        val normalized =
            draft.copy(
                name = draft.name.trim(),
                nickname = draft.nickname.trim(),
                notes = draft.notes.trim(),
                morningLabel = normalizeLabel(draft.morningEnabled, draft.morningLabel, DoseSlot.MORNING),
                morningCountdownMinutes = draft.morningCountdownMinutes.takeIf { draft.morningEnabled },
                morningReminderMinutes = draft.morningReminderMinutes.takeIf { draft.morningEnabled },
                afternoonLabel = normalizeLabel(draft.afternoonEnabled, draft.afternoonLabel, DoseSlot.AFTERNOON),
                afternoonCountdownMinutes = draft.afternoonCountdownMinutes.takeIf { draft.afternoonEnabled },
                afternoonReminderMinutes = draft.afternoonReminderMinutes.takeIf { draft.afternoonEnabled },
                eveningLabel = normalizeLabel(draft.eveningEnabled, draft.eveningLabel, DoseSlot.EVENING),
                eveningCountdownMinutes = draft.eveningCountdownMinutes.takeIf { draft.eveningEnabled },
                eveningReminderMinutes = draft.eveningReminderMinutes.takeIf { draft.eveningEnabled },
                nightLabel = normalizeLabel(draft.nightEnabled, draft.nightLabel, DoseSlot.NIGHT),
                nightCountdownMinutes = draft.nightCountdownMinutes.takeIf { draft.nightEnabled },
                nightReminderMinutes = draft.nightReminderMinutes.takeIf { draft.nightEnabled },
                supplyUnitName = draft.supplyUnitName.trim().ifBlank { "units" },
                supplyInitialUnits = draft.supplyInitialUnits.takeIf { draft.supplyEnabled },
                lowSupplyThreshold = draft.lowSupplyThreshold.takeIf { draft.supplyEnabled },
            )
        val errors = mutableMapOf<String, String>()

        if (normalized.name.isEmpty()) {
            errors["name"] = "Medicine name is required."
        } else if (normalized.name.length > MEDICINE_NAME_MAX_LENGTH) {
            errors["name"] = "Medicine name must be $MEDICINE_NAME_MAX_LENGTH characters or fewer."
        }
        if (normalized.nickname.length > MEDICINE_NICKNAME_MAX_LENGTH) {
            errors["nickname"] = "Nickname must be $MEDICINE_NICKNAME_MAX_LENGTH characters or fewer."
        }
        if (normalized.notes.length > MEDICINE_NOTES_MAX_LENGTH) {
            errors["notes"] = "Notes must be $MEDICINE_NOTES_MAX_LENGTH characters or fewer."
        }
        if (normalized.widgetNameMode == WidgetNameMode.NICKNAME && normalized.nickname.isBlank()) {
            errors["nickname"] = "Add a nickname before using it on widgets."
        }
        if (DoseSlot.entries.none(normalized::isSlotEnabled)) {
            errors["slots"] = "Enable at least one slot."
        }
        DoseSlot.entries.forEach { slot ->
            validateSlot(slot, normalized, errors)
        }
        validateCourse(normalized.startDate, normalized.endDate, errors)
        validateSupply(normalized, errors)
        return ValidationResult(normalized, errors)
    }

    private fun normalizeLabel(
        enabled: Boolean,
        value: String,
        slot: DoseSlot,
    ): String = if (enabled) value.trim() else slot.defaultLabel

    private fun validateSlot(
        slot: DoseSlot,
        draft: MedicineDraft,
        errors: MutableMap<String, String>,
    ) {
        if (!draft.isSlotEnabled(slot)) return
        val prefix = slot.wireValue
        val label =
            when (slot) {
                DoseSlot.MORNING -> draft.morningLabel
                DoseSlot.AFTERNOON -> draft.afternoonLabel
                DoseSlot.EVENING -> draft.eveningLabel
                DoseSlot.NIGHT -> draft.nightLabel
            }
        if (label.isEmpty()) {
            errors["${prefix}Label"] = "Enabled slots need a label."
        } else if (label.length > SLOT_LABEL_MAX_LENGTH) {
            errors["${prefix}Label"] = "Slot labels must be $SLOT_LABEL_MAX_LENGTH characters or fewer."
        }
        validateCountdown("${prefix}CountdownMinutes", draft.countdownMinutes(slot), errors)
        val reminder =
            when (slot) {
                DoseSlot.MORNING -> draft.morningReminderMinutes
                DoseSlot.AFTERNOON -> draft.afternoonReminderMinutes
                DoseSlot.EVENING -> draft.eveningReminderMinutes
                DoseSlot.NIGHT -> draft.nightReminderMinutes
            }
        if (reminder != null && reminder !in 0..REMINDER_MINUTES_MAX) {
            errors["${prefix}ReminderMinutes"] = "Reminder time must be within the day."
        }
    }

    private fun validateCourse(
        startDate: LocalDate?,
        endDate: LocalDate?,
        errors: MutableMap<String, String>,
    ) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            errors["course"] = "End date cannot be before the start date."
        }
    }

    private fun validateSupply(
        draft: MedicineDraft,
        errors: MutableMap<String, String>,
    ) {
        if (!draft.supplyEnabled) return
        val initial = draft.supplyInitialUnits
        if (initial == null || initial <= 0.0 || initial > SUPPLY_MAX_UNITS) {
            errors["supplyInitialUnits"] = "Enter a starting supply greater than 0."
        }
        if (!draft.unitsPerDose.isFinite() || draft.unitsPerDose <= 0.0 || draft.unitsPerDose > SUPPLY_MAX_UNITS) {
            errors["unitsPerDose"] = "Units per dose must be greater than 0."
        }
        val threshold = draft.lowSupplyThreshold
        if (threshold != null && (!threshold.isFinite() || threshold < 0.0 || threshold > SUPPLY_MAX_UNITS)) {
            errors["lowSupplyThreshold"] = "Low-supply threshold must be 0 or greater."
        }
        if (draft.supplyUnitName.length > 30) {
            errors["supplyUnitName"] = "Supply unit name must be 30 characters or fewer."
        }
    }

    private fun validateCountdown(
        key: String,
        value: Int?,
        errors: MutableMap<String, String>,
    ) {
        if (value != null && value !in COUNTDOWN_MIN_MINUTES..COUNTDOWN_MAX_MINUTES) {
            errors[key] = "Countdown must be between 1 minute and 24 hours."
        }
    }
}
