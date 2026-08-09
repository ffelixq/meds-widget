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
        raise RuntimeError(f"Expected text not found in {path}: {old[:100]!r}")
    write(path, text.replace(old, new, 1))


def regex_once(path: str, pattern: str, repl: str) -> None:
    text = read(path)
    updated, count = re.subn(pattern, repl, text, count=1, flags=re.MULTILINE | re.DOTALL)
    if count != 1:
        raise RuntimeError(f"Expected one regex match in {path}, got {count}: {pattern[:100]!r}")
    write(path, updated)


# Repository contract + Firebase implementation for refills.
replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/data/Repositories.kt",
    """    suspend fun delete(\n        uid: String,\n        medicineId: String,\n    )\n}\n""",
    """    suspend fun delete(\n        uid: String,\n        medicineId: String,\n    )\n\n    suspend fun refill(\n        uid: String,\n        medicineId: String,\n        units: Double,\n    )\n}\n""",
)
replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/data/UnavailableRepositories.kt",
    """    override suspend fun delete(\n        uid: String,\n        medicineId: String,\n    ) = unavailable()\n}\n""",
    """    override suspend fun delete(\n        uid: String,\n        medicineId: String,\n    ) = unavailable()\n\n    override suspend fun refill(\n        uid: String,\n        medicineId: String,\n        units: Double,\n    ) = unavailable()\n}\n""",
)
replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/firebase/FirestoreMedicineRepository.kt",
    """    override suspend fun delete(\n        uid: String,\n        medicineId: String,\n    ) {\n        dispatchWrite(uid) {\n            FirestorePaths\n                .medicines(firestore, uid)\n                .document(medicineId)\n                .delete()\n        }\n    }\n\n""",
    """    override suspend fun delete(\n        uid: String,\n        medicineId: String,\n    ) {\n        dispatchWrite(uid) {\n            FirestorePaths\n                .medicines(firestore, uid)\n                .document(medicineId)\n                .delete()\n        }\n    }\n\n    override suspend fun refill(\n        uid: String,\n        medicineId: String,\n        units: Double,\n    ) {\n        require(units.isFinite() && units > 0.0) { \"Refill amount must be greater than 0.\" }\n        dispatchWrite(uid) {\n            FirestorePaths\n                .medicines(firestore, uid)\n                .document(medicineId)\n                .update(\n                    mapOf(\n                        \"supplyInitialUnits\" to FieldValue.increment(units),\n                        \"updatedAt\" to FieldValue.serverTimestamp(),\n                    ),\n                )\n        }\n    }\n\n""",
)

# Supply changes are part of the same offline-capable Firestore batch as the dose action.
dose_path = "app/src/main/java/io/github/ffelixq/medswidget/firebase/FirestoreDoseRepository.kt"
text = read(dose_path)
text = text.replace(
    """                zone,\n                source,\n            )\n            true\n        }\n\n    override suspend fun skip""",
    """                zone,\n                source,\n                medicine,\n            )\n            true\n        }\n\n    override suspend fun skip""",
    1,
)
text = text.replace(
    """                zone,\n                source,\n            )\n            true\n        }\n\n    private fun writeAction""",
    """                zone,\n                source,\n                medicine,\n            )\n            true\n        }\n\n    private fun writeAction""",
    1,
)
text = text.replace(
    """        timezoneId: String,\n        source: CheckSource,\n    ) {\n""",
    """        timezoneId: String,\n        source: CheckSource,\n        medicine: Medicine? = null,\n    ) {\n""",
    1,
)
needle = """        val pendingWrite =\n            PendingWrite(\n"""
if needle not in text:
    raise RuntimeError("Could not find dose pending write insertion point")
text = text.replace(
    needle,
    """        applySupplyAdjustment(batch, state.ownerUid, medicine, action, rollbackState)\n        val pendingWrite =\n            PendingWrite(\n""",
    1,
)
needle = """    @Suppress(\"TooGenericExceptionCaught\")\n    private fun dispatchWrite(\n"""
if needle not in text:
    raise RuntimeError("Could not find dose dispatch insertion point")
text = text.replace(
    needle,
    """    private fun applySupplyAdjustment(\n        batch: WriteBatch,\n        uid: String,\n        medicine: Medicine?,\n        action: DoseAction,\n        previous: DoseState?,\n    ) {\n        if (medicine?.supplyEnabled != true || medicine.supplyInitialUnits == null) return\n        val delta =\n            when {\n                action == DoseAction.CHECK -> -medicine.unitsPerDose\n                action == DoseAction.UNDO && previous?.isTaken == true -> medicine.unitsPerDose\n                else -> 0.0\n            }\n        if (delta == 0.0) return\n        batch.update(\n            FirestorePaths.medicines(firestore, uid).document(medicine.id),\n            mapOf(\n                \"supplyInitialUnits\" to FieldValue.increment(delta),\n                \"updatedAt\" to FieldValue.serverTimestamp(),\n            ),\n        )\n    }\n\n    @Suppress(\"TooGenericExceptionCaught\")\n    private fun dispatchWrite(\n""",
    1,
)
write(dose_path, text)

# Current supply may become negative if a dose is logged when recorded stock is already zero.
# That is more useful than failing the dose write; a refill brings it back above zero.
replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/domain/MedicineValidation.kt",
    """        if (invalidPositiveNumber(draft.supplyInitialUnits)) {\n            errors[\"supplyInitialUnits\"] = \"Enter a starting supply greater than 0.\"\n        }\n""",
    """        if (invalidInventoryNumber(draft.supplyInitialUnits)) {\n            errors[\"supplyInitialUnits\"] = \"Current supply must be within the supported range.\"\n        }\n""",
)
replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/domain/MedicineValidation.kt",
    """private fun invalidPositiveNumber(value: Double?): Boolean {\n""",
    """private fun invalidInventoryNumber(value: Double?): Boolean =\n    value == null || !value.isFinite() || value < -SUPPLY_MAX_UNITS || value > SUPPLY_MAX_UNITS\n\nprivate fun invalidPositiveNumber(value: Double?): Boolean {\n""",
)
replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/ui/MedicineScreen.kt",
    'label = "Starting amount",',
    'label = "Current supply",',
)

# Refill action in the Today ViewModel.
replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/ui/MainViewModel.kt",
    """    fun archiveMedicine(\n""",
    """    fun refillSupply(\n        medicine: Medicine,\n        units: Double,\n    ) {\n        if (!medicine.supplyEnabled || !units.isFinite() || units <= 0.0) return\n        val uid = repositories.auth.session.value?.uid ?: return\n        viewModelScope.launch {\n            dependencies.accountOperationGate.runMutation {\n                repositories.medicines.refill(uid, medicine.id, units)\n                dependencies.refreshFromRepositories()\n            }\n        }\n    }\n\n    fun archiveMedicine(\n""",
)

# Today UI: supply status + refill dialog.
main_path = "app/src/main/java/io/github/ffelixq/medswidget/ui/MainScreen.kt"
text = read(main_path)
text = text.replace(
    """    onRestartCountdown: (DoseRow) -> Unit = {},\n    onAdd: () -> Unit,\n""",
    """    onRestartCountdown: (DoseRow) -> Unit = {},\n    onRefill: (Medicine, Double) -> Unit = { _, _ -> },\n    onAdd: () -> Unit,\n""",
    1,
)
text = text.replace(
    """    var skipReason by remember { mutableStateOf(\"\") }\n""",
    """    var skipReason by remember { mutableStateOf(\"\") }\n    var refillCandidate by remember { mutableStateOf<Medicine?>(null) }\n""",
    1,
)
text = text.replace(
    """                        onRestartCountdown = onRestartCountdown,\n                    )\n""",
    """                        onRestartCountdown = onRestartCountdown,\n                        onRefill = { refillCandidate = medicine },\n                    )\n""",
    1,
)
text = text.replace(
    """    skipCandidate?.let { row ->\n        SkipDoseDialog(\n""",
    """    refillCandidate?.let { medicine ->\n        RefillSupplyDialog(\n            medicine = medicine,\n            onDismiss = { refillCandidate = null },\n            onConfirm = { units ->\n                onRefill(medicine, units)\n                refillCandidate = null\n            },\n        )\n    }\n    skipCandidate?.let { row ->\n        SkipDoseDialog(\n""",
    1,
)
text = text.replace(
    """    onRestartCountdown: (DoseRow) -> Unit,\n) {\n""",
    """    onRestartCountdown: (DoseRow) -> Unit,\n    onRefill: () -> Unit,\n) {\n""",
    1,
)
text = text.replace(
    """            MedicineHeader(medicine, onEdit)\n            when {\n""",
    """            MedicineHeader(medicine, onEdit)\n            if (medicine.supplyEnabled && medicine.supplyInitialUnits != null) {\n                Row(\n                    modifier = Modifier.fillMaxWidth(),\n                    horizontalArrangement = Arrangement.SpaceBetween,\n                    verticalAlignment = Alignment.CenterVertically,\n                ) {\n                    Column {\n                        val amount = formatSupplyAmount(medicine.supplyInitialUnits)\n                        Text(\n                            \"Supply: $amount ${medicine.supplyUnitName}\",\n                            style = MaterialTheme.typography.bodyMedium,\n                        )\n                        if (\n                            medicine.lowSupplyThreshold != null &&\n                            medicine.supplyInitialUnits <= medicine.lowSupplyThreshold\n                        ) {\n                            Text(\n                                \"Low supply\",\n                                color = MaterialTheme.colorScheme.error,\n                                style = MaterialTheme.typography.labelMedium,\n                            )\n                        }\n                    }\n                    TextButton(onClick = onRefill) { Text(\"Refill\") }\n                }\n            }\n            when {\n""",
    1,
)
text += """\nprivate fun formatSupplyAmount(value: Double): String =\n    if (value % 1.0 == 0.0) value.toLong().toString() else \"%.2f\".format(value).trimEnd('0').trimEnd('.')\n"""
write(main_path, text)

support_path = "app/src/main/java/io/github/ffelixq/medswidget/ui/TodaySupport.kt"
text = read(support_path)
text = text.replace(
    "import androidx.compose.ui.unit.dp\n",
    "import androidx.compose.ui.text.input.KeyboardType\nimport androidx.compose.foundation.text.KeyboardOptions\nimport androidx.compose.ui.unit.dp\n",
    1,
)
text += """

@Suppress("FunctionNaming")
@Composable
internal fun RefillSupplyDialog(
    medicine: io.github.ffelixq.medswidget.domain.Medicine,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var amount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val parsed = amount.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add supply") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${medicine.name} · current ${medicine.supplyInitialUnits ?: 0.0} ${medicine.supplyUnitName}")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { value ->
                        amount = value.filter { it.isDigit() || it == '.' }.take(12)
                    },
                    label = { Text("Amount to add") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = parsed != null && parsed.isFinite() && parsed > 0.0,
                onClick = { parsed?.let(onConfirm) },
            ) { Text("Add refill") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
"""
write(support_path, text)

# Reminder scheduling is kept in sync with the latest medicine snapshot.
app_graph = "app/src/main/java/io/github/ffelixq/medswidget/AppGraph.kt"
text = read(app_graph)
text = text.replace(
    "import io.github.ffelixq.medswidget.sync.CountdownRefreshScheduler\n",
    "import io.github.ffelixq.medswidget.sync.CountdownRefreshScheduler\nimport io.github.ffelixq.medswidget.sync.MedicineReminderScheduler\n",
    1,
)
text = text.replace(
    """    val countdownRefreshScheduler = CountdownRefreshScheduler(context, clock)\n""",
    """    val countdownRefreshScheduler = CountdownRefreshScheduler(context, clock)\n    val reminderScheduler = MedicineReminderScheduler(context, clock)\n""",
    1,
)
text = text.replace(
    """                if (session == null) {\n                    mutableAccountDaySnapshot.value = null\n""",
    """                if (session == null) {\n                    reminderScheduler.cancelAll()\n                    mutableAccountDaySnapshot.value = null\n""",
    1,
)
text = text.replace(
    """        countdownRefreshScheduler.schedule(snapshotStore.read())\n    }\n\n    private fun handleDoseWriteOutcome""",
    """        countdownRefreshScheduler.schedule(snapshotStore.read())\n        reminderScheduler.sync(accountSnapshot.ownerUid, accountSnapshot.medicines.value)\n    }\n\n    private fun handleDoseWriteOutcome""",
    1,
)
text = text.replace(
    """    suspend fun refreshTemporalState() {\n        recomputeTemporalState(updateWidgets = true)\n    }\n\n""",
    """    suspend fun refreshTemporalState() {\n        recomputeTemporalState(updateWidgets = true)\n    }\n\n    suspend fun refreshReminders() {\n        val uid = currentAuthenticatedUid ?: return\n        val medicines =\n            withTimeoutOrNull(10_000L) { repositories.medicines.observeActive(uid).first() }\n                ?.value\n                ?: return\n        reminderScheduler.sync(uid, medicines)\n    }\n\n""",
    1,
)
write(app_graph, text)

replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/sync/MedicineReminderScheduler.kt",
    """    fun cancel(uid: String) {\n        workManager.cancelAllWorkByTag(ownerTag(uid))\n    }\n\n""",
    """    fun cancel(uid: String) {\n        workManager.cancelAllWorkByTag(ownerTag(uid))\n    }\n\n    fun cancelAll() {\n        workManager.cancelAllWorkByTag(REMINDER_TAG)\n    }\n\n""",
)
replace_once(
    "app/src/main/java/io/github/ffelixq/medswidget/sync/ResetBoundaryScheduler.kt",
    """            MedsApplication.graph(applicationContext).refreshTemporalState()\n            Result.success()\n""",
    """            val graph = MedsApplication.graph(applicationContext)\n            graph.refreshTemporalState()\n            graph.refreshReminders()\n            Result.success()\n""",
)

# Manifest: notification permission + secure FileProvider export.
manifest = "app/src/main/AndroidManifest.xml"
text = read(manifest)
text = text.replace(
    '<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n',
    '<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n',
    1,
)
text = text.replace(
    """        <activity\n            android:name=\".widget.SingleWidgetConfigurationActivity\"\n""",
    """        <provider\n            android:name=\"androidx.core.content.FileProvider\"\n            android:authorities=\"${applicationId}.fileprovider\"\n            android:exported=\"false\"\n            android:grantUriPermissions=\"true\">\n            <meta-data\n                android:name=\"android.support.FILE_PROVIDER_PATHS\"\n                android:resource=\"@xml/export_file_paths\" />\n        </provider>\n\n        <activity\n            android:name=\".widget.SingleWidgetConfigurationActivity\"\n""",
    1,
)
write(manifest, text)

# Settings hooks for tools/export.
settings = "app/src/main/java/io/github/ffelixq/medswidget/ui/SettingsScreen.kt"
text = read(settings)
text = text.replace(
    """    onDeleteGoogleAccount: () -> Unit,\n) {\n""",
    """    onDeleteGoogleAccount: () -> Unit,\n    onExport: () -> Unit,\n) {\n""",
    1,
)
text = text.replace(
    """            Text(\"Privacy\", style = MaterialTheme.typography.titleMedium)\n""",
    """            SettingsTools(onExport)\n\n            Text(\"Privacy\", style = MaterialTheme.typography.titleMedium)\n""",
    1,
)
write(settings, text)

# Activity export implementation + navigation wiring.
activity = "app/src/main/java/io/github/ffelixq/medswidget/ui/MainActivity.kt"
text = read(activity)
text = text.replace("import android.os.Bundle\n", "import android.os.Bundle\nimport androidx.core.content.FileProvider\n", 1)
text = text.replace(
    "import kotlinx.coroutines.launch\n",
    "import io.github.ffelixq.medswidget.util.MedicationCsvExporter\nimport kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.launch\nimport java.io.File\nimport java.time.LocalDate\n",
    1,
)
text = text.replace(
    """                        onDeleteGoogle = {\n                            requestGoogleCredential(\n""",
    """                        onExport = ::shareCsvExport,\n                        onDeleteGoogle = {\n                            requestGoogleCredential(\n""",
    1,
)
text = text.replace(
    """    // google-services creates this resource only for configured builds. A direct R reference\n""",
    """    private fun shareCsvExport() {\n        val uid = graph.repositories.auth.session.value?.uid ?: return\n        lifecycleScope.launch {\n            val medicines = graph.repositories.medicines.observeAll(uid).first().value\n            val history = graph.repositories.doses.observeHistory(uid).first().value\n            val directory = File(cacheDir, \"exports\").apply { mkdirs() }\n            val file = File(directory, \"meds-widget-${LocalDate.now()}.csv\")\n            file.writeText(MedicationCsvExporter.export(medicines, history))\n            val uri = FileProvider.getUriForFile(\n                this@MainActivity,\n                \"$packageName.fileprovider\",\n                file,\n            )\n            val shareIntent =\n                Intent(Intent.ACTION_SEND).apply {\n                    type = \"text/csv\"\n                    putExtra(Intent.EXTRA_STREAM, uri)\n                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)\n                }\n            startActivity(Intent.createChooser(shareIntent, \"Export Meds Widget data\"))\n        }\n    }\n\n    // google-services creates this resource only for configured builds. A direct R reference\n""",
    1,
)
text = text.replace(
    """    settingsViewModel: SettingsViewModel,\n    onDeleteGoogle: () -> Unit,\n) {\n""",
    """    settingsViewModel: SettingsViewModel,\n    onExport: () -> Unit,\n    onDeleteGoogle: () -> Unit,\n) {\n""",
    1,
)
text = text.replace(
    """                onDeleteGoogleAccount = onDeleteGoogle,\n            )\n""",
    """                onDeleteGoogleAccount = onDeleteGoogle,\n                onExport = onExport,\n            )\n""",
    1,
)
text = text.replace(
    """                onRestartCountdown = mainViewModel::restartCountdown,\n                onAdd = { navigation.navigate(Routes.ADD) },\n""",
    """                onRestartCountdown = mainViewModel::restartCountdown,\n                onRefill = mainViewModel::refillSupply,\n                onAdd = { navigation.navigate(Routes.ADD) },\n""",
    1,
)
write(activity, text)

# Version for the full V2 test release.
replace_once(
    "app/build.gradle.kts",
    'versionName = "1.1.0"',
    'versionName = "2.0.0"',
)

# Firestore security rule permits inventory to move below zero rather than rejecting a valid dose log.
rules = "firestore.rules"
text = read(rules)
text = text.replace(
    """    function isNullableNonNegativeNumber(value, maxValue) {\n""",
    """    function isBoundedNumber(value, maxValue) {\n      return (value is int || value is float)\n        && value >= -maxValue\n        && value <= maxValue;\n    }\n\n    function isNullableNonNegativeNumber(value, maxValue) {\n""",
    1,
)
text = text.replace(
    """            && isPositiveNumber(data.supplyInitialUnits, 1000000)\n""",
    """            && isBoundedNumber(data.supplyInitialUnits, 1000000)\n""",
    1,
)
write(rules, text)

print("V2 runtime integration patches applied.")
