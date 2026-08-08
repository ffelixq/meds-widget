package io.github.ffelixq.medswidget.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownState
import io.github.ffelixq.medswidget.domain.CountdownStatus
import io.github.ffelixq.medswidget.domain.DOSE_SCHEMA_VERSION
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseEvent
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.DoseState
import io.github.ffelixq.medswidget.domain.MEDICINE_SCHEMA_VERSION
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.SCHEMA_VERSION
import io.github.ffelixq.medswidget.domain.ThemePreference
import io.github.ffelixq.medswidget.domain.UserSettings
import io.github.ffelixq.medswidget.domain.WidgetNameMode
import java.time.Instant
import java.time.LocalDate

internal fun DocumentSnapshot.toMedicine(): Medicine? {
    val slotAfternoon = getBoolean("afternoonEnabled") ?: return null
    val slotNight = getBoolean("nightEnabled") ?: return null
    return Medicine(
        id = getString("id") ?: id,
        ownerUid = getString("ownerUid") ?: return null,
        name = getString("name") ?: return null,
        nickname = getString("nickname").orEmpty(),
        notes = getString("notes").orEmpty(),
        widgetNameMode = WidgetNameMode.fromWire(getString("widgetNameMode")),
        morningEnabled = getBoolean("morningEnabled") ?: false,
        morningLabel = getString("morningLabel") ?: DoseSlot.MORNING.defaultLabel,
        morningCountdownMinutes = getLong("morningCountdownMinutes")?.toInt(),
        morningReminderMinutes = getLong("morningReminderMinutes")?.toInt(),
        afternoonEnabled = slotAfternoon,
        afternoonLabel = getString("afternoonLabel") ?: DoseSlot.AFTERNOON.defaultLabel,
        afternoonCountdownMinutes = getLong("afternoonCountdownMinutes")?.toInt(),
        afternoonReminderMinutes = getLong("afternoonReminderMinutes")?.toInt(),
        eveningEnabled = getBoolean("eveningEnabled") ?: false,
        eveningLabel = getString("eveningLabel") ?: DoseSlot.EVENING.defaultLabel,
        eveningCountdownMinutes = getLong("eveningCountdownMinutes")?.toInt(),
        eveningReminderMinutes = getLong("eveningReminderMinutes")?.toInt(),
        nightEnabled = slotNight,
        nightLabel = getString("nightLabel") ?: DoseSlot.NIGHT.defaultLabel,
        nightCountdownMinutes = getLong("nightCountdownMinutes")?.toInt(),
        nightReminderMinutes = getLong("nightReminderMinutes")?.toInt(),
        startDate = getString("startDate")?.let(::parseLocalDate),
        endDate = getString("endDate")?.let(::parseLocalDate),
        supplyEnabled = getBoolean("supplyEnabled") ?: false,
        supplyInitialUnits = getDouble("supplyInitialUnits"),
        unitsPerDose = getDouble("unitsPerDose") ?: 1.0,
        lowSupplyThreshold = getDouble("lowSupplyThreshold"),
        supplyUnitName = getString("supplyUnitName") ?: "units",
        archived = getBoolean("archived") ?: false,
        createdAt = getTimestamp("createdAt").toInstantOrEpoch(),
        updatedAt = getTimestamp("updatedAt").toInstantOrEpoch(),
        schemaVersion = getLong("schemaVersion")?.toInt() ?: MEDICINE_SCHEMA_VERSION,
    )
}

internal fun DocumentSnapshot.toCountdownState(): CountdownState? =
    CountdownState(
        id = id,
        ownerUid = getString("ownerUid") ?: return null,
        logicalDay = runCatching { LocalDate.parse(getString("logicalDay")) }.getOrNull() ?: return null,
        medicineId = getString("medicineId") ?: return null,
        slot = DoseSlot.fromWire(getString("slot").orEmpty()) ?: return null,
        durationMinutes = getLong("durationMinutes")?.toInt() ?: return null,
        startedAt = getTimestamp("startedAt")?.toDate()?.toInstant() ?: return null,
        targetAt = getTimestamp("targetAt")?.toDate()?.toInstant() ?: return null,
        startedTimezone = getString("startedTimezone") ?: return null,
        startedSource = CheckSource.fromWire(getString("startedSource").orEmpty()) ?: return null,
        status = CountdownStatus.fromWire(getString("status").orEmpty()) ?: return null,
        cancelledAt = getTimestamp("cancelledAt")?.toDate()?.toInstant(),
        completedAt = getTimestamp("completedAt")?.toDate()?.toInstant(),
        lastActionId = getString("lastActionId") ?: return null,
        updatedAt = getTimestamp("updatedAt").toInstantOrEpoch(),
        schemaVersion = getLong("schemaVersion")?.toInt() ?: 1,
    )

internal fun DocumentSnapshot.toDoseState(): DoseState? =
    DoseState(
        id = id,
        ownerUid = getString("ownerUid") ?: return null,
        logicalDay = runCatching { LocalDate.parse(getString("logicalDay")) }.getOrNull() ?: return null,
        medicineId = getString("medicineId") ?: return null,
        slot = DoseSlot.fromWire(getString("slot").orEmpty()) ?: return null,
        labelSnapshot = getString("labelSnapshot") ?: return null,
        medicineNameSnapshot = getString("medicineNameSnapshot") ?: return null,
        isTaken = getBoolean("isTaken") ?: false,
        checkedAt = getTimestamp("checkedAt")?.toDate()?.toInstant(),
        checkedTimezone = getString("checkedTimezone"),
        checkedSource = getString("checkedSource")?.let(CheckSource::fromWire),
        skippedAt = getTimestamp("skippedAt")?.toDate()?.toInstant(),
        skipReason = getString("skipReason"),
        undoneAt = getTimestamp("undoneAt")?.toDate()?.toInstant(),
        lastActionId = getString("lastActionId").orEmpty(),
        updatedAt = getTimestamp("updatedAt").toInstantOrEpoch(),
        schemaVersion = getLong("schemaVersion")?.toInt() ?: DOSE_SCHEMA_VERSION,
    )

internal fun DocumentSnapshot.toDoseEvent(): DoseEvent? =
    DoseEvent(
        eventId = getString("eventId") ?: id,
        ownerUid = getString("ownerUid") ?: return null,
        action = DoseAction.fromWire(getString("action").orEmpty()) ?: return null,
        logicalDay = runCatching { LocalDate.parse(getString("logicalDay")) }.getOrNull() ?: return null,
        medicineId = getString("medicineId") ?: return null,
        medicineNameSnapshot = getString("medicineNameSnapshot") ?: return null,
        slot = DoseSlot.fromWire(getString("slot").orEmpty()) ?: return null,
        labelSnapshot = getString("labelSnapshot") ?: return null,
        occurredAt = getTimestamp("occurredAt")?.toDate()?.toInstant() ?: return null,
        timezoneId = getString("timezoneId") ?: return null,
        source = CheckSource.fromWire(getString("source").orEmpty()) ?: return null,
        relatedStateId = getString("relatedStateId") ?: return null,
        previousActionId = getString("previousActionId"),
        skipReason = getString("skipReason"),
        syncedAt = getTimestamp("syncedAt").toInstantOrEpoch(),
        schemaVersion = getLong("schemaVersion")?.toInt() ?: DOSE_SCHEMA_VERSION,
    )

internal fun DocumentSnapshot.toSettings(default: UserSettings): UserSettings =
    UserSettings(
        resetMinutesAfterMidnight = getLong("resetMinutesAfterMidnight")?.toInt() ?: default.resetMinutesAfterMidnight,
        timezoneId = getString("timezoneId") ?: default.timezoneId,
        displayName = getString("displayName") ?: default.displayName,
        themePreference = ThemePreference.fromWire(getString("themePreference").orEmpty()),
        updatedAt = getTimestamp("updatedAt").toInstantOrEpoch(),
        schemaVersion = getLong("schemaVersion")?.toInt() ?: SCHEMA_VERSION,
    )

private fun parseLocalDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

private fun Timestamp?.toInstantOrEpoch(): Instant = this?.toDate()?.toInstant() ?: Instant.EPOCH
