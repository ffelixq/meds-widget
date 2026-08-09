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
        raise RuntimeError(f"Expected one match in {path}, got {count}: {pattern[:120]!r}")
    write(path, updated)


# MainScreen: keep the file below Detekt's function-count limit.
replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/ui/MainScreen.kt",
    '''\nprivate fun formatSupplyAmount(value: Double): String =\n    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')''',
    "",
)
replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/ui/TodaySupport.kt",
    "\n@Suppress(\"FunctionNaming\")\n@Composable\ninternal fun RefillSupplyDialog(",
    '''\ninternal fun formatSupplyAmount(value: Double): String =\n    if (value % 1.0 == 0.0) {\n        value.toLong().toString()\n    } else {\n        "%.2f".format(value).trimEnd('0').trimEnd('.')\n    }\n\n@Suppress("FunctionNaming")\n@Composable\ninternal fun RefillSupplyDialog(''',
)

# Firestore dose writes: carry metadata as one typed request instead of 8 parameters.
dose_path = "app/src/main/java/io/github/ffelixq/medswidget/firebase/FirestoreDoseRepository.kt"
text = read(dose_path)
text = text.replace(
    '''            writeAction(\n                state,\n                previous,\n                actionId,\n                DoseAction.CHECK,\n                occurredAt,\n                zone,\n                source,\n                medicine,\n            )''',
    '''            writeAction(\n                DoseWriteRequest(\n                    state = state,\n                    rollbackState = previous,\n                    actionId = actionId,\n                    action = DoseAction.CHECK,\n                    occurredAt = occurredAt,\n                    timezoneId = zone,\n                    source = source,\n                    medicine = medicine,\n                ),\n            )''',
    1,
)
text = text.replace(
    '''            writeAction(\n                state,\n                previous,\n                actionId,\n                DoseAction.SKIP,\n                now,\n                zone,\n                source,\n            )''',
    '''            writeAction(\n                DoseWriteRequest(\n                    state = state,\n                    rollbackState = previous,\n                    actionId = actionId,\n                    action = DoseAction.SKIP,\n                    occurredAt = now,\n                    timezoneId = zone,\n                    source = source,\n                ),\n            )''',
    1,
)
text = text.replace(
    '''            writeAction(\n                updated,\n                current,\n                actionId,\n                DoseAction.UNDO,\n                now,\n                zone,\n                source,\n                medicine,\n            )''',
    '''            writeAction(\n                DoseWriteRequest(\n                    state = updated,\n                    rollbackState = current,\n                    actionId = actionId,\n                    action = DoseAction.UNDO,\n                    occurredAt = now,\n                    timezoneId = zone,\n                    source = source,\n                    medicine = medicine,\n                ),\n            )''',
    1,
)
old_signature = '''    private fun writeAction(\n        state: DoseState,\n        rollbackState: DoseState?,\n        actionId: String,\n        action: DoseAction,\n        occurredAt: Instant,\n        timezoneId: String,\n        source: CheckSource,\n        medicine: Medicine? = null,\n    ) {\n'''
new_signature = '''    private fun writeAction(request: DoseWriteRequest) {\n        val state = request.state\n        val rollbackState = request.rollbackState\n        val actionId = request.actionId\n        val action = request.action\n        val occurredAt = request.occurredAt\n        val timezoneId = request.timezoneId\n        val source = request.source\n        val medicine = request.medicine\n'''
if old_signature not in text:
    raise RuntimeError("Firestore writeAction signature not found")
text = text.replace(old_signature, new_signature, 1)
insert_marker = "\n    private fun writeAction(request: DoseWriteRequest) {"
request_class = '''\n    private data class DoseWriteRequest(\n        val state: DoseState,\n        val rollbackState: DoseState?,\n        val actionId: String,\n        val action: DoseAction,\n        val occurredAt: Instant,\n        val timezoneId: String,\n        val source: CheckSource,\n        val medicine: Medicine? = null,\n    )\n'''
if insert_marker not in text:
    raise RuntimeError("Firestore request insertion point not found")
text = text.replace(insert_marker, request_class + insert_marker, 1)
write(dose_path, text)

# Widget snapshot decoder: split collection/record parsing into focused helpers.
snapshot_path = "app/src/main/java/io/github/ffelixq/medswidget/widget/WidgetSnapshot.kt"
text = read(snapshot_path)
start = text.index("    fun decode(raw: String): WidgetSnapshot =")
end = text.index("    private fun JSONObject.optNullableString", start)
new_decode = '''    fun decode(raw: String): WidgetSnapshot =\n        runCatching {\n            val json = JSONObject(raw)\n            WidgetSnapshot(\n                ownerUid = json.optNullableString("ownerUid"),\n                signedIn = json.optBoolean("signedIn"),\n                isLoading = json.optBoolean("isLoading"),\n                logicalDay = LocalDate.parse(json.getString("logicalDay")),\n                medicines = decodeMedicines(json.optJSONArray("medicines")),\n                rows = decodeRows(json.optJSONArray("rows")),\n                pendingCountdownActions =\n                    decodePendingCountdownActions(json.optJSONArray("pendingCountdownActions")),\n                pendingActions = decodePendingActions(json.optJSONArray("pendingActions")),\n                fromCache = json.optBoolean("fromCache"),\n                hasPendingWrites = json.optBoolean("hasPendingWrites"),\n                repositoryHasPendingWrites = json.optBoolean("repositoryHasPendingWrites"),\n                errorMessage = json.optNullableString("errorMessage"),\n            )\n        }.getOrElse { WidgetSnapshot() }\n\n    private fun decodeMedicines(values: JSONArray?): List<WidgetMedicine> {\n        val array = values ?: JSONArray()\n        return (0 until array.length()).map { index ->\n            array.getJSONObject(index).toWidgetMedicine()\n        }\n    }\n\n    private fun JSONObject.toWidgetMedicine(): WidgetMedicine {\n        val name = getString("name")\n        return WidgetMedicine(\n            id = getString("id"),\n            name = name,\n            displayName = optNullableString("displayName") ?: name,\n            morningEnabled = optBoolean("morningEnabled", false),\n            morningLabel = optNullableString("morningLabel") ?: DoseSlot.MORNING.defaultLabel,\n            morningCountdownMinutes = optNullableInt("morningCountdownMinutes"),\n            afternoonEnabled = optBoolean("afternoonEnabled", false),\n            afternoonLabel =\n                optNullableString("afternoonLabel") ?: DoseSlot.AFTERNOON.defaultLabel,\n            afternoonCountdownMinutes = optNullableInt("afternoonCountdownMinutes"),\n            eveningEnabled = optBoolean("eveningEnabled", false),\n            eveningLabel = optNullableString("eveningLabel") ?: DoseSlot.EVENING.defaultLabel,\n            eveningCountdownMinutes = optNullableInt("eveningCountdownMinutes"),\n            nightEnabled = optBoolean("nightEnabled", false),\n            nightLabel = optNullableString("nightLabel") ?: DoseSlot.NIGHT.defaultLabel,\n            nightCountdownMinutes = optNullableInt("nightCountdownMinutes"),\n            supplyEnabled = optBoolean("supplyEnabled", false),\n            supplyRemainingUnits = optNullableDouble("supplyRemainingUnits"),\n            unitsPerDose = optDouble("unitsPerDose", 1.0),\n        )\n    }\n\n    private fun decodeRows(values: JSONArray?): List<WidgetDoseRow> {\n        val array = values ?: JSONArray()\n        return (0 until array.length()).mapNotNull { index ->\n            val value = array.getJSONObject(index)\n            val slot = DoseSlot.fromWire(value.getString("slot")) ?: return@mapNotNull null\n            WidgetDoseRow(\n                medicineId = value.getString("medicineId"),\n                medicineName = value.getString("medicineName"),\n                slot = slot,\n                label = value.getString("label"),\n                isTaken = value.getBoolean("isTaken"),\n                checkedAt = value.optNullableString("checkedAt")?.let(Instant::parse),\n                checkedTimezone = value.optNullableString("checkedTimezone"),\n                countdownMinutes = value.optNullableInt("countdownMinutes"),\n                countdown = value.optJSONObject("countdown")?.toCountdownState(),\n            )\n        }\n    }\n\n    private fun decodePendingCountdownActions(\n        values: JSONArray?,\n    ): List<WidgetPendingCountdownAction> {\n        val array = values ?: JSONArray()\n        return (0 until array.length()).mapNotNull { index ->\n            val value = array.getJSONObject(index)\n            val slot = DoseSlot.fromWire(value.getString("slot")) ?: return@mapNotNull null\n            WidgetPendingCountdownAction(\n                actionId = value.getString("actionId"),\n                medicineId = value.getString("medicineId"),\n                slot = slot,\n                createdAt =\n                    value.optNullableString("createdAt")?.let(Instant::parse) ?: Instant.EPOCH,\n                submitted = value.optBoolean("submitted"),\n            )\n        }\n    }\n\n    private fun decodePendingActions(values: JSONArray?): List<WidgetPendingAction> {\n        val array = values ?: JSONArray()\n        return (0 until array.length()).mapNotNull { index ->\n            val value = array.getJSONObject(index)\n            val slot = DoseSlot.fromWire(value.getString("slot")) ?: return@mapNotNull null\n            WidgetPendingAction(\n                actionId = value.getString("actionId"),\n                medicineId = value.getString("medicineId"),\n                slot = slot,\n                createdAt =\n                    value.optNullableString("createdAt")?.let(Instant::parse) ?: Instant.EPOCH,\n                submitted = value.optBoolean("submitted"),\n            )\n        }\n    }\n\n'''
text = text[:start] + new_decode + text[end:]
# Force nullable helpers onto readable multi-line bodies so MaxLineLength cannot recur.
text = text.replace(
    '''    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)\n\n    private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)\n''',
    '''    private fun JSONObject.optNullableInt(key: String): Int? =\n        if (isNull(key) || !has(key)) null else optInt(key)\n\n    private fun JSONObject.optNullableDouble(key: String): Double? =\n        if (isNull(key) || !has(key)) null else optDouble(key)\n''',
)
write(snapshot_path, text)

# Reminder worker: parse once and use a single delivery branch rather than many early returns.
reminder_path = "app/src/main/java/io/github/ffelixq/medswidget/sync/MedicineReminderScheduler.kt"
text = read(reminder_path)
start = text.index("class MedicineReminderWorker(")
notifications = text.index("    private fun notificationsAllowed", start)
prefix = text[:start]
suffix = text[notifications:]
worker = '''class MedicineReminderWorker(\n    appContext: Context,\n    workerParams: WorkerParameters,\n) : CoroutineWorker(appContext, workerParams) {\n    override suspend fun doWork(): Result {\n        val request = reminderRequest() ?: return Result.success()\n        if (request.isExpired()) {\n            request.workName?.let {\n                WorkManager.getInstance(applicationContext).cancelUniqueWork(it)\n            }\n        } else if (canDeliver(request.uid)) {\n            ensureChannel(applicationContext)\n            showNotification(\n                applicationContext,\n                request.medicineId,\n                request.slot,\n                request.medicineName,\n                request.label,\n            )\n        }\n        return Result.success()\n    }\n\n    private fun reminderRequest(): ReminderRequest? {\n        val uid = inputData.getString(MedicineReminderScheduler.KEY_UID) ?: return null\n        val medicineId =\n            inputData.getString(MedicineReminderScheduler.KEY_MEDICINE_ID) ?: return null\n        val slot =\n            DoseSlot.fromWire(inputData.getString(MedicineReminderScheduler.KEY_SLOT).orEmpty())\n                ?: return null\n        val endDate =\n            inputData.getString(MedicineReminderScheduler.KEY_END_DATE)?.let { raw ->\n                runCatching { LocalDate.parse(raw) }.getOrNull()\n            }\n        return ReminderRequest(\n            uid = uid,\n            medicineId = medicineId,\n            slot = slot,\n            medicineName =\n                inputData.getString(MedicineReminderScheduler.KEY_MEDICINE_NAME).orEmpty(),\n            label = inputData.getString(MedicineReminderScheduler.KEY_LABEL).orEmpty(),\n            endDate = endDate,\n            workName = inputData.getString(MedicineReminderScheduler.KEY_WORK_NAME),\n        )\n    }\n\n    private fun ReminderRequest.isExpired(): Boolean =\n        endDate != null && LocalDate.now().isAfter(endDate)\n\n    private fun canDeliver(uid: String): Boolean {\n        val graph = MedsApplication.graph(applicationContext)\n        val signedIn =\n            graph.currentAuthenticatedUid == uid && !graph.accountOperationGate.isDeletionInProgress\n        return signedIn && notificationsAllowed(applicationContext)\n    }\n\n    private data class ReminderRequest(\n        val uid: String,\n        val medicineId: String,\n        val slot: DoseSlot,\n        val medicineName: String,\n        val label: String,\n        val endDate: LocalDate?,\n        val workName: String?,\n    )\n\n'''
write(reminder_path, prefix + worker + suffix)

print("Release Detekt refactor applied.")
