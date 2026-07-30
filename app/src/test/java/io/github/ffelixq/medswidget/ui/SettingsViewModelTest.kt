package io.github.ffelixq.medswidget.ui

import androidx.lifecycle.viewModelScope
import io.github.ffelixq.medswidget.domain.AuthSession
import io.github.ffelixq.medswidget.domain.DISPLAY_NAME_MAX_LENGTH
import io.github.ffelixq.medswidget.domain.DataEnvelope
import io.github.ffelixq.medswidget.domain.ThemePreference
import io.github.ffelixq.medswidget.domain.UserSettings
import io.github.ffelixq.medswidget.sync.AccountOperationGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock =
        Clock.fixed(
            Instant.parse("2026-07-29T05:00:00Z"),
            ZoneId.of("Asia/Singapore"),
        )

    @Test
    fun `valid reset-time change updates settings and temporal widget refresh seam`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            val tracker = SettingsTracker()
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)
            advanceUntilIdle()

            viewModel.updateResetTime(3 * 60 + 15)
            advanceUntilIdle()

            assertEquals(195, fakes.settings.localSettings.value.resetMinutesAfterMidnight)
            assertEquals(listOf("user-a"), fakes.settings.updateUids)
            assertEquals(1, tracker.temporalRefreshes)
            assertEquals(
                "Reset time changed. The visible logical day may change.",
                viewModel.state.value.message,
            )
            assertFalse(viewModel.state.value.isBusy)
        }

    @Test
    fun `invalid reset time is rejected without persistence or refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            val tracker = SettingsTracker()
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)
            advanceUntilIdle()

            viewModel.updateResetTime(24 * 60)
            advanceUntilIdle()

            assertEquals("Enter a valid time.", viewModel.state.value.errorMessage)
            assertTrue(fakes.settings.updateUids.isEmpty())
            assertEquals(0, tracker.temporalRefreshes)
        }

    @Test
    fun `theme change persists selected light dark and system preferences`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            val tracker = SettingsTracker()
            val viewModel = viewModel(fakes, tracker)

            listOf(ThemePreference.LIGHT, ThemePreference.DARK, ThemePreference.SYSTEM).forEach {
                viewModel.updateTheme(it)
                advanceUntilIdle()
                assertEquals(it, fakes.settings.localSettings.value.themePreference)
            }

            assertEquals(3, tracker.temporalRefreshes)
        }

    @Test
    fun `display name is trimmed and updated in auth and settings`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            val viewModel = viewModel(fakes, SettingsTracker())
            activate(viewModel)

            viewModel.updateDisplayName("  Updated Person  ")
            advanceUntilIdle()

            assertEquals(listOf("Updated Person"), fakes.auth.displayNameUpdates)
            assertEquals("Updated Person", fakes.settings.localSettings.value.displayName)
            assertEquals("Display name updated.", viewModel.state.value.message)
            assertNull(viewModel.state.value.errorMessage)
        }

    @Test
    fun `empty and excessive display names are rejected`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            val viewModel = viewModel(fakes, SettingsTracker())
            activate(viewModel)
            advanceUntilIdle()

            viewModel.updateDisplayName(" ")
            advanceUntilIdle()
            assertEquals(
                "Display name must be 1–$DISPLAY_NAME_MAX_LENGTH characters.",
                viewModel.state.value.errorMessage,
            )

            viewModel.clearMessage()
            viewModel.updateDisplayName("x".repeat(DISPLAY_NAME_MAX_LENGTH + 1))
            advanceUntilIdle()

            assertEquals(
                "Display name must be 1–$DISPLAY_NAME_MAX_LENGTH characters.",
                viewModel.state.value.errorMessage,
            )
            assertTrue(fakes.auth.displayNameUpdates.isEmpty())
        }

    @Test
    fun `sign out clears local account snapshot and updates widgets`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes =
                fakeRepositories(
                    session = passwordSession(),
                    clock = clock,
                    settings = UserSettings(displayName = "Private user"),
                )
            val tracker = SettingsTracker()
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)

            viewModel.signOut()
            advanceUntilIdle()

            assertEquals(1, fakes.auth.signOutCount)
            assertNull(fakes.auth.session.value)
            assertEquals(1, fakes.settings.clearCount)
            assertEquals(1, tracker.snapshotClears)
            assertEquals(0, tracker.configurationClears)
            assertEquals(1, tracker.widgetUpdates)
            assertEquals("", fakes.settings.localSettings.value.displayName)
        }

    @Test
    fun `password account deletion requires reauthentication before deleting owned data`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            val tracker = SettingsTracker()
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)

            viewModel.deleteAccount(password = null)
            advanceUntilIdle()

            assertEquals(
                "Enter your password to confirm account deletion.",
                viewModel.state.value.errorMessage,
            )
            assertFalse(viewModel.state.value.isDeletingAccount)
            assertFalse(tracker.accountOperationGate.isDeletionInProgress)
            assertTrue(fakes.accountData.deletedUids.isEmpty())
            assertEquals(0, tracker.snapshotClears)

            viewModel.deleteAccount(password = "current-password")
            advanceUntilIdle()

            assertEquals(listOf("current-password"), fakes.auth.passwordReauthentications)
            assertEquals(listOf("user-a"), tracker.outstandingWriteWaitUids)
            assertEquals(listOf("user-a"), fakes.accountData.deletedUids)
            assertEquals(1, fakes.auth.deleteAccountCount)
            assertEquals(1, tracker.snapshotClears)
            assertEquals(1, tracker.configurationClears)
            assertEquals(1, tracker.widgetUpdates)
            assertEquals(1, tracker.persistenceClears)
            assertEquals(1, tracker.graphReinitializations)
            assertTrue(viewModel.state.value.accountDeletionCompleted)
        }

    @Test
    fun `account deletion waits for pending writes and fails safely when they cannot synchronise`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            val tracker = SettingsTracker(outstandingWritesReady = false)
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)

            viewModel.deleteAccount(password = "current-password")
            advanceUntilIdle()

            assertEquals(listOf("current-password"), fakes.auth.passwordReauthentications)
            assertEquals(listOf("user-a"), tracker.outstandingWriteWaitUids)
            assertTrue(fakes.accountData.deletedUids.isEmpty())
            assertEquals(0, fakes.auth.deleteAccountCount)
            assertEquals(0, tracker.snapshotClears)
            assertEquals(0, tracker.graphReinitializations)
            assertFalse(tracker.accountOperationGate.isDeletionInProgress)
            assertFalse(viewModel.state.value.isDeletingAccount)
            assertFalse(viewModel.state.value.isBusy)
            assertEquals(
                "Reconnect and wait for pending changes to synchronise before deleting your account.",
                viewModel.state.value.errorMessage,
            )
        }

    @Test
    fun `account deletion gate rejects duplicate deletion and sign out while deletion is running`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            val operationGate = CompletableDeferred<Unit>()
            fakes.auth.operationGate = operationGate
            val tracker = SettingsTracker()
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)

            viewModel.deleteAccount(password = "current-password")
            viewModel.deleteAccount(password = "current-password")
            viewModel.signOut()
            runCurrent()

            assertTrue(viewModel.state.value.isBusy)
            assertTrue(viewModel.state.value.isDeletingAccount)
            assertTrue(tracker.accountOperationGate.isDeletionInProgress)
            assertEquals(0, fakes.auth.signOutCount)

            operationGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("current-password"), fakes.auth.passwordReauthentications)
            assertEquals(listOf("user-a"), fakes.accountData.deletedUids)
            assertEquals(1, fakes.auth.deleteAccountCount)
            assertEquals(0, fakes.auth.signOutCount)
            assertEquals(1, tracker.graphReinitializations)
            assertTrue(viewModel.state.value.accountDeletionCompleted)
        }

    @Test
    fun `post-auth persistence cleanup failure still completes deletion and requests graph restart`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            val tracker =
                SettingsTracker(
                    persistenceFailure = IllegalStateException("Persistence could not be cleared"),
                )
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)

            viewModel.deleteAccount(password = "current-password")
            advanceUntilIdle()

            assertEquals(1, fakes.auth.deleteAccountCount)
            assertEquals(1, tracker.persistenceClears)
            assertEquals(1, tracker.graphReinitializations)
            assertTrue(viewModel.state.value.accountDeletionCompleted)
            assertTrue(viewModel.state.value.isDeletingAccount)
            assertTrue(viewModel.state.value.isBusy)
            assertEquals(
                "Account deleted. Finishing local cleanup while the app restarts.",
                viewModel.state.value.message,
            )
            assertNull(viewModel.state.value.errorMessage)
        }

    @Test
    fun `Google account deletion uses fresh Google credential and clears all local mappings`() =
        runTest(mainDispatcherRule.dispatcher) {
            val googleSession =
                AuthSession(
                    uid = "google-user",
                    displayName = "Google User",
                    email = "google@example.com",
                    providers = setOf("google.com"),
                )
            val fakes = fakeRepositories(session = googleSession, clock = clock)
            val tracker = SettingsTracker()
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)

            viewModel.deleteAccount(password = null, googleIdToken = "fresh-google-token")
            advanceUntilIdle()

            assertEquals(listOf("fresh-google-token"), fakes.auth.googleReauthentications)
            assertEquals(listOf("google-user"), fakes.accountData.deletedUids)
            assertEquals(1, tracker.snapshotClears)
            assertEquals(1, tracker.configurationClears)
            assertEquals(1, tracker.widgetUpdates)
            assertEquals(1, tracker.persistenceClears)
            assertEquals(1, tracker.graphReinitializations)
            assertTrue(viewModel.state.value.accountDeletionCompleted)
        }

    @Test
    fun `cancellation after authentication deletion cannot interrupt cleanup or graph reinitialization`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cleanupStarted = CompletableDeferred<Unit>()
            val releaseCleanup = CompletableDeferred<Unit>()
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            val tracker =
                SettingsTracker(
                    snapshotClearStarted = cleanupStarted,
                    releaseSnapshotClear = releaseCleanup,
                )
            val viewModel = viewModel(fakes, tracker)
            activate(viewModel)

            viewModel.deleteAccount(password = "current-password")
            cleanupStarted.await()

            assertEquals(1, fakes.auth.deleteAccountCount)
            viewModel.viewModelScope.cancel()
            releaseCleanup.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, fakes.settings.clearCount)
            assertEquals(1, tracker.snapshotClears)
            assertEquals(1, tracker.configurationClears)
            assertEquals(1, tracker.widgetUpdates)
            assertEquals(1, tracker.persistenceClears)
            assertEquals(1, tracker.graphReinitializations)
            assertEquals(
                listOf("snapshot", "configurations", "widgets", "persistence", "graph"),
                tracker.teardownEvents,
            )
        }

    @Test
    fun `repository error exits busy state and does not report success`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            fakes.auth.nextFailure = IllegalStateException("Offline; try again later")
            val viewModel = viewModel(fakes, SettingsTracker())
            activate(viewModel)

            viewModel.updateDisplayName("Updated Person")
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isBusy)
            assertEquals("Offline; try again later", viewModel.state.value.errorMessage)
            assertNull(viewModel.state.value.message)
            assertEquals("", fakes.settings.localSettings.value.displayName)
        }

    @Test
    fun `settings sync metadata exposes pending cached and failure states`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fakes = fakeRepositories(session = passwordSession(), clock = clock)
            val viewModel = viewModel(fakes, SettingsTracker())
            activate(viewModel)

            fakes.settings.syncStatus.value =
                DataEnvelope(
                    value = fakes.settings.localSettings.value,
                    fromCache = true,
                    hasPendingWrites = true,
                )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isCached)
            assertTrue(viewModel.state.value.isSyncPending)
            assertNull(viewModel.state.value.errorMessage)

            fakes.settings.syncStatus.value =
                DataEnvelope(
                    value = fakes.settings.localSettings.value,
                    fromCache = true,
                    errorMessage = "Settings could not be refreshed.",
                )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isSyncPending)
            assertEquals("Settings could not be refreshed.", viewModel.state.value.errorMessage)
        }

    private fun TestScope.activate(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect()
        }
    }

    private fun viewModel(
        fakes: FakeRepositories,
        tracker: SettingsTracker,
    ): SettingsViewModel =
        SettingsViewModel(
            SettingsViewModelDependencies(
                repositories = fakes.bundle,
                accountOperationGate = tracker.accountOperationGate,
                refreshTemporalState = { tracker.temporalRefreshes += 1 },
                clearSnapshotAccount = {
                    tracker.snapshotClears += 1
                    tracker.teardownEvents += "snapshot"
                    tracker.snapshotClearStarted?.complete(Unit)
                    tracker.releaseSnapshotClear?.await()
                },
                clearConfigurations = {
                    tracker.configurationClears += 1
                    tracker.teardownEvents += "configurations"
                },
                updateWidgets = {
                    tracker.widgetUpdates += 1
                    tracker.teardownEvents += "widgets"
                },
                shutdownAndClearFirestorePersistence = {
                    tracker.persistenceClears += 1
                    tracker.teardownEvents += "persistence"
                    tracker.persistenceFailure?.let { throw it }
                },
                awaitOutstandingWrites = { uid ->
                    tracker.outstandingWriteWaitUids += uid
                    tracker.outstandingWritesReady
                },
                reinitializeApplicationGraph = {
                    tracker.graphReinitializations += 1
                    tracker.teardownEvents += "graph"
                },
            ),
        )

    private fun passwordSession(): AuthSession =
        AuthSession(
            uid = "user-a",
            displayName = "User A",
            email = "user-a@example.com",
            providers = setOf("password"),
        )

    private data class SettingsTracker(
        val accountOperationGate: AccountOperationGate = AccountOperationGate(),
        var temporalRefreshes: Int = 0,
        var snapshotClears: Int = 0,
        var configurationClears: Int = 0,
        var widgetUpdates: Int = 0,
        var persistenceClears: Int = 0,
        var graphReinitializations: Int = 0,
        val persistenceFailure: Throwable? = null,
        val outstandingWritesReady: Boolean = true,
        val outstandingWriteWaitUids: MutableList<String> = mutableListOf(),
        val snapshotClearStarted: CompletableDeferred<Unit>? = null,
        val releaseSnapshotClear: CompletableDeferred<Unit>? = null,
        val teardownEvents: MutableList<String> = mutableListOf(),
    )
}
