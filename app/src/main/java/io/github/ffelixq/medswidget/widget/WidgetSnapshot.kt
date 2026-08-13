package io.github.ffelixq.medswidget.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ffelixq.medswidget.data.CountdownWriteOutcome
import io.github.ffelixq.medswidget.data.DoseWriteOutcome
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownAction
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.CountdownState
import io.github.ffelixq.medswidget.domain.CountdownStatus
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.security.SensitiveDataCipher
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
    val displayName: String = name,
    val morningEnabled: Boolean = false,
    val morningLabel: String = DoseSlot.MORNING.defaultLabel,
    val morningCountdownMinutes: Int? = null,
    val afternoonEnabled: Boolean,
    val afternoonLabel: String,
    val afternoonCountdownMinutes: Int? = null,
    val eveningEnabled: Boolean = false,
    val eveningLabel: String = DoseSlot.EVENING.defaultLabel,
    val eveningCountdownMinutes: Int? = null,
    val nightEnabled: Boolean,
    val nightLabel: String,
    val nightCountdownMinutes: Int? = null,
    val supplyEnabled: Boolean = false,
    val supplyRemainingUnits: Double? = null,
    val unitsPerDose: Double = 1.0,
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
}

data class WidgetDoseRow(
    val medicineId: String,
    val medicineName: String,
    val slot: DoseSlot,
    val label: String,
    val isTaken: Boolean,
    val checkedAt: Instant?,
    val checkedTimezone: String? = null,
    val countdownMinutes: Int? = null,
    val countdown: CountdownState? = null,
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
            countdownMinutes = countdownMinutes,
            countdown = countdown,
        )
}

data class WidgetPendingAction(
    val actionId: String,
    val medicineId: String,
    val slot: DoseSlot,
    val createdAt: Instant,
    val submitted: Boolean = false,
)

data class WidgetPendingCountdownAction(
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
    val pendingCountdownActions: List<WidgetPendingCountdownAction> = emptyList(),
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
private val WIDGET_SNAPSHOT_CIPHER = SensitiveDataCipher("widget_snapshot")

@Suppress("TooManyFunctions")
class WidgetSnapshotStore(
    private val context: Context,
) {
    val flow: Flow<WidgetSnapshot> =
        context.widgetSnapshotDataStore.data.map { preferences ->
            preferences[SNAPSHOT]
                ?.let(WIDGET_SNAPSHOT_CIPHER::decryptOrPlaintext)
                ?.let(WidgetSnapshotCodec::decode)
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
    @Suppress("CyclomaticComplexMethod")
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
            val pendingCountdownActions =
                current.pendingCountdownActions
                    .takeIf { sameAccountAndDay || current.ownerUid == snapshot.ownerUid }
                    .orEmpty()
                    .filter { action ->
                        !resolvePendingActions ||
                            snapshot.fromCache ||
                            repositoryPending ||
                            !action.submitted
                    }
            val pendingCountdownKeys =
                pendingCountdownActions.map { it.medicineId to it.slot }.toSet()
            val mergedRows =
                snapshot.rows.map { repositoryRow ->
                    val key = repositoryRow.medicineId to repositoryRow.slot
                    val currentRow =
                        current.rows.firstOrNull {
                            it.medicineId == repositoryRow.medicineId &&
                                it.slot == repositoryRow.slot
                        }
                    repositoryRow.copy(
                        isTaken =
                            if (key in pendingKeys) {
                                currentRow?.isTaken ?: repositoryRow.isTaken
                            } else {
                                repositoryRow.isTaken
                            },
                        checkedAt = if (key in pendingKeys) currentRow?.checkedAt else repositoryRow.checkedAt,
                        checkedTimezone =
                            if (key in pendingKeys) currentRow?.checkedTimezone else repositoryRow.checkedTimezone,
                        countdown =
                            if (key in pendingCountdownKeys) currentRow?.countdown else repositoryRow.countdown,
                    )
                }
            preferences.store(
                snapshot.copy(
                    rows = mergedRows,
                    pendingActions = pendingActions,
                    pendingCountdownActions = pendingCountdownActions,
                    hasPendingWrites =
                        repositoryPending ||
                            pendingActions.isNotEmpty() ||
                            pendingCountdownActions.isNotEmpty(),
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
                                    countdown = null,
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

    @Suppress("LongParameterList", "ComplexCondition")
    suspend fun markCountdownStartedOptimistically(
        expectedUid: String,
        medicineId: String,
        slot: DoseSlot,
        logicalDay: LocalDate,
        durationMinutes: Int,
        startedAt: Instant,
        timezoneId: String,
        source: CheckSource,
        actionId: String,
    ): Boolean {
        var changed = false
        context.widgetSnapshotDataStore.edit { preferences ->
            val current = preferences.snapshot()
            val matching = current.rows.firstOrNull { it.medicineId == medicineId && it.slot == slot }
            if (
                current.ownerUid != expectedUid ||
                !current.signedIn ||
                matching == null ||
                matching.isTaken ||
                matching.countdownMinutes != durationMinutes ||
                matching.countdown?.status == CountdownStatus.RUNNING
            ) {
                return@edit
            }
            val countdown =
                CountdownState(
                    id = "${logicalDay}_${medicineId}_${slot.wireValue}",
                    ownerUid = expectedUid,
                    logicalDay = logicalDay,
                    medicineId = medicineId,
                    slot = slot,
                    durationMinutes = durationMinutes,
                    startedAt = startedAt,
                    targetAt = CountdownLogic.targetAt(startedAt, durationMinutes),
                    startedTimezone = timezoneId,
                    startedSource = source,
                    status = CountdownStatus.RUNNING,
                    cancelledAt = null,
                    completedAt = null,
                    lastActionId = actionId,
                )
            preferences.store(
                current.copy(
                    rows =
                        current.rows.map {
                            if (it.medicineId == medicineId && it.slot == slot) {
                                it.copy(countdown = countdown)
                            } else {
                                it
                            }
                        },
                    pendingCountdownActions =
                        current.pendingCountdownActions
                            .filterNot { it.medicineId == medicineId && it.slot == slot } +
                            WidgetPendingCountdownAction(actionId, medicineId, slot, startedAt),
                    hasPendingWrites = true,
                    errorMessage = null,
                ),
            )
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

    suspend fun markCountdownActionSubmitted(actionId: String): Boolean {
        var changed = false
        context.widgetSnapshotDataStore.edit { preferences ->
            val current = preferences.snapshot()
            if (current.pendingCountdownActions.none { it.actionId == actionId }) return@edit
            preferences.store(
                current.copy(
                    pendingCountdownActions =
                        current.pendingCountdownActions.map {
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
            val expiredCountdowns =
                current.pendingCountdownActions.filter { action ->
                    !action.submitted && !action.createdAt.isAfter(createdBefore)
                }
            if (expired.isEmpty() && expiredCountdowns.isEmpty()) return@edit
            val expiredKeys = expired.map { it.medicineId to it.slot }.toSet()
            val remaining = current.pendingActions - expired.toSet()
            val expiredCountdownKeys = expiredCountdowns.map { it.medicineId to it.slot }.toSet()
            val remainingCountdowns = current.pendingCountdownActions - expiredCountdowns.toSet()
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
                            } else if ((row.medicineId to row.slot) in expiredCountdownKeys) {
                                row.copy(countdown = null)
                            } else {
                                row
                            }
                        },
                    pendingActions = remaining,
                    pendingCountdownActions = remainingCountdowns,
                    hasPendingWrites =
                        current.repositoryHasPendingWrites ||
                            remaining.isNotEmpty() ||
                            remainingCountdowns.isNotEmpty(),
                    errorMessage =
                        if (expiredCountdowns.isNotEmpty()) {
                            "An interrupted widget countdown was not saved. Try again."
                        } else {
                            "An interrupted widget check was not saved. Try again."
                        },
                ),
            )
            changed = true
        }
        return changed
    }

    suspend fun resolveCountdownWriteOutcome(outcome: CountdownWriteOutcome): Boolean {
        var changed = false
        context.widgetSnapshotDataStore.edit { preferences ->
            val current = preferences.snapshot()
            if (current.ownerUid != outcome.ownerUid || !current.signedIn) return@edit
            val pending =
                current.pendingCountdownActions.firstOrNull { it.actionId == outcome.actionId }
                    ?: return@edit
            val remaining = current.pendingCountdownActions.filterNot { it.actionId == outcome.actionId }
            val rows =
                if (!outcome.successful && outcome.action == CountdownAction.START) {
                    current.rows.map { row ->
                        if (row.medicineId == pending.medicineId && row.slot == pending.slot) {
                            row.copy(countdown = null)
                        } else {
                            row
                        }
                    }
                } else {
                    current.rows
                }
            preferences.store(
                current.copy(
                    rows = rows,
                    pendingCountdownActions = remaining,
                    hasPendingWrites =
                        current.repositoryHasPendingWrites ||
                            current.pendingActions.isNotEmpty() ||
                            remaining.isNotEmpty(),
                    errorMessage = if (outcome.successful) null else outcome.errorMessage,
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
                        current.repositoryHasPendingWrites ||
                            remaining.isNotEmpty() ||
                            current.pendingCountdownActions.isNotEmpty(),
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
                    DoseSlot.entries
                        .filter(medicine::isEnabled)
                        .map { slot ->
                            WidgetDoseRow(
                                medicineId = medicine.id,
                                medicineName = medicine.displayName,
                                slot = slot,
                                label = medicine.label(slot),
                                isTaken = false,
                                checkedAt = null,
                                checkedTimezone = null,
                                countdownMinutes = medicine.countdownMinutes(slot),
                                countdown =
                                    current.rows
                                        .firstOrNull {
                                            it.medicineId == medicine.id && it.slot == slot
                                        }?.countdown,
                            )
                        }
                }
            val pendingCountdownActions =
                current.pendingCountdownActions.filter { pending ->
                    rows.any { it.medicineId == pending.medicineId && it.slot == pending.slot }
                }
            preferences.store(
                current.copy(
                    logicalDay = logicalDay,
                    rows = rows,
                    pendingActions = emptyList(),
                    pendingCountdownActions = pendingCountdownActions,
                    hasPendingWrites =
                        current.repositoryHasPendingWrites ||
                            pendingCountdownActions.isNotEmpty(),
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
                displayName = value.widgetDisplayName(),
                morningEnabled = value.morningEnabled,
                morningLabel = value.morningLabel,
                morningCountdownMinutes = value.morningCountdownMinutes,
                afternoonEnabled = value.afternoonEnabled,
                afternoonLabel = value.afternoonLabel,
                afternoonCountdownMinutes = value.afternoonCountdownMinutes,
                eveningEnabled = value.eveningEnabled,
                eveningLabel = value.eveningLabel,
                eveningCountdownMinutes = value.eveningCountdownMinutes,
                nightEnabled = value.nightEnabled,
                nightLabel = value.nightLabel,
                nightCountdownMinutes = value.nightCountdownMinutes,
                supplyEnabled = value.supplyEnabled,
                supplyRemainingUnits = value.supplyInitialUnits,
                unitsPerDose = value.unitsPerDose,
            )

        fun fromRow(
            value: DoseRow,
            widgetMedicineName: String? = null,
        ): WidgetDoseRow =
            WidgetDoseRow(
                medicineId = value.medicineId,
                medicineName = widgetMedicineName ?: value.medicineName,
                slot = value.slot,
                label = value.label,
                isTaken = value.isTaken,
                checkedAt = value.checkedAt,
                checkedTimezone = value.checkedTimezone,
                countdownMinutes = value.countdownMinutes,
                countdown = value.countdown,
            )
    }
}

private fun WidgetSnapshot.canOptimisticallyCheck(
    expectedUid: String,
    matching: WidgetDoseRow?,
): Boolean = ownerUid == expectedUid && signedIn && matching?.isTaken == false

private fun Preferences.snapshot(): WidgetSnapshot =
    this[SNAPSHOT]
        ?.let(WIDGET_SNAPSHOT_CIPHER::decryptOrPlaintext)
        ?.let(WidgetSnapshotCodec::decode)
        ?: WidgetSnapshot(isLoading = true)

private fun MutablePreferences.store(snapshot: WidgetSnapshot) {
    this[SNAPSHOT] = WIDGET_SNAPSHOT_CIPHER.encrypt(WidgetSnapshotCodec.encode(snapshot))
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
                                .put("displayName", medicine.displayName)
                                .put("morningEnabled", medicine.morningEnabled)
                                .put("morningLabel", medicine.morningLabel)
                                .put("morningCountdownMinutes", medicine.morningCountdownMinutes)
                                .put("afternoonEnabled", medicine.afternoonEnabled)
                                .put("afternoonLabel", medicine.afternoonLabel)
                                .put("afternoonCountdownMinutes", medicine.afternoonCountdownMinutes)
                                .put("eveningEnabled", medicine.eveningEnabled)
                                .put("eveningLabel", medicine.eveningLabel)
                                .put("eveningCountdownMinutes", medicine.eveningCountdownMinutes)
                                .put("nightEnabled", medicine.nightEnabled)
                                .put("nightLabel", medicine.nightLabel)
                                .put("nightCountdownMinutes", medicine.nightCountdownMinutes)
                                .put("supplyEnabled", medicine.supplyEnabled)
                                .put("supplyRemainingUnits", medicine.supplyRemainingUnits)
                                .put("unitsPerDose", medicine.unitsPerDose),
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
                                .put("checkedTimezone", row.checkedTimezone)
                                .put("countdownMinutes", row.countdownMinutes)
                                .put("countdown", row.countdown?.toJson()),
                        )
                    }
                },
            ).put(
                "pendingCountdownActions",
                JSONArray().apply {
                    snapshot.pendingCountdownActions.forEach { action ->
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
            WidgetSnapshot(
                ownerUid = json.optNullableString("ownerUid"),
                signedIn = json.optBoolean("signedIn"),
                isLoading = json.optBoolean("isLoading"),
                logicalDay = LocalDate.parse(json.getString("logicalDay")),
                medicines = decodeMedicines(json.optJSONArray("medicines")),
                rows = decodeRows(json.optJSONArray("rows")),
                pendingCountdownActions =
                    decodePendingCountdownActions(json.optJSONArray("pendingCountdownActions")),
                pendingActions = decodePendingActions(json.optJSONArray("pendingActions")),
                fromCache = json.optBoolean("fromCache"),
                hasPendingWrites = json.optBoolean("hasPendingWrites"),
                repositoryHasPendingWrites = json.optBoolean("repositoryHasPendingWrites"),
                errorMessage = json.optNullableString("errorMessage"),
            )
        }.getOrElse { WidgetSnapshot() }

    private fun decodeMedicines(values: JSONArray?): List<WidgetMedicine> {
        val array = values ?: JSONArray()
        return (0 until array.length()).map { index ->
            array.getJSONObject(index).toWidgetMedicine()
        }
    }

    private fun JSONObject.toWidgetMedicine(): WidgetMedicine {
        val name = getString("name")
        return WidgetMedicine(
            id = getString("id"),
            name = name,
            displayName = optNullableString("displayName") ?: name,
            morningEnabled = optBoolean("morningEnabled", false),
            morningLabel = optNullableString("morningLabel") ?: DoseSlot.MORNING.defaultLabel,
            morningCountdownMinutes = optNullableInt("morningCountdownMinutes"),
            afternoonEnabled = optBoolean("afternoonEnabled", false),
            afternoonLabel =
                optNullableString("afternoonLabel") ?: DoseSlot.AFTERNOON.defaultLabel,
            afternoonCountdownMinutes = optNullableInt("afternoonCountdownMinutes"),
            eveningEnabled = optBoolean("eveningEnabled", false),
            eveningLabel = optNullableString("eveningLabel") ?: DoseSlot.EVENING.defaultLabel,
            eveningCountdownMinutes = optNullableInt("eveningCountdownMinutes"),
            nightEnabled = optBoolean("nightEnabled", false),
            nightLabel = optNullableString("nightLabel") ?: DoseSlot.NIGHT.defaultLabel,
            nightCountdownMinutes = optNullableInt("nightCountdownMinutes"),
            supplyEnabled = optBoolean("supplyEnabled", false),
            supplyRemainingUnits = optNullableDouble("supplyRemainingUnits"),
            unitsPerDose = optDouble("unitsPerDose", 1.0),
        )
    }

    private fun decodeRows(values: JSONArray?): List<WidgetDoseRow> {
        val array = values ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val value = array.getJSONObject(index)
            val slot = DoseSlot.fromWire(value.getString("slot")) ?: return@mapNotNull null
            WidgetDoseRow(
                medicineId = value.getString("medicineId"),
                medicineName = value.getString("medicineName"),
                slot = slot,
                label = value.getString("label"),
                isTaken = value.getBoolean("isTaken"),
                checkedAt = value.optNullableString("checkedAt")?.let(Instant::parse),
                checkedTimezone = value.optNullableString("checkedTimezone"),
                countdownMinutes = value.optNullableInt("countdownMinutes"),
                countdown = value.optJSONObject("countdown")?.toCountdownState(),
            )
        }
    }

    private fun decodePendingCountdownActions(values: JSONArray?): List<WidgetPendingCountdownAction> {
        val array = values ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val value = array.getJSONObject(index)
            val slot = DoseSlot.fromWire(value.getString("slot")) ?: return@mapNotNull null
            WidgetPendingCountdownAction(
                actionId = value.getString("actionId"),
                medicineId = value.getString("medicineId"),
                slot = slot,
                createdAt =
                    value.optNullableString("createdAt")?.let(Instant::parse) ?: Instant.EPOCH,
                submitted = value.optBoolean("submitted"),
            )
        }
    }

    private fun decodePendingActions(values: JSONArray?): List<WidgetPendingAction> {
        val array = values ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val value = array.getJSONObject(index)
            val slot = DoseSlot.fromWire(value.getString("slot")) ?: return@mapNotNull null
            WidgetPendingAction(
                actionId = value.getString("actionId"),
                medicineId = value.getString("medicineId"),
                slot = slot,
                createdAt =
                    value.optNullableString("createdAt")?.let(Instant::parse) ?: Instant.EPOCH,
                submitted = value.optBoolean("submitted"),
            )
        }
    }

    private fun CountdownState.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("ownerUid", ownerUid)
            .put("logicalDay", logicalDay.toString())
            .put("medicineId", medicineId)
            .put("slot", slot.wireValue)
            .put("durationMinutes", durationMinutes)
            .put("startedAt", startedAt.toString())
            .put("targetAt", targetAt.toString())
            .put("startedTimezone", startedTimezone)
            .put("startedSource", startedSource.wireValue)
            .put("status", status.wireValue)
            .put("cancelledAt", cancelledAt?.toString())
            .put("completedAt", completedAt?.toString())
            .put("lastActionId", lastActionId)
            .put("updatedAt", updatedAt.toString())
            .put("schemaVersion", schemaVersion)

    private fun JSONObject.toCountdownState(): CountdownState? =
        runCatching {
            CountdownState(
                id = getString("id"),
                ownerUid = getString("ownerUid"),
                logicalDay = LocalDate.parse(getString("logicalDay")),
                medicineId = getString("medicineId"),
                slot = DoseSlot.fromWire(getString("slot")) ?: return null,
                durationMinutes = getInt("durationMinutes"),
                startedAt = Instant.parse(getString("startedAt")),
                targetAt = Instant.parse(getString("targetAt")),
                startedTimezone = getString("startedTimezone"),
                startedSource = CheckSource.fromWire(getString("startedSource")) ?: return null,
                status = CountdownStatus.fromWire(getString("status")) ?: return null,
                cancelledAt = optNullableString("cancelledAt")?.let(Instant::parse),
                completedAt = optNullableString("completedAt")?.let(Instant::parse),
                lastActionId = getString("lastActionId"),
                updatedAt = optNullableString("updatedAt")?.let(Instant::parse) ?: Instant.EPOCH,
                schemaVersion = optInt("schemaVersion", 1),
            )
        }.getOrNull()
}

private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key).takeIf(String::isNotBlank)

private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
