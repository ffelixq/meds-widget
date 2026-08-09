package io.github.ffelixq.medswidget.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val SCHEMA_VERSION = 1
const val MEDICINE_SCHEMA_VERSION = 3
const val DOSE_SCHEMA_VERSION = 2
const val COUNTDOWN_SCHEMA_VERSION = 1
const val MEDICINE_NAME_MAX_LENGTH = 100
const val MEDICINE_NICKNAME_MAX_LENGTH = 60
const val MEDICINE_NOTES_MAX_LENGTH = 500
const val SLOT_LABEL_MAX_LENGTH = 60
const val DISPLAY_NAME_MAX_LENGTH = 80
const val COUNTDOWN_MIN_MINUTES = 1
const val COUNTDOWN_MAX_MINUTES = 24 * 60
const val REMINDER_MINUTES_MAX = 24 * 60 - 1
const val SUPPLY_MAX_UNITS = 1_000_000.0

enum class DoseSlot(
    val wireValue: String,
    val defaultLabel: String,
) {
    MORNING("morning", "Morning"),
    AFTERNOON("afternoon", "Afternoon"),
    EVENING("evening", "Evening"),
    NIGHT("night", "Night"),
    ;

    companion object {
        fun fromWire(value: String): DoseSlot? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class CheckSource(
    val wireValue: String,
) {
    APP("app"),
    APP_PREVIEW("app_preview"),
    WIDGET_2X2("widget_2x2"),
    WIDGET_4X2("widget_4x2"),
    WIDGET_4X4("widget_4x4"),
    NOTIFICATION("notification"),
    ;

    companion object {
        fun fromWire(value: String): CheckSource? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class DoseAction(
    val wireValue: String,
) {
    CHECK("check"),
    SKIP("skip"),
    UNDO("undo"),
    ;

    companion object {
        fun fromWire(value: String): DoseAction? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class CountdownAction(
    val wireValue: String,
) {
    START("start"),
    CANCEL("cancel"),
    RESTART("restart"),
    CLEAR_BY_CHECK("clear_by_check"),
    ;

    companion object {
        fun fromWire(value: String): CountdownAction? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class CountdownStatus(
    val wireValue: String,
) {
    RUNNING("running"),
    CANCELLED("cancelled"),
    CONSUMED("consumed"),
    ;

    companion object {
        fun fromWire(value: String): CountdownStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class ThemePreference(
    val wireValue: String,
) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromWire(value: String): ThemePreference = entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

enum class WidgetNameMode(
    val wireValue: String,
) {
    FULL("full"),
    NICKNAME("nickname"),
    HIDDEN("hidden"),
    ;

    companion object {
        fun fromWire(value: String?): WidgetNameMode = entries.firstOrNull { it.wireValue == value } ?: FULL
    }
}

data class AuthSession(
    val uid: String,
    val displayName: String,
    val email: String?,
    val isAnonymous: Boolean = false,
    val providers: Set<String> = emptySet(),
)

data class Medicine(
    val id: String,
    val ownerUid: String,
    val name: String,
    val nickname: String = "",
    val notes: String = "",
    val widgetNameMode: WidgetNameMode = WidgetNameMode.FULL,
    val morningEnabled: Boolean = false,
    val morningLabel: String = DoseSlot.MORNING.defaultLabel,
    val morningCountdownMinutes: Int? = null,
    val morningReminderMinutes: Int? = null,
    val afternoonEnabled: Boolean,
    val afternoonLabel: String = DoseSlot.AFTERNOON.defaultLabel,
    val afternoonCountdownMinutes: Int? = null,
    val afternoonReminderMinutes: Int? = null,
    val eveningEnabled: Boolean = false,
    val eveningLabel: String = DoseSlot.EVENING.defaultLabel,
    val eveningCountdownMinutes: Int? = null,
    val eveningReminderMinutes: Int? = null,
    val nightEnabled: Boolean,
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
    val archived: Boolean = false,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val schemaVersion: Int = MEDICINE_SCHEMA_VERSION,
) {
    fun isEnabled(slot: DoseSlot): Boolean =
        when (slot) {
            DoseSlot.MORNING -> morningEnabled
            DoseSlot.AFTERNOON -> afternoonEnabled
            DoseSlot.EVENING -> eveningEnabled
            DoseSlot.NIGHT -> nightEnabled
        }

    fun label(slot: DoseSlot): String =
        when (slot) {
            DoseSlot.MORNING -> morningLabel
            DoseSlot.AFTERNOON -> afternoonLabel
            DoseSlot.EVENING -> eveningLabel
            DoseSlot.NIGHT -> nightLabel
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

    fun enabledSlots(): List<DoseSlot> = DoseSlot.entries.filter(::isEnabled)

    fun isActiveOn(day: LocalDate): Boolean =
        !archived &&
            (startDate == null || !day.isBefore(startDate)) &&
            (endDate == null || !day.isAfter(endDate))

    fun widgetDisplayName(): String =
        when (widgetNameMode) {
            WidgetNameMode.FULL -> name
            WidgetNameMode.NICKNAME -> nickname.ifBlank { "Medicine" }
            WidgetNameMode.HIDDEN -> "Medicine"
        }
}

data class UserSettings(
    val resetMinutesAfterMidnight: Int = 0,
    val timezoneId: String = ZoneId.systemDefault().id,
    val displayName: String = "",
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val updatedAt: Instant = Instant.EPOCH,
    val schemaVersion: Int = SCHEMA_VERSION,
)

data class DoseState(
    val id: String,
    val ownerUid: String,
    val logicalDay: LocalDate,
    val medicineId: String,
    val slot: DoseSlot,
    val labelSnapshot: String,
    val medicineNameSnapshot: String,
    val isTaken: Boolean,
    val checkedAt: Instant?,
    val checkedTimezone: String?,
    val checkedSource: CheckSource?,
    val skippedAt: Instant? = null,
    val skipReason: String? = null,
    val undoneAt: Instant?,
    val lastActionId: String,
    val updatedAt: Instant = Instant.EPOCH,
    val schemaVersion: Int = DOSE_SCHEMA_VERSION,
) {
    val isSkipped: Boolean get() = !isTaken && skippedAt != null
    val isPending: Boolean get() = !isTaken && skippedAt == null
}

data class DoseEvent(
    val eventId: String,
    val ownerUid: String,
    val action: DoseAction,
    val logicalDay: LocalDate,
    val medicineId: String,
    val medicineNameSnapshot: String,
    val slot: DoseSlot,
    val labelSnapshot: String,
    val occurredAt: Instant,
    val timezoneId: String,
    val source: CheckSource,
    val relatedStateId: String,
    val previousActionId: String? = null,
    val skipReason: String? = null,
    val syncedAt: Instant = Instant.EPOCH,
    val schemaVersion: Int = DOSE_SCHEMA_VERSION,
)

data class CountdownState(
    val id: String,
    val ownerUid: String,
    val logicalDay: LocalDate,
    val medicineId: String,
    val slot: DoseSlot,
    val durationMinutes: Int,
    val startedAt: Instant,
    val targetAt: Instant,
    val startedTimezone: String,
    val startedSource: CheckSource,
    val status: CountdownStatus,
    val cancelledAt: Instant?,
    val completedAt: Instant?,
    val lastActionId: String,
    val updatedAt: Instant = Instant.EPOCH,
    val schemaVersion: Int = COUNTDOWN_SCHEMA_VERSION,
)

data class CountdownEvent(
    val eventId: String,
    val ownerUid: String,
    val action: CountdownAction,
    val logicalDay: LocalDate,
    val medicineId: String,
    val slot: DoseSlot,
    val durationMinutes: Int,
    val occurredAt: Instant,
    val timezoneId: String,
    val source: CheckSource,
    val relatedStateId: String,
    val previousActionId: String?,
    val syncedAt: Instant = Instant.EPOCH,
    val schemaVersion: Int = COUNTDOWN_SCHEMA_VERSION,
)

data class DataEnvelope<T>(
    val value: T,
    val fromCache: Boolean = false,
    val hasPendingWrites: Boolean = false,
    val errorMessage: String? = null,
)

data class CompletionProgress(
    val completed: Int,
    val total: Int,
    val skipped: Int = 0,
) {
    val pending: Int get() = (total - completed - skipped).coerceAtLeast(0)
    val display: String get() = "$completed of $total completed"
    val compactDisplay: String get() = "$completed/$total"
}

data class DoseRow(
    val medicineId: String,
    val medicineName: String,
    val slot: DoseSlot,
    val label: String,
    val isTaken: Boolean,
    val checkedAt: Instant?,
    val checkedTimezone: String? = null,
    val stateId: String,
    val isSkipped: Boolean = false,
    val skippedAt: Instant? = null,
    val skipReason: String? = null,
    val countdownMinutes: Int? = null,
    val countdown: CountdownState? = null,
)

data class AdherenceSummary(
    val scheduled: Int,
    val taken: Int,
    val skipped: Int,
    val missed: Int,
) {
    val adherencePercent: Double = if (scheduled == 0) 100.0 else taken * 100.0 / scheduled
}

sealed interface ContentState<out T> {
    data object Loading : ContentState<Nothing>

    data object SignedOut : ContentState<Nothing>

    data object Empty : ContentState<Nothing>

    data class Content<T>(
        val value: T,
        val isCached: Boolean = false,
        val isSyncPending: Boolean = false,
    ) : ContentState<T>

    data class Error(
        val message: String,
        val cachedValueAvailable: Boolean = false,
    ) : ContentState<Nothing>
}
