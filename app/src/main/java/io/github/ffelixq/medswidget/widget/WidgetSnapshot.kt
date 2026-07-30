package io.github.ffelixq.medswidget.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ffelixq.medswidget.data.DoseWriteOutcome
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.Medicine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

data class WidgetMedicine(
    val id: String,
    val name: String,
    val afternoonEnabled: Boolean,
    val afternoonLabel: String,
    val nightEnabled: Boolean,
    val nightLabel: String,
)

data class WidgetDoseRow(
    val medicineId: String,
    val medicineName: String,
    val slot: DoseSlot,
    val label: String,
    val isTaken: Boolean,
    val checkedAt: Instant?,
    val checkedTimezone: String? = null,
) {
    fun toDomain(logicalDay: LocalDate): DoseRow =
        DoseRow(
            medicineId = medicineId,
            medicineName = medicineName,
            slot = slot,
            label = label,
            isTaken = isTaken,
            checkedAt = checkedAt,
            checkedTimezone = checkedTimezone,
            stateId = "${logicalDay}_${medicineId}_${slot.wireValue}",
        )
}

data class WidgetPendingAction(
    val actionId: String,
    val medicineId: String,
    val slot: DoseSlot,
    val createdAt: Instant,
    val submitted: Boolean = false,
)

data class WidgetSnapshot(
    val ownerUid: String? = null,
    val signedIn: Boolean = false,
    val isLoading: Boolean = false,
    val logicalDay: LocalDate = LocalDate.now(),
    val medicines: List<WidgetMedicine> = emptyList(),
    val rows: List<WidgetDoseRow> = emptyList(),
    val pendingActions: List<WidgetPendingAction> = emptyList(),
    val fromCache: Boolean = false,
    val hasPendingWrites: Boolean = false,
    val repositoryHasPendingWrites: Boolean = false,
    val errorMessage: String? = null,
) {
    fun medicine(id: String): WidgetMedicine? = medicines.firstOrNull { it.id == id }

    fun rowsForMedicine(id: String): List<WidgetDoseRow> = rows.filter { it.medicineId == id }

    fun compactStatus(): String? =
        when {
            isLoading -> "Loading"
            hasPendingWrites -> "Syncing"
            errorMessage != null || fromCache -> "Cached"
            else -> null
        }
}

private val Context.widgetSnapshotDataStore by preferencesDataStore("widget_snapshot")
private val SNAPSHOT = stringPreferencesKey("snapshot_json")
private val SIGNED_IN = booleanPreferencesKey("signed_in")

class WidgetSnapshotStore(
    private val context: Context,
) {
    val flow: Flow<WidgetSnapshot> =
        context.widgetSnapshotDataStore.data.map { preferences ->
            preferences[SNAPSHOT]?.let(WidgetSnapshotCodec::decode)
                ?: WidgetSnapshot(isLoading = true)
        }

    suspend fun read(): WidgetSnapshot = flow.first()

    suspend fun write(snapshot: WidgetSnapshot) {
        context.widgetSnapshotDataStore.edit { preferences ->
            preferences.store(snapshot)
        }
    }

    /**
     * Merges repository state with widget actions that still await Firestore acknowledgement.
     * The DataStore transaction prevents a listener write from resurrecting an action that a
     * concurrent write callback has already resolved.
     */
    suspend fun writeRepositorySnapshot(
        snapshot: WidgetSnapshot,
        resolvePendingActions: Boolean = false,
    ) {
        context.widgetSnapshotDataStore.edit { preferences ->
            val current = preferences.snapshot()
            val repositoryPending = snapshot.repositoryHasPendingWrites || snapshot.hasPendingWrites
            val sameAccountAndDay =
                current.ownerUid == snapshot.ownerUid &&
                    current.logicalDay == snapshot.logicalDay
            val pendingActions =
                current.pendingActions
                    .takeIf { sameAccountAndDay }
                    .orEmpty()
                    .filter { action ->
                        !resolvePendingActions ||
                            snapshot.fromCache ||
                            repositoryPending ||
                            !action.submitted
                    }
            val pendingKeys = pendingActions.map { it.medicineId to it.slot }.toSet()
            val mergedRows =
                snapshot.rows.map { repositoryRow ->
                    if ((repositoryRow.medicineId to repositoryRow.slot) in pendingKeys) {
                        current.rows.firstOrNull {
                            it.medicineId == repositoryRow.medicineId &&
                                it.slot == repositoryRow.slot
                        } ?: repositoryRow
                    } else {
                        repositoryRow
                    }
                }
            preferences.store(
                snapshot.copy(
                    rows = mergedRows,
                    pendingActions = pendingActions,
                    hasPendingWrites = repositoryPending || pendingActions.isNotEmpty(),
                    repositoryHasPendingWrites = repositoryPending,
                ),
            )
        }
    }

    /**
     * Removes cached account content before a widget render whenever Firebase Auth's live UID
     * does not match it. This check is synchronous with rendering and does not rely on the
     * application-scope auth collector winning a startup race.
     */
    suspend fun secureForSession(
        activeUid: String?,
        logicalDay: LocalDate,
    ): Boolean {
        var changed = false
        context.widgetSnapshotDataStore.edit { preferences ->
            val current = preferences.snapshot()
            val replacement =
                when {
                    activeUid == null && (current.signedIn || current.ownerUid != null) -> {
                        WidgetSnapshot(logicalDay = logicalDay)
                    }

                    activeUid != null &&
                        (!current.signedIn || current.ownerUid != activeUid) -> {
                        WidgetSnapshot(
                            ownerUid = activeUid,
                            signedIn = true,
                            isLoading = true,
                            logicalDay = logicalDay,
                        )
                    }

                    else -> {
                        null
                    }
                }
            if (replacement != null) {
                preferences.store(replacement)
                changed = true
            }
        }
        return changed
    }

    suspend fun markTakenOptimistically(
        expectedUid: String,
        medicineId: String,
        slot: DoseSlot,
        checkedAt: Instant,
        checkedTimezone: String,
        actionId: String,
    ): Boolean {
        var changed = false
        context.widgetSnapshotDataStore.edit { preferences ->
            val current = preferences.snapshot()
            val matching = current.rows.firstOrNull { it.medicineId == medicineId && it.slot == slot }
            if (!current.canOptimisticallyCheck(expectedUid, matching)) {
                return@edit
            }
            val updated =
                current.copy(
                    rows =
                        current.rows.map {
                            if (it.medicineId == medicineId && it.slot == slot) {
                                it.copy(
                                    isTaken = true,
                                    checkedAt = checkedAt,
                                    checkedTimezone = checkedTimezone,
                                )
                            } else {
                                it
                            }
                        },
                    pendingActions =
                        current.pendingActions
                            .filterNot { it.medicineId == medicineId && it.slot == slot } +
                            WidgetPendingAction(
                                actionId = actionId,
                                medicineId = medicineId,
                                slot = slot,
                                createdAt = checkedAt,
                            ),
                    hasPendingWrites = true,
                    errorMessage = null,
                )
            preferences.store(updated)
            changed = true
        }
        return changed
    }

    suspend fun markActionSubmitted(actionId: String): Boolean {
        var changed = false
        context.widgetSnapshotDataStore.edit { preferences ->
            val current = preferences.snapshot()
            if (current.pendingActions.none { it.actionId == actionId }) return@edit
            preferences.store(
                current.copy(
                    pendingActions =
                        current.pendingActions.map {
                            if (it.actionId == actionId) it.copy(submitted = true) else it
                        },
                ),
            )
            changed = true
        }
        return changed
    }

    suspend fun expireUnsubmittedActions(createdBefore: Instant): Boolean {
        var changed = false
        context.widgetSnapshotDataStore.edit { preferences ->
            val current = preferences.snapshot()
            val expired =
                current.pendingActions.filter { action ->
                    !action.submitted && !action.createdAt.isAfter(createdBefore)
                }
            if (expired.isEmpty()) return@edit
            val expiredKeys = expired.map { it.medicineId to it.slot }.toSet()
            val remaining = current.pendingActions - expired.toSet()
            preferences.store(
                current.copy(
                    rows =
                        current.rows.map { row ->
                            if ((row.medicineId to row.slot) in expiredKeys) {
                                row.copy(
                                    isTaken = false,
                                    checkedAt = null,
                                    checkedTimezone = null,
                                )
                            } else {
                                row
                            }
                        },
                    pendingActions = remaining,
                    hasPendingWrites =
                        current.repositoryHasPendingWrites || remaining.isNotEmpty(),
                    errorMessage = "An interrupted widget check was not saved. Try again.",
                ),
            )
            changed = true
        }
        return changed
    }

    suspend fun resolveWriteOutcome(outcome: DoseWriteOutcome): Boolean {
        var changed = false
        context.widgetSnapshotDataStore.edit { preferences ->
            val current = preferences.snapshot()
            if (current.ownerUid != outcome.ownerUid || !current.signedIn) return@edit
            val pending = current.pendingActions.firstOrNull { it.actionId == outcome.actionId } ?: return@edit
            val remaining = current.pendingActions.filterNot { it.actionId == outcome.actionId }
            val resolvedRows =
                if (!outcome.successful && outcome.action == DoseAction.CHECK) {
                    current.rows.map { row ->
                        if (row.medicineId == pending.medicineId && row.slot == pending.slot) {
                            row.copy(
                                isTaken = false,
                                checkedAt = null,
                                checkedTimezone = null,
                            )
                        } else {
                            row
                        }
                    }
                } else {
                    current.rows
                }
            preferences.store(
                current.copy(
                    rows = resolvedRows,
                    pendingActions = remaining,
                    hasPendingWrites =
                        current.repositoryHasPendingWrites || remaining.isNotEmpty(),
                    errorMessage = if (outcome.successful) null else outcome.errorMessage,
                ),
            )
            changed = true
        }
        return changed
    }

    suspend fun rollToLogicalDay(logicalDay: LocalDate): Boolean {
        var changed = false
        context.widgetSnapshotDataStore.edit { preferences ->
            val current = preferences.snapshot()
            if (current.logicalDay == logicalDay) return@edit
            val rows =
                current.medicines.flatMap { medicine ->
                    buildList {
                        if (medicine.afternoonEnabled) {
                            add(
                                WidgetDoseRow(
                                    medicine.id,
                                    medicine.name,
                                    DoseSlot.AFTERNOON,
                                    medicine.afternoonLabel,
                                    false,
                                    null,
                                    null,
                                ),
                            )
                        }
                        if (medicine.nightEnabled) {
                            add(
                                WidgetDoseRow(
                                    medicine.id,
                                    medicine.name,
                                    DoseSlot.NIGHT,
                                    medicine.nightLabel,
                                    false,
                                    null,
                                    null,
                                ),
                            )
                        }
                    }
                }
            preferences.store(
                current.copy(
                    logicalDay = logicalDay,
                    rows = rows,
                    pendingActions = emptyList(),
                    hasPendingWrites = current.repositoryHasPendingWrites,
                ),
            )
            changed = true
        }
        return changed
    }

    suspend fun clearAccount() {
        write(WidgetSnapshot(logicalDay = LocalDate.now()))
    }

    companion object {
        fun fromMedicine(value: Medicine): WidgetMedicine =
            WidgetMedicine(
                id = value.id,
                name = value.name,
                afternoonEnabled = value.afternoonEnabled,
                afternoonLabel = value.afternoonLabel,
                nightEnabled = value.nightEnabled,
                nightLabel = value.nightLabel,
            )

        fun fromRow(value: DoseRow): WidgetDoseRow =
            WidgetDoseRow(
                medicineId = value.medicineId,
                medicineName = value.medicineName,
                slot = value.slot,
                label = value.label,
                isTaken = value.isTaken,
                checkedAt = value.checkedAt,
                checkedTimezone = value.checkedTimezone,
            )
    }
}

private fun WidgetSnapshot.canOptimisticallyCheck(
    expectedUid: String,
    matching: WidgetDoseRow?,
): Boolean = ownerUid == expectedUid && signedIn && matching?.isTaken == false

private fun Preferences.snapshot(): WidgetSnapshot =
    this[SNAPSHOT]
        ?.let(WidgetSnapshotCodec::decode)
        ?: WidgetSnapshot(isLoading = true)

private fun MutablePreferences.store(snapshot: WidgetSnapshot) {
    this[SNAPSHOT] = WidgetSnapshotCodec.encode(snapshot)
    this[SIGNED_IN] = snapshot.signedIn
}

internal object WidgetSnapshotCodec {
    fun encode(snapshot: WidgetSnapshot): String =
        JSONObject()
            .put("ownerUid", snapshot.ownerUid)
            .put("signedIn", snapshot.signedIn)
            .put("isLoading", snapshot.isLoading)
            .put("logicalDay", snapshot.logicalDay.toString())
            .put(
                "medicines",
                JSONArray().apply {
                    snapshot.medicines.forEach { medicine ->
                        put(
                            JSONObject()
                                .put("id", medicine.id)
                                .put("name", medicine.name)
                                .put("afternoonEnabled", medicine.afternoonEnabled)
                                .put("afternoonLabel", medicine.afternoonLabel)
                                .put("nightEnabled", medicine.nightEnabled)
                                .put("nightLabel", medicine.nightLabel),
                        )
                    }
                },
            ).put(
                "rows",
                JSONArray().apply {
                    snapshot.rows.forEach { row ->
                        put(
                            JSONObject()
                                .put("medicineId", row.medicineId)
                                .put("medicineName", row.medicineName)
                                .put("slot", row.slot.wireValue)
                                .put("label", row.label)
                                .put("isTaken", row.isTaken)
                                .put("checkedAt", row.checkedAt?.toString())
                                .put("checkedTimezone", row.checkedTimezone),
                        )
                    }
                },
            ).put(
                "pendingActions",
                JSONArray().apply {
                    snapshot.pendingActions.forEach { action ->
                        put(
                            JSONObject()
                                .put("actionId", action.actionId)
                                .put("medicineId", action.medicineId)
                                .put("slot", action.slot.wireValue)
                                .put("createdAt", action.createdAt.toString())
                                .put("submitted", action.submitted),
                        )
                    }
                },
            ).put("fromCache", snapshot.fromCache)
            .put("hasPendingWrites", snapshot.hasPendingWrites)
            .put("repositoryHasPendingWrites", snapshot.repositoryHasPendingWrites)
            .put("errorMessage", snapshot.errorMessage)
            .toString()

    fun decode(raw: String): WidgetSnapshot =
        runCatching {
            val json = JSONObject(raw)
            val medicinesJson = json.optJSONArray("medicines") ?: JSONArray()
            val rowsJson = json.optJSONArray("rows") ?: JSONArray()
            val pendingActionsJson = json.optJSONArray("pendingActions") ?: JSONArray()
            WidgetSnapshot(
                ownerUid = json.optNullableString("ownerUid"),
                signedIn = json.optBoolean("signedIn"),
                isLoading = json.optBoolean("isLoading"),
                logicalDay = LocalDate.parse(json.getString("logicalDay")),
                medicines =
                    (0 until medicinesJson.length()).map { index ->
                        val value = medicinesJson.getJSONObject(index)
                        WidgetMedicine(
                            id = value.getString("id"),
                            name = value.getString("name"),
                            afternoonEnabled = value.getBoolean("afternoonEnabled"),
                            afternoonLabel = value.getString("afternoonLabel"),
                            nightEnabled = value.getBoolean("nightEnabled"),
                            nightLabel = value.getString("nightLabel"),
                        )
                    },
                rows =
                    (0 until rowsJson.length()).mapNotNull { index ->
                        val value = rowsJson.getJSONObject(index)
                        val slot = DoseSlot.fromWire(value.getString("slot")) ?: return@mapNotNull null
                        WidgetDoseRow(
                            medicineId = value.getString("medicineId"),
                            medicineName = value.getString("medicineName"),
                            slot = slot,
                            label = value.getString("label"),
                            isTaken = value.getBoolean("isTaken"),
                            checkedAt = value.optNullableString("checkedAt")?.let(Instant::parse),
                            checkedTimezone = value.optNullableString("checkedTimezone"),
                        )
                    },
                pendingActions =
                    (0 until pendingActionsJson.length()).mapNotNull { index ->
                        val value = pendingActionsJson.getJSONObject(index)
                        val slot = DoseSlot.fromWire(value.getString("slot")) ?: return@mapNotNull null
                        WidgetPendingAction(
                            actionId = value.getString("actionId"),
                            medicineId = value.getString("medicineId"),
                            slot = slot,
                            createdAt =
                                value
                                    .optNullableString("createdAt")
                                    ?.let(Instant::parse)
                                    ?: Instant.EPOCH,
                            submitted = value.optBoolean("submitted"),
                        )
                    },
                fromCache = json.optBoolean("fromCache"),
                hasPendingWrites = json.optBoolean("hasPendingWrites"),
                repositoryHasPendingWrites = json.optBoolean("repositoryHasPendingWrites"),
                errorMessage = json.optNullableString("errorMessage"),
            )
        }.getOrElse { WidgetSnapshot() }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf(String::isNotBlank)
}
