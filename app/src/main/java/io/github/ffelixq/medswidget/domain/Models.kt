package io.github.ffelixq.medswidget.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val SCHEMA_VERSION = 1
const val MEDICINE_SCHEMA_VERSION = 2
const val COUNTDOWN_SCHEMA_VERSION = 1
const val MEDICINE_NAME_MAX_LENGTH = 100
const val SLOT_LABEL_MAX_LENGTH = 60
const val DISPLAY_NAME_MAX_LENGTH = 80
const val COUNTDOWN_MIN_MINUTES = 1
const val COUNTDOWN_MAX_MINUTES = 24 * 60

enum class DoseSlot(
    val wireValue: String,
) {
    AFTERNOON("afternoon"),
    NIGHT("night"),
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
    ;

    companion object {
        fun fromWire(value: String): CheckSource? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class DoseAction(
    val wireValue: String,
) {
    CHECK("check"),
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
    val afternoonEnabled: Boolean,
    val afternoonLabel: String = "Afternoon",
    val nightEnabled: Boolean,
    val nightLabel: String = "Night",
    val afternoonCountdownMinutes: Int? = null,
    val nightCountdownMinutes: Int? = null,
    val archived: Boolean = false,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val schemaVersion: Int = MEDICINE_SCHEMA_VERSION,
) {
    fun isEnabled(slot: DoseSlot): Boolean =
        when (slot) {
            DoseSlot.AFTERNOON -> afternoonEnabled
            DoseSlot.NIGHT -> nightEnabled
        }

    fun label(slot: DoseSlot): String =
        when (slot) {
            DoseSlot.AFTERNOON -> afternoonLabel
            DoseSlot.NIGHT -> nightLabel
        }

    fun countdownMinutes(slot: DoseSlot): Int? =
        when (slot) {
            DoseSlot.AFTERNOON -> afternoonCountdownMinutes
            DoseSlot.NIGHT -> nightCountdownMinutes
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
    val undoneAt: Instant?,
    val lastActionId: String,
    val updatedAt: Instant = Instant.EPOCH,
    val schemaVersion: Int = SCHEMA_VERSION,
)

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
    val syncedAt: Instant = Instant.EPOCH,
    val schemaVersion: Int = SCHEMA_VERSION,
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
) {
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
    val countdownMinutes: Int? = null,
    val countdown: CountdownState? = null,
)

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
