package io.github.ffelixq.medswidget.widget

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.ffelixq.medswidget.data.CountdownWriteOutcome
import io.github.ffelixq.medswidget.data.DoseWriteOutcome
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownAction
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseIds
import io.github.ffelixq.medswidget.domain.DoseSlot
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetSnapshotTest {
    private lateinit var store: WidgetSnapshotStore
    private val day = LocalDate.of(2026, 7, 29)

    @Before
    fun setUp() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            store = WidgetSnapshotStore(context)
            store.write(WidgetSnapshot(logicalDay = day))
        }

    @Test
    fun `snapshot codec round trips all cache and sync fields`() {
        val snapshot = contentSnapshot().copy(isLoading = true)

        val decoded = WidgetSnapshotCodec.decode(WidgetSnapshotCodec.encode(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `snapshot codec safely falls back for malformed payload`() {
        val decoded = WidgetSnapshotCodec.decode("{not-json")

        assertFalse(decoded.signedIn)
        assertNull(decoded.ownerUid)
        assertTrue(decoded.medicines.isEmpty())
        assertTrue(decoded.rows.isEmpty())
    }

    @Test
    fun `snapshot codec drops an unsupported row slot without dropping valid content`() {
        val encoded =
            WidgetSnapshotCodec
                .encode(contentSnapshot())
                .replace("\"slot\":\"night\"", "\"slot\":\"morning\"")

        val decoded = WidgetSnapshotCodec.decode(encoded)

        assertEquals(1, decoded.rows.size)
        assertEquals(DoseSlot.AFTERNOON, decoded.rows.single().slot)
        assertEquals(1, decoded.medicines.size)
    }

    @Test
    fun `widget row converts to a deterministic domain row`() {
        val widgetRow = contentSnapshot().rows.first()

        val row = widgetRow.toDomain(day)

        assertEquals(widgetRow.medicineId, row.medicineId)
        assertEquals(widgetRow.medicineName, row.medicineName)
        assertEquals(widgetRow.slot, row.slot)
        assertEquals(widgetRow.label, row.label)
        assertEquals(widgetRow.isTaken, row.isTaken)
        assertEquals(widgetRow.checkedAt, row.checkedAt)
        assertEquals(widgetRow.checkedTimezone, row.checkedTimezone)
        assertEquals(DoseIds.stateId(day, widgetRow.medicineId, widgetRow.slot), row.stateId)
    }

    @Test
    fun `snapshot lookups keep each medicine and its rows isolated`() {
        val secondMedicine =
            WidgetMedicine(
                id = "medicine-b",
                name = "Medicine B",
                afternoonEnabled = false,
                afternoonLabel = "Afternoon",
                nightEnabled = true,
                nightLabel = "Sleep",
            )
        val snapshot =
            contentSnapshot().let {
                it.copy(
                    medicines = it.medicines + secondMedicine,
                    rows =
                        it.rows +
                            WidgetDoseRow(
                                medicineId = "medicine-b",
                                medicineName = "Medicine B",
                                slot = DoseSlot.NIGHT,
                                label = "Sleep",
                                isTaken = false,
                                checkedAt = null,
                            ),
                )
            }

        assertEquals("Medicine B", snapshot.medicine("medicine-b")?.name)
        assertEquals(2, snapshot.rowsForMedicine("medicine-a").size)
        assertEquals(1, snapshot.rowsForMedicine("medicine-b").size)
        assertNull(snapshot.medicine("missing"))
    }

    @Test
    fun `optimistic check updates once and repeated taps are idempotent`() =
        runTest {
            store.write(contentSnapshot())
            val checkedAt = Instant.parse("2026-07-29T13:15:30Z")

            val first =
                store.markTakenOptimistically(
                    expectedUid = "user-a",
                    medicineId = "medicine-a",
                    slot = DoseSlot.NIGHT,
                    checkedAt = checkedAt,
                    checkedTimezone = "Asia/Singapore",
                    actionId = "action-a",
                )
            val repeated =
                store.markTakenOptimistically(
                    expectedUid = "user-a",
                    medicineId = "medicine-a",
                    slot = DoseSlot.NIGHT,
                    checkedAt = checkedAt.plusSeconds(1),
                    checkedTimezone = "Pacific/Auckland",
                    actionId = "action-b",
                )

            val stored = store.read()
            val night = stored.rows.single { it.slot == DoseSlot.NIGHT }
            assertTrue(first)
            assertFalse(repeated)
            assertTrue(night.isTaken)
            assertEquals(checkedAt, night.checkedAt)
            assertEquals("Asia/Singapore", night.checkedTimezone)
            assertTrue(stored.hasPendingWrites)
            assertEquals(listOf("action-a"), stored.pendingActions.map(WidgetPendingAction::actionId))
        }

    @Test
    fun `optimistic countdown start is timestamp derived idempotent and cleared by dose check`() =
        runTest {
            val configured =
                contentSnapshot().let { snapshot ->
                    snapshot.copy(
                        rows =
                            snapshot.rows.map {
                                if (it.slot == DoseSlot.NIGHT) it.copy(countdownMinutes = 120) else it
                            },
                    )
                }
            store.write(configured)
            val startedAt = Instant.parse("2026-07-29T13:15:30Z")

            assertTrue(
                store.markCountdownStartedOptimistically(
                    expectedUid = "user-a",
                    medicineId = "medicine-a",
                    slot = DoseSlot.NIGHT,
                    logicalDay = day,
                    durationMinutes = 120,
                    startedAt = startedAt,
                    timezoneId = "Asia/Singapore",
                    source = CheckSource.WIDGET_2X2,
                    actionId = "countdown-a",
                ),
            )
            assertFalse(
                store.markCountdownStartedOptimistically(
                    "user-a",
                    "medicine-a",
                    DoseSlot.NIGHT,
                    day,
                    120,
                    startedAt.plusSeconds(1),
                    "Asia/Singapore",
                    CheckSource.WIDGET_2X2,
                    "countdown-b",
                ),
            )
            val running = store.read().rows.single { it.slot == DoseSlot.NIGHT }
            assertEquals(startedAt.plusSeconds(7_200), running.countdown?.targetAt)
            assertEquals(listOf("countdown-a"), store.read().pendingCountdownActions.map { it.actionId })

            assertTrue(
                store.markTakenOptimistically(
                    "user-a",
                    "medicine-a",
                    DoseSlot.NIGHT,
                    startedAt.plusSeconds(10),
                    "Asia/Singapore",
                    "dose-a",
                ),
            )
            assertNull(
                store
                    .read()
                    .rows
                    .single { it.slot == DoseSlot.NIGHT }
                    .countdown,
            )
        }

    @Test
    fun `failed countdown write rolls back only its correlated timer`() =
        runTest {
            val base =
                contentSnapshot().copy(
                    rows =
                        contentSnapshot().rows.map {
                            if (it.slot == DoseSlot.NIGHT) it.copy(countdownMinutes = 90) else it
                        },
                )
            store.write(base)
            store.markCountdownStartedOptimistically(
                "user-a",
                "medicine-a",
                DoseSlot.NIGHT,
                day,
                90,
                Instant.parse("2026-07-29T13:00:00Z"),
                "Asia/Singapore",
                CheckSource.WIDGET_4X2,
                "countdown-a",
            )

            assertTrue(
                store.resolveCountdownWriteOutcome(
                    CountdownWriteOutcome(
                        ownerUid = "user-a",
                        actionId = "countdown-a",
                        medicineId = "medicine-a",
                        slot = DoseSlot.NIGHT,
                        action = CountdownAction.START,
                        successful = false,
                        errorMessage = "Could not sync countdown.",
                    ),
                ),
            )
            val stored = store.read()
            assertNull(stored.rows.single { it.slot == DoseSlot.NIGHT }.countdown)
            assertTrue(stored.pendingCountdownActions.isEmpty())
            assertEquals("Could not sync countdown.", stored.errorMessage)
        }

    @Test
    fun `successful widget write outcome clears only its correlated pending state`() =
        runTest {
            store.write(contentSnapshot())
            store.markTakenOptimistically(
                expectedUid = "user-a",
                medicineId = "medicine-a",
                slot = DoseSlot.NIGHT,
                checkedAt = Instant.parse("2026-07-29T13:15:30Z"),
                checkedTimezone = "Asia/Singapore",
                actionId = "action-a",
            )

            val changed =
                store.resolveWriteOutcome(
                    DoseWriteOutcome(
                        ownerUid = "user-a",
                        actionId = "action-a",
                        medicineId = "medicine-a",
                        slot = DoseSlot.NIGHT,
                        action = DoseAction.CHECK,
                        successful = true,
                    ),
                )

            val stored = store.read()
            assertTrue(changed)
            assertTrue(stored.rows.single { it.slot == DoseSlot.NIGHT }.isTaken)
            assertTrue(stored.pendingActions.isEmpty())
            assertFalse(stored.hasPendingWrites)
            assertNull(stored.errorMessage)
        }

    @Test
    fun `repository snapshots preserve unresolved actions but never resurrect resolved ones`() =
        runTest {
            val base = contentSnapshot().copy(hasPendingWrites = false, errorMessage = null)
            store.write(base)
            store.markTakenOptimistically(
                expectedUid = "user-a",
                medicineId = "medicine-a",
                slot = DoseSlot.NIGHT,
                checkedAt = Instant.parse("2026-07-29T13:15:30Z"),
                checkedTimezone = "Asia/Singapore",
                actionId = "action-a",
            )

            store.writeRepositorySnapshot(base.copy(hasPendingWrites = true))
            val pending = store.read()
            assertEquals(listOf("action-a"), pending.pendingActions.map(WidgetPendingAction::actionId))
            assertTrue(pending.rows.single { it.slot == DoseSlot.NIGHT }.isTaken)

            store.resolveWriteOutcome(
                DoseWriteOutcome(
                    ownerUid = "user-a",
                    actionId = "action-a",
                    medicineId = "medicine-a",
                    slot = DoseSlot.NIGHT,
                    action = DoseAction.CHECK,
                    successful = true,
                ),
            )
            store.writeRepositorySnapshot(base)

            val stored = store.read()
            assertTrue(stored.pendingActions.isEmpty())
            assertFalse(stored.hasPendingWrites)
        }

    @Test
    fun `authoritative repository snapshot resolves a pending action after process recreation`() =
        runTest {
            val base = contentSnapshot().copy(hasPendingWrites = false, errorMessage = null)
            store.write(base)
            store.markTakenOptimistically(
                expectedUid = "user-a",
                medicineId = "medicine-a",
                slot = DoseSlot.NIGHT,
                checkedAt = Instant.parse("2026-07-29T13:15:30Z"),
                checkedTimezone = "Asia/Singapore",
                actionId = "action-before-process-death",
            )
            assertTrue(store.markActionSubmitted("action-before-process-death"))
            assertTrue(
                store
                    .read()
                    .pendingActions
                    .single()
                    .submitted,
            )

            store.writeRepositorySnapshot(
                base.copy(fromCache = true, hasPendingWrites = false),
                resolvePendingActions = true,
            )
            assertEquals(1, store.read().pendingActions.size)

            store.writeRepositorySnapshot(
                base.copy(fromCache = false, hasPendingWrites = false),
                resolvePendingActions = true,
            )

            val stored = store.read()
            assertTrue(stored.pendingActions.isEmpty())
            assertFalse(stored.hasPendingWrites)
            assertFalse(stored.rows.single { it.slot == DoseSlot.NIGHT }.isTaken)
        }

    @Test
    fun `normal listener projection cannot clear a created or submitted widget action`() =
        runTest {
            val base = contentSnapshot().copy(fromCache = false, hasPendingWrites = false)
            store.write(base)
            store.markTakenOptimistically(
                expectedUid = "user-a",
                medicineId = "medicine-a",
                slot = DoseSlot.NIGHT,
                checkedAt = Instant.parse("2026-07-29T13:15:30Z"),
                checkedTimezone = "Asia/Singapore",
                actionId = "action-a",
            )

            store.writeRepositorySnapshot(base)
            assertEquals(listOf("action-a"), store.read().pendingActions.map(WidgetPendingAction::actionId))

            store.markActionSubmitted("action-a")
            store.writeRepositorySnapshot(base)

            val stored = store.read()
            assertEquals(listOf("action-a"), stored.pendingActions.map(WidgetPendingAction::actionId))
            assertTrue(stored.rows.single { it.slot == DoseSlot.NIGHT }.isTaken)
        }

    @Test
    fun `unsubmitted orphan expires only after its grace boundary`() =
        runTest {
            val checkedAt = Instant.parse("2026-07-29T13:15:30Z")
            store.write(contentSnapshot().copy(hasPendingWrites = false, errorMessage = null))
            store.markTakenOptimistically(
                expectedUid = "user-a",
                medicineId = "medicine-a",
                slot = DoseSlot.NIGHT,
                checkedAt = checkedAt,
                checkedTimezone = "Asia/Singapore",
                actionId = "orphan",
            )

            store.writeRepositorySnapshot(
                contentSnapshot().copy(fromCache = false, hasPendingWrites = false, errorMessage = null),
                resolvePendingActions = true,
            )
            assertEquals(listOf("orphan"), store.read().pendingActions.map(WidgetPendingAction::actionId))
            assertFalse(store.expireUnsubmittedActions(checkedAt.minusMillis(1)))
            assertTrue(
                store
                    .read()
                    .rows
                    .single { it.slot == DoseSlot.NIGHT }
                    .isTaken,
            )

            assertTrue(store.expireUnsubmittedActions(checkedAt))
            val stored = store.read()
            assertTrue(stored.pendingActions.isEmpty())
            assertFalse(stored.rows.single { it.slot == DoseSlot.NIGHT }.isTaken)
            assertEquals("An interrupted widget check was not saved. Try again.", stored.errorMessage)
        }

    @Test
    fun `widget outcome does not erase unrelated repository pending state`() =
        runTest {
            val repositoryPending =
                contentSnapshot().copy(
                    fromCache = false,
                    hasPendingWrites = true,
                    repositoryHasPendingWrites = true,
                    errorMessage = null,
                )
            store.write(repositoryPending)
            store.markTakenOptimistically(
                expectedUid = "user-a",
                medicineId = "medicine-a",
                slot = DoseSlot.NIGHT,
                checkedAt = Instant.parse("2026-07-29T13:15:30Z"),
                checkedTimezone = "Asia/Singapore",
                actionId = "action-a",
            )

            store.resolveWriteOutcome(
                DoseWriteOutcome(
                    ownerUid = "user-a",
                    actionId = "action-a",
                    medicineId = "medicine-a",
                    slot = DoseSlot.NIGHT,
                    action = DoseAction.CHECK,
                    successful = true,
                ),
            )

            val stored = store.read()
            assertTrue(stored.pendingActions.isEmpty())
            assertTrue(stored.repositoryHasPendingWrites)
            assertTrue(stored.hasPendingWrites)
        }

    @Test
    fun `concurrent optimistic checks retain both pending correlations`() =
        runTest {
            val unchecked =
                contentSnapshot().let { snapshot ->
                    snapshot.copy(
                        rows =
                            snapshot.rows.map {
                                it.copy(
                                    isTaken = false,
                                    checkedAt = null,
                                    checkedTimezone = null,
                                )
                            },
                        hasPendingWrites = false,
                        errorMessage = null,
                    )
                }
            store.write(unchecked)

            val afternoon =
                async {
                    store.markTakenOptimistically(
                        expectedUid = "user-a",
                        medicineId = "medicine-a",
                        slot = DoseSlot.AFTERNOON,
                        checkedAt = Instant.parse("2026-07-29T05:00:00Z"),
                        checkedTimezone = "Asia/Singapore",
                        actionId = "action-afternoon",
                    )
                }
            val night =
                async {
                    store.markTakenOptimistically(
                        expectedUid = "user-a",
                        medicineId = "medicine-a",
                        slot = DoseSlot.NIGHT,
                        checkedAt = Instant.parse("2026-07-29T13:00:00Z"),
                        checkedTimezone = "Asia/Singapore",
                        actionId = "action-night",
                    )
                }

            assertTrue(afternoon.await())
            assertTrue(night.await())
            val stored = store.read()
            assertEquals(2, stored.rows.count(WidgetDoseRow::isTaken))
            assertEquals(
                setOf("action-afternoon", "action-night"),
                stored.pendingActions.map(WidgetPendingAction::actionId).toSet(),
            )
        }

    @Test
    fun `failed widget write outcome rolls back cached check and ignores stale outcomes`() =
        runTest {
            store.write(contentSnapshot())
            store.markTakenOptimistically(
                expectedUid = "user-a",
                medicineId = "medicine-a",
                slot = DoseSlot.NIGHT,
                checkedAt = Instant.parse("2026-07-29T13:15:30Z"),
                checkedTimezone = "Asia/Singapore",
                actionId = "action-a",
            )
            val stale =
                DoseWriteOutcome(
                    ownerUid = "user-a",
                    actionId = "stale-action",
                    medicineId = "medicine-a",
                    slot = DoseSlot.NIGHT,
                    action = DoseAction.CHECK,
                    successful = false,
                    errorMessage = "Could not sync.",
                )

            assertFalse(store.resolveWriteOutcome(stale))
            assertTrue(
                store
                    .read()
                    .rows
                    .single { it.slot == DoseSlot.NIGHT }
                    .isTaken,
            )

            val changed = store.resolveWriteOutcome(stale.copy(actionId = "action-a"))

            val stored = store.read()
            assertTrue(changed)
            val night = stored.rows.single { it.slot == DoseSlot.NIGHT }
            assertFalse(night.isTaken)
            assertNull(night.checkedAt)
            assertNull(night.checkedTimezone)
            assertTrue(stored.pendingActions.isEmpty())
            assertFalse(stored.hasPendingWrites)
            assertEquals("Could not sync.", stored.errorMessage)
        }

    @Test
    fun `optimistic check rejects wrong account missing medicine and signed-out state`() =
        runTest {
            store.write(contentSnapshot())
            val now = Instant.parse("2026-07-29T13:15:30Z")

            assertFalse(
                store.markTakenOptimistically(
                    "user-b",
                    "medicine-a",
                    DoseSlot.NIGHT,
                    now,
                    "Asia/Singapore",
                    "action-wrong-account",
                ),
            )
            assertFalse(
                store.markTakenOptimistically(
                    "user-a",
                    "missing",
                    DoseSlot.NIGHT,
                    now,
                    "Asia/Singapore",
                    "action-missing",
                ),
            )

            store.clearAccount()
            assertFalse(
                store.markTakenOptimistically(
                    "user-a",
                    "medicine-a",
                    DoseSlot.NIGHT,
                    now,
                    "Asia/Singapore",
                    "action-signed-out",
                ),
            )
        }

    @Test
    fun `logical day rollover rebuilds enabled rows as unchecked and retains labels`() =
        runTest {
            store.write(contentSnapshot())
            val nextDay = day.plusDays(1)

            store.rollToLogicalDay(nextDay)

            val rolled = store.read()
            assertEquals(nextDay, rolled.logicalDay)
            assertEquals(2, rolled.rows.size)
            assertTrue(rolled.rows.none(WidgetDoseRow::isTaken))
            assertTrue(rolled.rows.all { it.checkedAt == null })
            assertTrue(rolled.rows.all { it.checkedTimezone == null })
            assertEquals(listOf("After lunch", "Before bed"), rolled.rows.map(WidgetDoseRow::label))
            assertFalse(rolled.hasPendingWrites)
        }

    @Test
    fun `same-day rollover is a no-op and preserves checked state`() =
        runTest {
            val original = contentSnapshot()
            store.write(original)

            val changed = store.rollToLogicalDay(day)

            assertFalse(changed)
            assertEquals(original, store.read())
        }

    @Test
    fun `changed-day rollover reports that temporal state changed`() =
        runTest {
            store.write(contentSnapshot())

            val changed = store.rollToLogicalDay(day.plusDays(1))

            assertTrue(changed)
        }

    @Test
    fun `compact status prioritizes loading syncing then cached and otherwise stays absent`() {
        assertEquals(
            "Loading",
            WidgetSnapshot(isLoading = true, hasPendingWrites = true).compactStatus(),
        )
        assertEquals(
            "Syncing",
            WidgetSnapshot(hasPendingWrites = true, fromCache = true, errorMessage = "Offline").compactStatus(),
        )
        assertEquals("Cached", WidgetSnapshot(fromCache = true).compactStatus())
        assertEquals("Cached", WidgetSnapshot(errorMessage = "Offline").compactStatus())
        assertNull(WidgetSnapshot().compactStatus())
    }

    @Test
    fun `clearing an account removes all account-specific widget content`() =
        runTest {
            store.write(contentSnapshot())

            store.clearAccount()

            val cleared = store.read()
            assertFalse(cleared.signedIn)
            assertNull(cleared.ownerUid)
            assertTrue(cleared.medicines.isEmpty())
            assertTrue(cleared.rows.isEmpty())
            assertFalse(cleared.hasPendingWrites)
        }

    @Test
    fun `live authentication identity clears stale widget content before rendering`() =
        runTest {
            store.write(contentSnapshot())

            assertTrue(store.secureForSession(activeUid = null, logicalDay = day))
            val signedOut = store.read()
            assertFalse(signedOut.signedIn)
            assertNull(signedOut.ownerUid)
            assertTrue(signedOut.rows.isEmpty())

            store.write(contentSnapshot())
            assertTrue(store.secureForSession(activeUid = "user-b", logicalDay = day))
            val switched = store.read()
            assertTrue(switched.signedIn)
            assertEquals("user-b", switched.ownerUid)
            assertTrue(switched.isLoading)
            assertTrue(switched.medicines.isEmpty())
            assertTrue(switched.rows.isEmpty())
            assertFalse(store.secureForSession(activeUid = "user-b", logicalDay = day))
        }

    private fun contentSnapshot(): WidgetSnapshot =
        WidgetSnapshot(
            ownerUid = "user-a",
            signedIn = true,
            logicalDay = day,
            medicines =
                listOf(
                    WidgetMedicine(
                        id = "medicine-a",
                        name = "Medicine A",
                        afternoonEnabled = true,
                        afternoonLabel = "After lunch",
                        nightEnabled = true,
                        nightLabel = "Before bed",
                        nightCountdownMinutes = 120,
                    ),
                ),
            rows =
                listOf(
                    WidgetDoseRow(
                        medicineId = "medicine-a",
                        medicineName = "Medicine A",
                        slot = DoseSlot.AFTERNOON,
                        label = "After lunch",
                        isTaken = true,
                        checkedAt = Instant.parse("2026-07-29T05:00:00Z"),
                        checkedTimezone = "Asia/Singapore",
                    ),
                    WidgetDoseRow(
                        medicineId = "medicine-a",
                        medicineName = "Medicine A",
                        slot = DoseSlot.NIGHT,
                        label = "Before bed",
                        isTaken = false,
                        checkedAt = null,
                        countdownMinutes = 120,
                    ),
                ),
            fromCache = true,
            hasPendingWrites = true,
            errorMessage = "Offline",
        )
}
