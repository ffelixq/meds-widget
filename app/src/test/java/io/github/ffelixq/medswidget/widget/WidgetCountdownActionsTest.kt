package io.github.ffelixq.medswidget.widget

import androidx.glance.action.actionParametersOf
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.Medicine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.ArrayDeque

class WidgetCountdownActionsTest {
    @Test
    fun `valid single and all widget requests start the intended timer`() =
        runTest {
            val single = FakeCountdownDependencies()
            WidgetCountdownHandler { single }.handle(parameters(CheckSource.WIDGET_2X2, 41)) { 41 }
            val all = FakeCountdownDependencies()
            WidgetCountdownHandler { all }.handle(parameters(CheckSource.WIDGET_4X2)) { null }

            assertEquals(CheckSource.WIDGET_2X2, single.starts.single().source)
            assertEquals(CheckSource.WIDGET_4X2, all.starts.single().source)
            assertEquals(120, single.starts.single().durationMinutes)
            assertEquals(
                120,
                single.starts
                    .single()
                    .medicine.afternoonCountdownMinutes,
            )
            assertEquals(1, single.widgetUpdates)
            assertEquals(1, all.widgetUpdates)
        }

    @Test
    fun `cold snapshot recovery can start offline from authoritative cached state`() =
        runTest {
            val dependencies =
                FakeCountdownDependencies(
                    snapshot = countdownSnapshot().copy(signedIn = false),
                    recoverySnapshot = countdownSnapshot(),
                )
            WidgetCountdownHandler { dependencies }.handle(parameters(CheckSource.WIDGET_4X2)) { null }

            assertEquals(1, dependencies.recoveries)
            assertEquals(2, dependencies.snapshotReads)
            assertEquals(1, dependencies.starts.size)
            assertEquals(1, dependencies.pendingSchedules)
            assertEquals(1, dependencies.refreshSchedules)
        }

    @Test
    fun `single widget requires matching runtime id and stored configuration`() =
        runTest {
            val mismatch = FakeCountdownDependencies()
            WidgetCountdownHandler { mismatch }.handle(parameters(CheckSource.WIDGET_2X2, 41)) { 52 }
            assertEquals(0, mismatch.refreshes)

            val missingConfiguration = FakeCountdownDependencies(configuration = null)
            val diagnostics = mutableListOf<String>()
            WidgetCountdownHandler(diagnostics::add) { missingConfiguration }
                .handle(parameters(CheckSource.WIDGET_2X2, 41)) { 41 }
            assertEquals(WidgetActionDiagnostic.CONFIGURATION_INVALID, diagnostics.last())
            assertTrue(missingConfiguration.starts.isEmpty())
        }

    @Test
    fun `rapid repeated start is idempotent and repository failure is diagnosed`() =
        runTest {
            val repeated =
                FakeCountdownDependencies(
                    optimisticResults = ArrayDeque(listOf(true, false)),
                )
            val handler = WidgetCountdownHandler { repeated }
            handler.handle(parameters(CheckSource.WIDGET_4X2)) { null }
            handler.handle(parameters(CheckSource.WIDGET_4X2)) { null }
            assertEquals(1, repeated.starts.size)

            val failed = FakeCountdownDependencies(startResult = false)
            val diagnostics = mutableListOf<String>()
            WidgetCountdownHandler(diagnostics::add) { failed }
                .handle(parameters(CheckSource.WIDGET_4X2)) { null }
            assertEquals(WidgetActionDiagnostic.REPOSITORY_WRITE_FAILED, diagnostics.last())
            assertEquals(1, failed.rejections)
            assertEquals(1, failed.recoveries)
        }

    @Test
    fun `checked running and unconfigured rows cannot start another timer`() =
        runTest {
            listOf(
                countdownSnapshot().copy(
                    rows = listOf(countdownSnapshot().rows.single().copy(isTaken = true)),
                ),
                countdownSnapshot().copy(
                    rows = listOf(countdownSnapshot().rows.single().copy(countdownMinutes = null)),
                ),
            ).forEach { snapshot ->
                val dependencies = FakeCountdownDependencies(snapshot = snapshot)
                val diagnostics = mutableListOf<String>()
                WidgetCountdownHandler(diagnostics::add) { dependencies }
                    .handle(parameters(CheckSource.WIDGET_4X2)) { null }
                assertEquals(WidgetActionDiagnostic.COUNTDOWN_UNAVAILABLE, diagnostics.last())
                assertTrue(dependencies.starts.isEmpty())
            }
        }

    private fun parameters(
        source: CheckSource,
        appWidgetId: Int? = null,
    ) = if (appWidgetId == null) {
        actionParametersOf(
            WidgetActionParameters.MEDICINE_ID to "medicine-a",
            WidgetActionParameters.SLOT to DoseSlot.AFTERNOON.wireValue,
            WidgetActionParameters.SOURCE to source.wireValue,
        )
    } else {
        actionParametersOf(
            WidgetActionParameters.MEDICINE_ID to "medicine-a",
            WidgetActionParameters.SLOT to DoseSlot.AFTERNOON.wireValue,
            WidgetActionParameters.SOURCE to source.wireValue,
            WidgetActionParameters.APP_WIDGET_ID to appWidgetId,
        )
    }
}

private data class CountdownStartCall(
    val source: CheckSource,
    val durationMinutes: Int,
    val medicine: Medicine,
)

private class FakeCountdownDependencies(
    override val currentUid: String? = "user-a",
    var snapshot: WidgetSnapshot = countdownSnapshot(),
    private val recoverySnapshot: WidgetSnapshot? = null,
    private val configuration: SingleWidgetConfiguration? =
        SingleWidgetConfiguration(41, "user-a", "medicine-a"),
    private val optimisticResults: ArrayDeque<Boolean> = ArrayDeque(listOf(true)),
    private val startResult: Boolean = true,
) : WidgetCountdownDependencies {
    override val startedAt: Instant = Instant.parse("2026-07-29T05:00:00Z")
    override val timezoneId: String = "Asia/Singapore"
    var refreshes = 0
    var snapshotReads = 0
    var recoveries = 0
    var widgetUpdates = 0
    var pendingSchedules = 0
    var refreshSchedules = 0
    var rejections = 0
    val starts = mutableListOf<CountdownStartCall>()

    override suspend fun refreshTemporalState() {
        refreshes += 1
    }

    override suspend fun configuration(id: Int): SingleWidgetConfiguration? = configuration

    override suspend fun readSnapshot(): WidgetSnapshot {
        snapshotReads += 1
        return snapshot
    }

    override suspend fun recoverFromRepositories() {
        recoveries += 1
        recoverySnapshot?.let { snapshot = it }
    }

    override suspend fun markStartedOptimistically(
        uid: String,
        logicalDay: LocalDate,
        medicineId: String,
        slot: DoseSlot,
        durationMinutes: Int,
        source: CheckSource,
        actionId: String,
        startedAt: Instant,
    ): Boolean = if (optimisticResults.isEmpty()) false else optimisticResults.removeFirst()

    override fun schedulePendingReconciliation() {
        pendingSchedules += 1
    }

    override suspend fun scheduleCountdownRefresh() {
        refreshSchedules += 1
    }

    override suspend fun updateWidgets() {
        widgetUpdates += 1
    }

    override suspend fun start(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
        actionId: String,
        startedAt: Instant,
        durationMinutes: Int,
    ): Boolean {
        starts += CountdownStartCall(source, durationMinutes, medicine)
        return startResult
    }

    override suspend fun markActionSubmitted(actionId: String) = Unit

    override suspend fun rejectOptimisticAction(
        uid: String,
        actionId: String,
        medicineId: String,
        slot: DoseSlot,
    ) {
        rejections += 1
    }
}

private fun countdownSnapshot(): WidgetSnapshot =
    WidgetSnapshot(
        ownerUid = "user-a",
        signedIn = true,
        logicalDay = LocalDate.of(2026, 7, 29),
        medicines =
            listOf(
                WidgetMedicine(
                    id = "medicine-a",
                    name = "Medicine A",
                    afternoonEnabled = true,
                    afternoonLabel = "After lunch",
                    nightEnabled = false,
                    nightLabel = "Night",
                    afternoonCountdownMinutes = 120,
                ),
            ),
        rows =
            listOf(
                WidgetDoseRow(
                    medicineId = "medicine-a",
                    medicineName = "Medicine A",
                    slot = DoseSlot.AFTERNOON,
                    label = "After lunch",
                    isTaken = false,
                    checkedAt = null,
                    countdownMinutes = 120,
                ),
            ),
    )
