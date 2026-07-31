package io.github.ffelixq.medswidget.domain

data class MedicineDraft(
    val id: String? = null,
    val name: String = "",
    val afternoonEnabled: Boolean = true,
    val afternoonLabel: String = "Afternoon",
    val afternoonCountdownMinutes: Int? = null,
    val nightEnabled: Boolean = true,
    val nightLabel: String = "Night",
    val nightCountdownMinutes: Int? = null,
    val restartChangedCountdowns: Boolean = false,
)

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
                afternoonLabel =
                    if (draft.afternoonEnabled) {
                        draft.afternoonLabel.trim()
                    } else {
                        "Afternoon"
                    },
                afternoonCountdownMinutes =
                    draft.afternoonCountdownMinutes.takeIf { draft.afternoonEnabled },
                nightLabel =
                    if (draft.nightEnabled) {
                        draft.nightLabel.trim()
                    } else {
                        "Night"
                    },
                nightCountdownMinutes =
                    draft.nightCountdownMinutes.takeIf { draft.nightEnabled },
            )
        val errors = mutableMapOf<String, String>()

        if (normalized.name.isEmpty()) {
            errors["name"] = "Medicine name is required."
        } else if (normalized.name.length > MEDICINE_NAME_MAX_LENGTH) {
            errors["name"] = "Medicine name must be $MEDICINE_NAME_MAX_LENGTH characters or fewer."
        }
        if (!normalized.afternoonEnabled && !normalized.nightEnabled) {
            errors["slots"] = "Enable at least one slot."
        }
        validateLabel("afternoonLabel", normalized.afternoonLabel, errors)
        validateLabel("nightLabel", normalized.nightLabel, errors)
        validateCountdown("afternoonCountdownMinutes", normalized.afternoonCountdownMinutes, errors)
        validateCountdown("nightCountdownMinutes", normalized.nightCountdownMinutes, errors)
        return ValidationResult(normalized, errors)
    }

    private fun validateLabel(
        key: String,
        value: String,
        errors: MutableMap<String, String>,
    ) {
        if (value.isEmpty()) {
            errors[key] = "Enabled slots need a label."
        } else if (value.length > SLOT_LABEL_MAX_LENGTH) {
            errors[key] = "Slot labels must be $SLOT_LABEL_MAX_LENGTH characters or fewer."
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
