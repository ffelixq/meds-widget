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

    fun reminderMinutes(slot: DoseSlot): Int? =
        when (slot) {
            DoseSlot.MORNING -> morningReminderMinutes
            DoseSlot.AFTERNOON -> afternoonReminderMinutes
            DoseSlot.EVENING -> eveningReminderMinutes
            DoseSlot.NIGHT -> nightReminderMinutes
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
        val normalized = normalize(draft)
        val errors = mutableMapOf<String, String>()
        validateIdentity(normalized, errors)
        if (DoseSlot.entries.none(normalized::isSlotEnabled)) {
            errors["slots"] = "Enable at least one slot."
        }
        DoseSlot.entries.forEach { slot -> validateSlot(slot, normalized, errors) }
        validateCourse(normalized.startDate, normalized.endDate, errors)
        validateSupply(normalized, errors)
        return ValidationResult(normalized, errors)
    }

    private fun normalize(draft: MedicineDraft): MedicineDraft =
        draft.copy(
            name = draft.name.trim(),
            nickname = draft.nickname.trim(),
            notes = draft.notes.trim(),
            morningLabel =
                normalizeLabel(draft.morningEnabled, draft.morningLabel, DoseSlot.MORNING),
            morningCountdownMinutes = draft.morningCountdownMinutes.takeIf { draft.morningEnabled },
            morningReminderMinutes = draft.morningReminderMinutes.takeIf { draft.morningEnabled },
            afternoonLabel =
                normalizeLabel(draft.afternoonEnabled, draft.afternoonLabel, DoseSlot.AFTERNOON),
            afternoonCountdownMinutes =
                draft.afternoonCountdownMinutes.takeIf { draft.afternoonEnabled },
            afternoonReminderMinutes = draft.afternoonReminderMinutes.takeIf { draft.afternoonEnabled },
            eveningLabel =
                normalizeLabel(draft.eveningEnabled, draft.eveningLabel, DoseSlot.EVENING),
            eveningCountdownMinutes = draft.eveningCountdownMinutes.takeIf { draft.eveningEnabled },
            eveningReminderMinutes = draft.eveningReminderMinutes.takeIf { draft.eveningEnabled },
            nightLabel = normalizeLabel(draft.nightEnabled, draft.nightLabel, DoseSlot.NIGHT),
            nightCountdownMinutes = draft.nightCountdownMinutes.takeIf { draft.nightEnabled },
            nightReminderMinutes = draft.nightReminderMinutes.takeIf { draft.nightEnabled },
            supplyUnitName = draft.supplyUnitName.trim().ifBlank { "units" },
            supplyInitialUnits = draft.supplyInitialUnits.takeIf { draft.supplyEnabled },
            lowSupplyThreshold = draft.lowSupplyThreshold.takeIf { draft.supplyEnabled },
        )

    private fun validateIdentity(
        draft: MedicineDraft,
        errors: MutableMap<String, String>,
    ) {
        when {
            draft.name.isEmpty() -> {
                errors["name"] = "Medicine name is required."
            }

            draft.name.length > MEDICINE_NAME_MAX_LENGTH -> {
                errors["name"] =
                    "Medicine name must be $MEDICINE_NAME_MAX_LENGTH characters or fewer."
            }
        }
        if (draft.nickname.length > MEDICINE_NICKNAME_MAX_LENGTH) {
            errors["nickname"] =
                "Nickname must be $MEDICINE_NICKNAME_MAX_LENGTH characters or fewer."
        }
        if (draft.notes.length > MEDICINE_NOTES_MAX_LENGTH) {
            errors["notes"] = "Notes must be $MEDICINE_NOTES_MAX_LENGTH characters or fewer."
        }
        if (draft.widgetNameMode == WidgetNameMode.NICKNAME && draft.nickname.isBlank()) {
            errors["nickname"] = "Add a nickname before using it on widgets."
        }
    }

    private fun validateSlot(
        slot: DoseSlot,
        draft: MedicineDraft,
        errors: MutableMap<String, String>,
    ) {
        if (!draft.isSlotEnabled(slot)) return
        val prefix = slot.wireValue
        val label = labelFor(slot, draft)
        when {
            label.isEmpty() -> {
                errors["${prefix}Label"] = "Enabled slots need a label."
            }

            label.length > SLOT_LABEL_MAX_LENGTH -> {
                errors["${prefix}Label"] =
                    "Slot labels must be $SLOT_LABEL_MAX_LENGTH characters or fewer."
            }
        }
        validateCountdown("${prefix}CountdownMinutes", draft.countdownMinutes(slot), errors)
        draft.reminderMinutes(slot)?.let { reminder ->
            if (reminder !in 0..REMINDER_MINUTES_MAX) {
                errors["${prefix}ReminderMinutes"] = "Reminder time must be within the day."
            }
        }
    }

    private fun labelFor(
        slot: DoseSlot,
        draft: MedicineDraft,
    ): String =
        when (slot) {
            DoseSlot.MORNING -> draft.morningLabel
            DoseSlot.AFTERNOON -> draft.afternoonLabel
            DoseSlot.EVENING -> draft.eveningLabel
            DoseSlot.NIGHT -> draft.nightLabel
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
        if (invalidPositiveNumber(draft.supplyInitialUnits)) {
            errors["supplyInitialUnits"] = "Enter a starting supply greater than 0."
        }
        if (invalidPositiveNumber(draft.unitsPerDose)) {
            errors["unitsPerDose"] = "Units per dose must be greater than 0."
        }
        if (invalidNonNegativeNumber(draft.lowSupplyThreshold)) {
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

private fun normalizeLabel(
    enabled: Boolean,
    value: String,
    slot: DoseSlot,
): String = if (enabled) value.trim() else slot.defaultLabel

private fun invalidPositiveNumber(value: Double?): Boolean =
    value == null || !value.isFinite() || value <= 0.0 || value > SUPPLY_MAX_UNITS

private fun invalidNonNegativeNumber(value: Double?): Boolean = value != null && (!value.isFinite() || value < 0.0 || value > SUPPLY_MAX_UNITS)
