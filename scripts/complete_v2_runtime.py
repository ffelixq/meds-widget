from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text)


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise RuntimeError(f"Expected text not found in {path}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


def regex_once(path: str, pattern: str, repl: str) -> None:
    text = read(path)
    updated, count = re.subn(pattern, repl, text, count=1, flags=re.MULTILINE | re.DOTALL)
    if count != 1:
        raise RuntimeError(f"Expected one regex match in {path}, got {count}: {pattern[:120]!r}")
    write(path, updated)


snapshot_path = "app/src/main/java/io/github/ffelixq/medswidget/widget/WidgetSnapshot.kt"
regex_once(
    snapshot_path,
    r"data class WidgetMedicine\(.*?\n\)\n\n(?=data class WidgetDoseRow)",
    '''data class WidgetMedicine(
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

''',
)
regex_once(
    snapshot_path,
    r"    suspend fun rollToLogicalDay\(logicalDay: LocalDate\): Boolean \{.*?\n    \}\n\n    suspend fun clearAccount",
    '''    suspend fun rollToLogicalDay(logicalDay: LocalDate): Boolean {
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

    suspend fun clearAccount''',
)
regex_once(
    snapshot_path,
    r"    companion object \{\n        fun fromMedicine\(value: Medicine\): WidgetMedicine =.*?\n        fun fromRow\(value: DoseRow\): WidgetDoseRow =.*?\n            \)\n    \}\n",
    '''    companion object {
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
''',
)
replace_once(
    snapshot_path,
    '''                                .put("id", medicine.id)
                                .put("name", medicine.name)
                                .put("afternoonEnabled", medicine.afternoonEnabled)
                                .put("afternoonLabel", medicine.afternoonLabel)
                                .put("afternoonCountdownMinutes", medicine.afternoonCountdownMinutes)
                                .put("nightEnabled", medicine.nightEnabled)
                                .put("nightLabel", medicine.nightLabel)
                                .put("nightCountdownMinutes", medicine.nightCountdownMinutes),
''',
    '''                                .put("id", medicine.id)
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
''',
)
replace_once(
    snapshot_path,
    '''                        WidgetMedicine(
                            id = value.getString("id"),
                            name = value.getString("name"),
                            afternoonEnabled = value.getBoolean("afternoonEnabled"),
                            afternoonLabel = value.getString("afternoonLabel"),
                            afternoonCountdownMinutes = value.optNullableInt("afternoonCountdownMinutes"),
                            nightEnabled = value.getBoolean("nightEnabled"),
                            nightLabel = value.getString("nightLabel"),
                            nightCountdownMinutes = value.optNullableInt("nightCountdownMinutes"),
                        )
''',
    '''                        val name = value.getString("name")
                        WidgetMedicine(
                            id = value.getString("id"),
                            name = name,
                            displayName = value.optNullableString("displayName") ?: name,
                            morningEnabled = value.optBoolean("morningEnabled", false),
                            morningLabel =
                                value.optNullableString("morningLabel")
                                    ?: DoseSlot.MORNING.defaultLabel,
                            morningCountdownMinutes = value.optNullableInt("morningCountdownMinutes"),
                            afternoonEnabled = value.optBoolean("afternoonEnabled", false),
                            afternoonLabel =
                                value.optNullableString("afternoonLabel")
                                    ?: DoseSlot.AFTERNOON.defaultLabel,
                            afternoonCountdownMinutes = value.optNullableInt("afternoonCountdownMinutes"),
                            eveningEnabled = value.optBoolean("eveningEnabled", false),
                            eveningLabel =
                                value.optNullableString("eveningLabel")
                                    ?: DoseSlot.EVENING.defaultLabel,
                            eveningCountdownMinutes = value.optNullableInt("eveningCountdownMinutes"),
                            nightEnabled = value.optBoolean("nightEnabled", false),
                            nightLabel =
                                value.optNullableString("nightLabel")
                                    ?: DoseSlot.NIGHT.defaultLabel,
                            nightCountdownMinutes = value.optNullableInt("nightCountdownMinutes"),
                            supplyEnabled = value.optBoolean("supplyEnabled", false),
                            supplyRemainingUnits = value.optNullableDouble("supplyRemainingUnits"),
                            unitsPerDose = value.optDouble("unitsPerDose", 1.0),
                        )
''',
)
replace_once(
    snapshot_path,
    '''    private fun JSONObject.optNullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

''',
    '''    private fun JSONObject.optNullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key)

''',
)

app_graph = "app/src/main/java/io/github/ffelixq/medswidget/AppGraph.kt"
replace_once(
    app_graph,
    '''                medicines = accountSnapshot.medicines.value.map(WidgetSnapshotStore::fromMedicine),
                rows = rows.map(WidgetSnapshotStore::fromRow),
''',
    '''                medicines = accountSnapshot.medicines.value.map(WidgetSnapshotStore::fromMedicine),
                rows =
                    rows.map { row ->
                        val widgetName =
                            accountSnapshot.medicines.value
                                .firstOrNull { it.id == row.medicineId }
                                ?.widgetDisplayName()
                        WidgetSnapshotStore.fromRow(row, widgetName)
                    },
''',
)

replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/widget/SingleMedicineWidget.kt",
    "title = DisplayTransform.truncate(medicine.name, 28),",
    "title = DisplayTransform.truncate(medicine.displayName, 28),",
)

actions = "app/src/main/java/io/github/ffelixq/medswidget/widget/WidgetActions.kt"
regex_once(
    actions,
    r"    fun toDomain\(\n        medicine: WidgetMedicine,\n        uid: String,\n        row: WidgetDoseRow,\n    \): Medicine =\n        Medicine\(.*?\n            updatedAt = Instant.EPOCH,\n        \)\n",
    '''    fun toDomain(
        medicine: WidgetMedicine,
        uid: String,
        row: WidgetDoseRow,
    ): Medicine =
        Medicine(
            id = medicine.id,
            ownerUid = uid,
            name = medicine.name,
            morningEnabled = medicine.morningEnabled || row.slot == DoseSlot.MORNING,
            morningLabel = if (row.slot == DoseSlot.MORNING) row.label else medicine.morningLabel,
            morningCountdownMinutes =
                if (row.slot == DoseSlot.MORNING) row.countdownMinutes else medicine.morningCountdownMinutes,
            afternoonEnabled = medicine.afternoonEnabled || row.slot == DoseSlot.AFTERNOON,
            afternoonLabel = if (row.slot == DoseSlot.AFTERNOON) row.label else medicine.afternoonLabel,
            afternoonCountdownMinutes =
                if (row.slot == DoseSlot.AFTERNOON) row.countdownMinutes else medicine.afternoonCountdownMinutes,
            eveningEnabled = medicine.eveningEnabled || row.slot == DoseSlot.EVENING,
            eveningLabel = if (row.slot == DoseSlot.EVENING) row.label else medicine.eveningLabel,
            eveningCountdownMinutes =
                if (row.slot == DoseSlot.EVENING) row.countdownMinutes else medicine.eveningCountdownMinutes,
            nightEnabled = medicine.nightEnabled || row.slot == DoseSlot.NIGHT,
            nightLabel = if (row.slot == DoseSlot.NIGHT) row.label else medicine.nightLabel,
            nightCountdownMinutes =
                if (row.slot == DoseSlot.NIGHT) row.countdownMinutes else medicine.nightCountdownMinutes,
            supplyEnabled = medicine.supplyEnabled,
            supplyInitialUnits = medicine.supplyRemainingUnits,
            unitsPerDose = medicine.unitsPerDose,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
''',
)

print("V2 widget snapshot migration patches applied.")
