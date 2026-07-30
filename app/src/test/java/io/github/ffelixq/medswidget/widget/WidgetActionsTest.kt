package io.github.ffelixq.medswidget.widget

import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.Medicine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.ArrayDeque

class WidgetActionsTest {
    @Test
    fun `guarded exits emit safe diagnostic reason codes`() =
        runTest {
            assertEquals(
                WidgetActionDiagnostic.INVALID_PARAMETERS,
                diagnosticCodes(
                    dependencies = FakeWidgetCheckDependencies(),
                    parameters =
                        actionParametersOf(
                            WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
                            WidgetActionParameters.SOURCE to CheckSource.WIDGET_4X2.wireValue,
                        ),
                ).last(),
            )
            assertEquals(
                WidgetActionDiagnostic.WIDGET_ID_MISMATCH,
                diagnosticCodes(
                    dependencies = FakeWidgetCheckDependencies(),
                    parameters = twoByTwoParameters(),
                    resolvedWidgetId = APP_WIDGET_ID + 1,
                ).last(),
            )
            assertEquals(
                WidgetActionDiagnostic.AUTH_UNAVAILABLE,
                diagnosticCodes(FakeWidgetCheckDependencies(currentUid = null)).last(),
            )
            assertEquals(
                WidgetActionDiagnostic.CONFIGURATION_INVALID,
                diagnosticCodes(
                    dependencies = FakeWidgetCheckDependencies(configuration = null),
                    parameters = twoByTwoParameters(),
                ).last(),
            )
            assertEquals(
                WidgetActionDiagnostic.SNAPSHOT_MISSING,
                diagnosticCodes(
                    FakeWidgetCheckDependencies(snapshot = contentSnapshot().copy(signedIn = false)),
                ).last(),
            )
            assertEquals(
                WidgetActionDiagnostic.MEDICINE_INELIGIBLE,
                diagnosticCodes(
                    FakeWidgetCheckDependencies(snapshot = contentSnapshot().copy(medicines = emptyList())),
                ).last(),
            )
            assertEquals(
                WidgetActionDiagnostic.OPTIMISTIC_UPDATE_REJECTED,
                diagnosticCodes(
                    FakeWidgetCheckDependencies(
                        optimisticResults = ArrayDeque(listOf(false)),
                    ),
                ).last(),
            )
            assertEquals(
                WidgetActionDiagnostic.REPOSITORY_WRITE_FAILED,
                diagnosticCodes(FakeWidgetCheckDependencies(checkResult = false)).last(),
            )
            assertEquals(
                WidgetActionDiagnostic.REPOSITORY_WRITE_SUCCEEDED,
                diagnosticCodes(FakeWidgetCheckDependencies()).last(),
            )
        }

    @Test
    fun `missing cached snapshot recovers once and then applies the check`() =
        runTest {
            val dependencies =
                FakeWidgetCheckDependencies(
                    snapshot = contentSnapshot().copy(signedIn = false),
                    recoverySnapshot = contentSnapshot(),
                )

            WidgetCheckHandler { dependencies }.handle(fourByTwoParameters()) {
                error("The all-medicines action does not resolve a single-widget ID")
            }

            assertEquals(1, dependencies.recoveryCalls)
            assertEquals(2, dependencies.snapshotReads)
            assertEquals(1, dependencies.optimisticCalls)
            assertEquals(1, dependencies.checkCalls.size)
        }

    @Test
    fun `valid all-medicines action applies an optimistic widget check`() =
        runTest {
            val dependencies = FakeWidgetCheckDependencies()

            WidgetCheckHandler { dependencies }.handle(fourByTwoParameters()) {
                error("The all-medicines action does not resolve a single-widget ID")
            }

            assertEquals(1, dependencies.optimisticCalls)
            assertEquals(1, dependencies.widgetUpdateCalls)
            assertEquals(CheckSource.WIDGET_4X2, dependencies.checkCalls.single().source)
            assertEquals(1, dependencies.submittedActionIds.size)
        }

    @Test
    fun `malformed and unsupported parameters stop before dependencies are created`() =
        runTest {
            val dependencies = FakeWidgetCheckDependencies()
            var dependencyCreations = 0
            val handler =
                WidgetCheckHandler {
                    dependencyCreations += 1
                    dependencies
                }
            val malformed =
                listOf(
                    actionParametersOf(
                        WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
                        WidgetActionParameters.SOURCE to CheckSource.WIDGET_4X2.wireValue,
                    ),
                    actionParametersOf(
                        WidgetActionParameters.MEDICINE_ID to "",
                        WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
                        WidgetActionParameters.SOURCE to CheckSource.WIDGET_4X2.wireValue,
                    ),
                    actionParametersOf(
                        WidgetActionParameters.MEDICINE_ID to MEDICINE_ID,
                        WidgetActionParameters.SLOT to "morning",
                        WidgetActionParameters.SOURCE to CheckSource.WIDGET_4X2.wireValue,
                    ),
                    actionParametersOf(
                        WidgetActionParameters.MEDICINE_ID to MEDICINE_ID,
                        WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
                        WidgetActionParameters.SOURCE to CheckSource.APP.wireValue,
                    ),
                    actionParametersOf(
                        WidgetActionParameters.MEDICINE_ID to MEDICINE_ID,
                        WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
                        WidgetActionParameters.SOURCE to CheckSource.WIDGET_2X2.wireValue,
                    ),
                    actionParametersOf(
                        WidgetActionParameters.MEDICINE_ID to MEDICINE_ID,
                        WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
                        WidgetActionParameters.SOURCE to CheckSource.WIDGET_2X2.wireValue,
                        WidgetActionParameters.APP_WIDGET_ID to -1,
                    ),
                )

            malformed.forEach { parameters ->
                handler.handle(parameters) { error("Malformed input must not resolve a widget ID") }
            }

            assertEquals(0, dependencyCreations)
            assertEquals(0, dependencies.refreshCalls)
        }

    @Test
    fun `signed-out and cached-account mismatch never mutate the snapshot`() =
        runTest {
            val signedOut = FakeWidgetCheckDependencies(currentUid = null)
            WidgetCheckHandler { signedOut }.handle(fourByTwoParameters()) {
                error("The all-medicines action does not resolve a single-widget ID")
            }

            assertEquals(1, signedOut.refreshCalls)
            assertEquals(0, signedOut.snapshotReads)
            assertEquals(0, signedOut.optimisticCalls)

            listOf(
                contentSnapshot().copy(signedIn = false),
                contentSnapshot().copy(ownerUid = "user-b"),
            ).forEach { mismatchedSnapshot ->
                val mismatched = FakeWidgetCheckDependencies(snapshot = mismatchedSnapshot)
                WidgetCheckHandler { mismatched }.handle(fourByTwoParameters()) {
                    error("The all-medicines action does not resolve a single-widget ID")
                }

                assertEquals(2, mismatched.snapshotReads)
                assertEquals(1, mismatched.recoveryCalls)
                assertEquals(0, mismatched.optimisticCalls)
                assertEquals(0, mismatched.checkCalls.size)
            }
        }

    @Test
    fun `single-widget identity and stored configuration must match the request`() =
        runTest {
            val wrongGlanceId = FakeWidgetCheckDependencies()
            var dependencyCreations = 0
            WidgetCheckHandler {
                dependencyCreations += 1
                wrongGlanceId
            }.handle(twoByTwoParameters()) { APP_WIDGET_ID + 1 }

            assertEquals(0, dependencyCreations)

            val invalidConfigurations =
                listOf(
                    null,
                    SingleWidgetConfiguration(APP_WIDGET_ID, "user-b", MEDICINE_ID),
                    SingleWidgetConfiguration(APP_WIDGET_ID, "user-a", "medicine-b"),
                )
            invalidConfigurations.forEach { configuration ->
                val dependencies = FakeWidgetCheckDependencies(configuration = configuration)

                WidgetCheckHandler { dependencies }.handle(twoByTwoParameters()) {
                    APP_WIDGET_ID
                }

                assertEquals(listOf(APP_WIDGET_ID), dependencies.configurationCalls)
                assertEquals(0, dependencies.snapshotReads)
                assertEquals(0, dependencies.optimisticCalls)
            }
        }

    @Test
    fun `optimistic repeated tap is idempotent and synchronises only once`() =
        runTest {
            val dependencies =
                FakeWidgetCheckDependencies(
                    configuration =
                        SingleWidgetConfiguration(
                            APP_WIDGET_ID,
                            "user-a",
                            MEDICINE_ID,
                        ),
                    optimisticResults = ArrayDeque(listOf(true, false)),
                )
            val handler = WidgetCheckHandler { dependencies }

            handler.handle(twoByTwoParameters()) { APP_WIDGET_ID }
            handler.handle(twoByTwoParameters()) { APP_WIDGET_ID }

            assertEquals(2, dependencies.refreshCalls)
            assertEquals(2, dependencies.optimisticCalls)
            assertEquals(1, dependencies.widgetUpdateCalls)
            assertEquals(1, dependencies.pendingReconciliationCalls)
            assertEquals(1, dependencies.checkCalls.size)
            assertEquals(CheckSource.WIDGET_2X2, dependencies.checkCalls.single().source)
            assertEquals(
                listOf("optimistic", "schedule", "render", "check", "submitted", "optimistic"),
                dependencies.callOrder,
            )
            assertEquals(CHECKED_AT, dependencies.lastOptimisticCheckedAt)
            assertEquals("Asia/Singapore", dependencies.lastOptimisticTimezone)
            assertEquals(dependencies.optimisticActionIds.first(), dependencies.checkCalls.single().actionId)
            assertEquals(
                dependencies.optimisticActionIds.first(),
                dependencies.submittedActionIds.single(),
            )
            assertEquals(CHECKED_AT, dependencies.checkCalls.single().occurredAt)
            assertEquals(0, dependencies.recoveryCalls)
        }

    @Test
    fun `repository rejection and exception both recover from authoritative data`() =
        runTest {
            val rejected = FakeWidgetCheckDependencies(checkResult = false)
            WidgetCheckHandler { rejected }.handle(fourByTwoParameters()) {
                error("The all-medicines action does not resolve a single-widget ID")
            }

            val failed =
                FakeWidgetCheckDependencies(
                    checkFailure = IllegalStateException("offline write failed"),
                )
            WidgetCheckHandler { failed }.handle(fourByTwoParameters()) {
                error("The all-medicines action does not resolve a single-widget ID")
            }

            listOf(rejected, failed).forEach { dependencies ->
                assertEquals(1, dependencies.widgetUpdateCalls)
                assertEquals(1, dependencies.pendingReconciliationCalls)
                assertEquals(1, dependencies.checkCalls.size)
                assertEquals(1, dependencies.rejectedActionIds.size)
                assertEquals(
                    dependencies.optimisticActionIds.single(),
                    dependencies.rejectedActionIds.single(),
                )
                assertEquals(1, dependencies.recoveryCalls)
                assertEquals(0, dependencies.submittedActionIds.size)
                assertEquals(
                    listOf("optimistic", "schedule", "render", "check", "reject", "recover"),
                    dependencies.callOrder,
                )
            }
        }

    @Test
    fun `enqueue and rendering failures reject optimistic state before submission`() =
        runTest {
            val enqueueFailure = IllegalStateException("enqueue failed")
            val enqueueFailed =
                FakeWidgetCheckDependencies(
                    pendingReconciliationFailure = enqueueFailure,
                )

            assertEquals(
                enqueueFailure,
                failureFrom {
                    WidgetCheckHandler { enqueueFailed }.handle(fourByTwoParameters()) {
                        error("The all-medicines action does not resolve a single-widget ID")
                    }
                },
            )
            assertEquals(0, enqueueFailed.widgetUpdateCalls)
            enqueueFailed.assertPreSubmissionFailure(
                listOf("optimistic", "schedule", "reject", "recover"),
            )

            val renderingFailure = IllegalStateException("render failed")
            val renderingFailed =
                FakeWidgetCheckDependencies(
                    widgetUpdateFailure = renderingFailure,
                )

            assertEquals(
                renderingFailure,
                failureFrom {
                    WidgetCheckHandler { renderingFailed }.handle(fourByTwoParameters()) {
                        error("The all-medicines action does not resolve a single-widget ID")
                    }
                },
            )
            assertEquals(1, renderingFailed.pendingReconciliationCalls)
            renderingFailed.assertPreSubmissionFailure(
                listOf("optimistic", "schedule", "render", "reject", "recover"),
            )
        }

    @Test
    fun `rendering and repository cancellation clean up non-cancellably then rethrow`() =
        runTest {
            val renderingCancelled =
                FakeWidgetCheckDependencies(
                    widgetUpdateFailure = CancellationException("render cancelled"),
                )
            val renderingFailure =
                failureFrom {
                    WidgetCheckHandler { renderingCancelled }.handle(fourByTwoParameters()) {
                        error("The all-medicines action does not resolve a single-widget ID")
                    }
                }

            assertTrue(renderingFailure is CancellationException)
            assertEquals("render cancelled", renderingFailure?.message)
            renderingCancelled.assertPreSubmissionFailure(
                listOf("optimistic", "schedule", "render", "reject", "recover"),
            )

            val repositoryCancelled =
                FakeWidgetCheckDependencies(
                    checkFailure = CancellationException("check cancelled"),
                )
            val repositoryFailure =
                failureFrom {
                    WidgetCheckHandler { repositoryCancelled }.handle(fourByTwoParameters()) {
                        error("The all-medicines action does not resolve a single-widget ID")
                    }
                }

            assertTrue(repositoryFailure is CancellationException)
            assertEquals("check cancelled", repositoryFailure?.message)
            repositoryCancelled.assertPreSubmissionFailure(
                listOf("optimistic", "schedule", "render", "check", "reject", "recover"),
            )
        }

    private suspend fun failureFrom(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (failure: Throwable) {
            failure
        }

    private suspend fun diagnosticCodes(
        dependencies: FakeWidgetCheckDependencies,
        parameters: ActionParameters = fourByTwoParameters(),
        resolvedWidgetId: Int? = APP_WIDGET_ID,
    ): List<String> {
        val diagnostics = mutableListOf<String>()
        WidgetCheckHandler(
            dependencies = { dependencies },
            recordDiagnostic = diagnostics::add,
        ).handle(parameters) {
            resolvedWidgetId
        }
        return diagnostics
    }

    private fun FakeWidgetCheckDependencies.assertPreSubmissionFailure(expectedCallOrder: List<String>) {
        assertEquals(if ("check" in expectedCallOrder) 1 else 0, checkCalls.size)
        assertEquals(0, submittedActionIds.size)
        assertEquals(1, rejectedActionIds.size)
        assertEquals(optimisticActionIds.single(), rejectedActionIds.single())
        assertEquals(1, recoveryCalls)
        assertEquals(expectedCallOrder, callOrder)
    }

    private fun fourByTwoParameters(): ActionParameters =
        actionParametersOf(
            WidgetActionParameters.MEDICINE_ID to MEDICINE_ID,
            WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
            WidgetActionParameters.SOURCE to CheckSource.WIDGET_4X2.wireValue,
        )

    private fun twoByTwoParameters(): ActionParameters =
        actionParametersOf(
            WidgetActionParameters.MEDICINE_ID to MEDICINE_ID,
            WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
            WidgetActionParameters.SOURCE to CheckSource.WIDGET_2X2.wireValue,
            WidgetActionParameters.APP_WIDGET_ID to APP_WIDGET_ID,
        )

    private data class CheckCall(
        val uid: String,
        val logicalDay: LocalDate,
        val medicine: Medicine,
        val slot: DoseSlot,
        val source: CheckSource,
        val actionId: String,
        val occurredAt: Instant,
    )

    private class FakeWidgetCheckDependencies(
        override val currentUid: String? = "user-a",
        private var snapshot: WidgetSnapshot = contentSnapshot(),
        private val recoverySnapshot: WidgetSnapshot? = null,
        private val configuration: SingleWidgetConfiguration? = null,
        private val optimisticResults: ArrayDeque<Boolean> = ArrayDeque(listOf(true)),
        private val checkResult: Boolean = true,
        private val checkFailure: Throwable? = null,
        private val widgetUpdateFailure: Throwable? = null,
        private val pendingReconciliationFailure: Throwable? = null,
    ) : WidgetCheckDependencies {
        override val checkedAt: Instant = CHECKED_AT
        override val timezoneId: String = "Asia/Singapore"
        var refreshCalls = 0
        var snapshotReads = 0
        var optimisticCalls = 0
        var widgetUpdateCalls = 0
        var pendingReconciliationCalls = 0
        var recoveryCalls = 0
        var lastOptimisticCheckedAt: Instant? = null
        var lastOptimisticTimezone: String? = null
        val optimisticActionIds = mutableListOf<String>()
        val configurationCalls = mutableListOf<Int>()
        val checkCalls = mutableListOf<CheckCall>()
        val rejectedActionIds = mutableListOf<String>()
        val submittedActionIds = mutableListOf<String>()
        val callOrder = mutableListOf<String>()

        override suspend fun refreshTemporalState() {
            refreshCalls += 1
        }

        override suspend fun configuration(id: Int): SingleWidgetConfiguration? {
            configurationCalls += id
            return configuration
        }

        override suspend fun readSnapshot(): WidgetSnapshot {
            snapshotReads += 1
            return snapshot
        }

        override suspend fun markTakenOptimistically(
            uid: String,
            medicineId: String,
            slot: DoseSlot,
            checkedAt: Instant,
            timezoneId: String,
            actionId: String,
        ): Boolean {
            optimisticCalls += 1
            callOrder += "optimistic"
            lastOptimisticCheckedAt = checkedAt
            lastOptimisticTimezone = timezoneId
            optimisticActionIds += actionId
            assertEquals("user-a", uid)
            assertEquals(MEDICINE_ID, medicineId)
            assertEquals(DoseSlot.NIGHT, slot)
            return optimisticResults.removeFirst()
        }

        override suspend fun updateWidgets() {
            widgetUpdateCalls += 1
            callOrder += "render"
            widgetUpdateFailure?.let { throw it }
        }

        override fun schedulePendingReconciliation() {
            pendingReconciliationCalls += 1
            callOrder += "schedule"
            pendingReconciliationFailure?.let { throw it }
        }

        override suspend fun markActionSubmitted(actionId: String) {
            callOrder += "submitted"
            submittedActionIds += actionId
        }

        override suspend fun check(
            uid: String,
            logicalDay: LocalDate,
            medicine: Medicine,
            slot: DoseSlot,
            source: CheckSource,
            actionId: String,
            occurredAt: Instant,
        ): Boolean {
            callOrder += "check"
            checkCalls += CheckCall(uid, logicalDay, medicine, slot, source, actionId, occurredAt)
            checkFailure?.let { throw it }
            return checkResult
        }

        override suspend fun rejectOptimisticAction(
            uid: String,
            actionId: String,
            medicineId: String,
            slot: DoseSlot,
        ) {
            assertEquals("user-a", uid)
            assertEquals(MEDICINE_ID, medicineId)
            assertEquals(DoseSlot.NIGHT, slot)
            callOrder += "reject"
            rejectedActionIds += actionId
        }

        override suspend fun recoverFromRepositories() {
            callOrder += "recover"
            recoveryCalls += 1
            recoverySnapshot?.let { snapshot = it }
        }
    }

    private companion object {
        const val APP_WIDGET_ID = 41
        const val MEDICINE_ID = "medicine-a"
        val CHECKED_AT: Instant = Instant.parse("2026-07-29T13:15:30Z")
        val LOGICAL_DAY: LocalDate = LocalDate.of(2026, 7, 29)
    }
}

private fun contentSnapshot(): WidgetSnapshot =
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
                    nightEnabled = true,
                    nightLabel = "Before bed",
                ),
            ),
    )
