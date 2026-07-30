package io.github.ffelixq.medswidget.ui

import io.github.ffelixq.medswidget.AccountDaySnapshot
import io.github.ffelixq.medswidget.domain.AuthSession
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DataEnvelope
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.MedicineDraft
import io.github.ffelixq.medswidget.domain.UserSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("Asia/Singapore")
    private val clock = Clock.fixed(Instant.parse("2026-07-29T05:00:00Z"), zone)
    private val day = LocalDate.of(2026, 7, 29)
    private lateinit var previousTimeZone: TimeZone

    @Before
    fun setUpTimeZone() {
        previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(previousTimeZone)
    }

    @Test
    fun `signed-out state contains no prior account data`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = null, clock = clock)
            fakes.medicines.emit("user-a", DataEnvelope(listOf(medicine("a", "Private medicine"))))
            val tracker = RefreshTracker()
            publish(fakes, tracker, "user-a", day)
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)

            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertTrue(
                viewModel.state.value.medicines
                    .isEmpty(),
            )
            assertTrue(
                viewModel.state.value.rows
                    .isEmpty(),
            )
            assertEquals("Sign in to view medicines.", viewModel.state.value.errorMessage)
        }

    @Test
    fun `create edit and archive medicine update state through repository fakes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = session("user-a"), clock = clock)
            val tracker = RefreshTracker()
            publish(fakes, tracker, "user-a", day)
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)
            advanceUntilIdle()

            val createResult =
                viewModel.saveMedicine(
                    MedicineDraft(
                        name = "  Medicine A ",
                        afternoonEnabled = true,
                        afternoonLabel = " After lunch ",
                        nightEnabled = false,
                        nightLabel = "",
                    ),
                )
            publish(fakes, tracker, "user-a", day)
            advanceUntilIdle()

            assertTrue(createResult.isValid)
            assertEquals(
                "Medicine A",
                fakes.medicines.saveCalls
                    .single()
                    .draft.name,
            )
            assertEquals(1, viewModel.state.value.medicines.size)
            assertEquals(
                listOf("After lunch"),
                viewModel.state.value.rows
                    .map { it.label },
            )

            val medicineId =
                viewModel.state.value.medicines
                    .single()
                    .id
            val editResult =
                viewModel.saveMedicine(
                    MedicineDraft(
                        id = medicineId,
                        name = "Medicine A edited",
                        afternoonEnabled = false,
                        afternoonLabel = "",
                        nightEnabled = true,
                        nightLabel = "Before bed",
                    ),
                )
            publish(fakes, tracker, "user-a", day)
            advanceUntilIdle()

            assertTrue(editResult.isValid)
            assertEquals(
                "Medicine A edited",
                viewModel.state.value.medicines
                    .single()
                    .name,
            )
            assertEquals(
                DoseSlot.NIGHT,
                viewModel.state.value.rows
                    .single()
                    .slot,
            )

            viewModel.archiveMedicine(medicineId)
            advanceUntilIdle()
            publish(fakes, tracker, "user-a", day)
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.medicines
                    .isEmpty(),
            )
            assertTrue(
                viewModel.state.value.rows
                    .isEmpty(),
            )
            assertEquals(3, tracker.repositoryRefreshes)
            assertEquals(Triple("user-a", medicineId, true), fakes.medicines.archiveCalls.single())
        }

    @Test
    fun `invalid medicine never reaches the repository or widget refresh path`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = session("user-a"), clock = clock)
            val tracker = RefreshTracker()
            val viewModel = viewModel(fakes, tracker)

            val result =
                viewModel.saveMedicine(
                    MedicineDraft(name = " ", afternoonEnabled = false, nightEnabled = false),
                )

            assertFalse(result.isValid)
            assertTrue(fakes.medicines.saveCalls.isEmpty())
            assertEquals(0, tracker.repositoryRefreshes)
        }

    @Test
    fun `delete medicine removes it and requests widget snapshot refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = session("user-a"), clock = clock)
            fakes.medicines.emit("user-a", DataEnvelope(listOf(medicine("a", "Medicine A"))))
            val tracker = RefreshTracker()
            publish(fakes, tracker, "user-a", day)
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)
            advanceUntilIdle()

            viewModel.deleteMedicine("a")
            advanceUntilIdle()
            publish(fakes, tracker, "user-a", day)
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.medicines
                    .isEmpty(),
            )
            assertEquals(listOf("user-a" to "a"), fakes.medicines.deleteCalls)
            assertEquals(1, tracker.repositoryRefreshes)
        }

    @Test
    fun `check and app-only undo update rows history and refresh requests`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = session("user-a"), clock = clock)
            fakes.medicines.emit("user-a", DataEnvelope(listOf(medicine("a", "Medicine A"))))
            val tracker = RefreshTracker()
            publish(fakes, tracker, "user-a", day)
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)
            advanceUntilIdle()
            val unchecked =
                viewModel.state.value.rows
                    .first { it.slot == DoseSlot.AFTERNOON }

            viewModel.check(unchecked, CheckSource.APP_PREVIEW)
            advanceUntilIdle()
            publish(fakes, tracker, "user-a", day)
            advanceUntilIdle()

            val checked =
                viewModel.state.value.rows.first {
                    it.medicineId == unchecked.medicineId && it.slot == unchecked.slot
                }
            assertTrue(checked.isTaken)
            assertEquals(1, viewModel.state.value.progress.completed)
            assertEquals(
                CheckSource.APP_PREVIEW,
                fakes.doses.checkCalls
                    .single()
                    .source,
            )

            viewModel.undo(checked)
            advanceUntilIdle()
            publish(fakes, tracker, "user-a", day)
            advanceUntilIdle()

            val undone =
                viewModel.state.value.rows.first {
                    it.medicineId == unchecked.medicineId && it.slot == unchecked.slot
                }
            assertFalse(undone.isTaken)
            assertEquals(
                CheckSource.APP,
                fakes.doses.undoCalls
                    .single()
                    .source,
            )
            assertEquals(2, tracker.repositoryRefreshes)
            assertEquals(
                listOf(DoseAction.CHECK, DoseAction.UNDO),
                fakes.doses.events("user-a").map { it.action },
            )
            assertEquals(
                "Medicine A",
                fakes.doses
                    .events("user-a")
                    .first()
                    .medicineNameSnapshot,
            )
        }

    @Test
    fun `cached offline and pending sync metadata remain visible without crashing`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = session("user-a"), clock = clock)
            fakes.medicines.emit(
                "user-a",
                DataEnvelope(
                    value = listOf(medicine("a", "Cached medicine")),
                    fromCache = true,
                    errorMessage = "Medicine listener offline",
                ),
            )
            fakes.doses.emit(
                "user-a",
                day,
                DataEnvelope(
                    value = emptyList(),
                    hasPendingWrites = true,
                    errorMessage = "Dose sync waiting for network",
                ),
            )
            val tracker = RefreshTracker()
            publish(fakes, tracker, "user-a", day)
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)

            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertTrue(viewModel.state.value.isCached)
            assertTrue(viewModel.state.value.hasPendingWrites)
            assertEquals("Dose sync waiting for network", viewModel.state.value.errorMessage)
            assertEquals(
                "Cached medicine",
                viewModel.state.value.medicines
                    .single()
                    .name,
            )
        }

    @Test
    fun `account switch replaces shared snapshot and never exposes previous account medicines`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = session("user-a"), clock = clock)
            fakes.medicines.emit(
                "user-a",
                DataEnvelope(listOf(medicine("a", "User A medicine", "user-a"))),
            )
            fakes.medicines.emit(
                "user-b",
                DataEnvelope(listOf(medicine("b", "User B medicine", "user-b"))),
            )
            val tracker = RefreshTracker()
            publish(fakes, tracker, "user-a", day)
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)
            advanceUntilIdle()
            assertEquals(
                listOf("User A medicine"),
                viewModel.state.value.medicines
                    .map(Medicine::name),
            )

            fakes.auth.session.value = session("user-b")
            publish(fakes, tracker, "user-b", day)
            advanceUntilIdle()

            assertEquals(
                listOf("User B medicine"),
                viewModel.state.value.medicines
                    .map(Medicine::name),
            )
            assertTrue(
                viewModel.state.value.medicines
                    .none { it.ownerUid == "user-a" },
            )

            fakes.auth.session.value = null
            advanceUntilIdle()
            assertTrue(
                viewModel.state.value.medicines
                    .isEmpty(),
            )
        }

    @Test
    fun `reset-time change adopts the account snapshot for the recomputed day`() =
        runTest(mainDispatcherRule.dispatcher) {
            val earlyClock = Clock.fixed(Instant.parse("2026-07-28T17:00:00Z"), zone)
            val fakes =
                fakeRepositories(
                    session = session("user-a"),
                    clock = earlyClock,
                    settings = UserSettings(resetMinutesAfterMidnight = 0),
                )
            val tracker = RefreshTracker()
            publish(fakes, tracker, "user-a", LocalDate.of(2026, 7, 29))
            val viewModel = viewModel(fakes, tracker, earlyClock)
            activate(viewModel)
            advanceUntilIdle()
            assertEquals(LocalDate.of(2026, 7, 29), viewModel.state.value.logicalDay)

            fakes.settings.localSettings.value = UserSettings(resetMinutesAfterMidnight = 120)
            publish(fakes, tracker, "user-a", LocalDate.of(2026, 7, 28))
            advanceUntilIdle()
            assertEquals(LocalDate.of(2026, 7, 28), viewModel.state.value.logicalDay)

            viewModel.refreshTemporalState()
            advanceUntilIdle()
            assertEquals(1, tracker.temporalRefreshes)
        }

    @Test
    fun `main state consumes graph snapshot without creating duplicate repository listeners`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = session("user-a"), clock = clock)
            fakes.medicines.emit("user-a", DataEnvelope(listOf(medicine("a", "Shared medicine"))))
            val tracker = RefreshTracker()
            publish(fakes, tracker, "user-a", day)
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)

            advanceUntilIdle()

            assertEquals(
                "Shared medicine",
                viewModel.state.value.medicines
                    .single()
                    .name,
            )
            assertEquals(0, fakes.medicines.observeActiveCount)
            assertEquals(0, fakes.doses.observeDayCount)
        }

    @Test
    fun `check at a temporal boundary writes to the recomputed day and refreshes temporal state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val staleDay = day.minusDays(1)
            val fakes = fakeRepositories(session = session("user-a"), clock = clock)
            fakes.medicines.emit("user-a", DataEnvelope(listOf(medicine("a", "Medicine A"))))
            val tracker = RefreshTracker()
            publish(fakes, tracker, "user-a", staleDay)
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)
            advanceUntilIdle()

            viewModel.check(
                viewModel.state.value.rows
                    .first(),
                CheckSource.APP,
            )
            advanceUntilIdle()

            assertEquals(
                day,
                fakes.doses.checkCalls
                    .single()
                    .logicalDay,
            )
            assertEquals(1, tracker.temporalRefreshes)
            assertEquals(1, tracker.repositoryRefreshes)
        }

    @Test
    fun `undo at a temporal boundary refreshes without undoing the prior logical day`() =
        runTest(mainDispatcherRule.dispatcher) {
            val staleDay = day.minusDays(1)
            val fakes = fakeRepositories(session = session("user-a"), clock = clock)
            val medicine = medicine("a", "Medicine A")
            fakes.medicines.emit("user-a", DataEnvelope(listOf(medicine)))
            fakes.doses.check(
                uid = "user-a",
                logicalDay = staleDay,
                medicine = medicine,
                slot = DoseSlot.AFTERNOON,
                source = CheckSource.APP,
            )
            fakes.doses.checkCalls.clear()
            val tracker = RefreshTracker()
            publish(fakes, tracker, "user-a", staleDay)
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)
            advanceUntilIdle()

            viewModel.undo(
                viewModel.state.value.rows
                    .first { it.slot == DoseSlot.AFTERNOON },
            )
            advanceUntilIdle()

            assertTrue(fakes.doses.undoCalls.isEmpty())
            assertEquals(1, tracker.temporalRefreshes)
            assertEquals(0, tracker.repositoryRefreshes)
        }

    private fun TestScope.activate(viewModel: MainViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect()
        }
    }

    private fun viewModel(
        fakes: FakeRepositories,
        tracker: RefreshTracker,
        dependencyClock: Clock = clock,
    ): MainViewModel =
        MainViewModel(
            MainViewModelDependencies(
                repositories = fakes.bundle,
                clock = dependencyClock,
                accountDaySnapshot = tracker.accountDaySnapshot,
                refreshTemporalState = { tracker.temporalRefreshes += 1 },
                refreshFromRepositories = { tracker.repositoryRefreshes += 1 },
            ),
        )

    private fun publish(
        fakes: FakeRepositories,
        tracker: RefreshTracker,
        uid: String,
        logicalDay: LocalDate,
    ) {
        tracker.accountDaySnapshot.value =
            AccountDaySnapshot(
                ownerUid = uid,
                logicalDay = logicalDay,
                medicines = fakes.medicines.envelope(uid),
                doses = fakes.doses.envelope(uid, logicalDay),
            )
    }

    private fun session(uid: String): AuthSession =
        AuthSession(
            uid = uid,
            displayName = "Test user",
            email = "$uid@example.com",
            providers = setOf("password"),
        )

    private fun medicine(
        id: String,
        name: String,
        ownerUid: String = "user-a",
    ): Medicine =
        Medicine(
            id = id,
            ownerUid = ownerUid,
            name = name,
            afternoonEnabled = true,
            afternoonLabel = "Afternoon",
            nightEnabled = true,
            nightLabel = "Night",
        )

    private data class RefreshTracker(
        var temporalRefreshes: Int = 0,
        var repositoryRefreshes: Int = 0,
        val accountDaySnapshot: MutableStateFlow<AccountDaySnapshot?> = MutableStateFlow(null),
    )
}
