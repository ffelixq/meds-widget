package io.github.ffelixq.medswidget.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseEvent
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.DoseState
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.SCHEMA_VERSION
import io.github.ffelixq.medswidget.domain.ThemePreference
import io.github.ffelixq.medswidget.domain.UserSettings
import java.time.Instant
import java.time.LocalDate

internal fun DocumentSnapshot.toMedicine(): Medicine? {
    val slotAfternoon = getBoolean("afternoonEnabled") ?: return null
    val slotNight = getBoolean("nightEnabled") ?: return null
    return Medicine(
        id = getString("id") ?: id,
        ownerUid = getString("ownerUid") ?: return null,
        name = getString("name") ?: return null,
        afternoonEnabled = slotAfternoon,
        afternoonLabel = getString("afternoonLabel") ?: "Afternoon",
        nightEnabled = slotNight,
        nightLabel = getString("nightLabel") ?: "Night",
        archived = getBoolean("archived") ?: false,
        createdAt = getTimestamp("createdAt").toInstantOrEpoch(),
        updatedAt = getTimestamp("updatedAt").toInstantOrEpoch(),
        schemaVersion = getLong("schemaVersion")?.toInt() ?: SCHEMA_VERSION,
    )
}

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
        undoneAt = getTimestamp("undoneAt")?.toDate()?.toInstant(),
        lastActionId = getString("lastActionId").orEmpty(),
        updatedAt = getTimestamp("updatedAt").toInstantOrEpoch(),
        schemaVersion = getLong("schemaVersion")?.toInt() ?: SCHEMA_VERSION,
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
        syncedAt = getTimestamp("syncedAt").toInstantOrEpoch(),
        schemaVersion = getLong("schemaVersion")?.toInt() ?: SCHEMA_VERSION,
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

private fun Timestamp?.toInstantOrEpoch(): Instant = this?.toDate()?.toInstant() ?: Instant.EPOCH
