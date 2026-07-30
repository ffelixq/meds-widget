package io.github.ffelixq.medswidget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ffelixq.medswidget.AppGraph
import io.github.ffelixq.medswidget.data.RepositoryBundle
import io.github.ffelixq.medswidget.domain.DISPLAY_NAME_MAX_LENGTH
import io.github.ffelixq.medswidget.domain.LogicalDayCalculator
import io.github.ffelixq.medswidget.domain.ThemePreference
import io.github.ffelixq.medswidget.domain.UserSettings
import io.github.ffelixq.medswidget.sync.AccountOperationGate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val accountEmail: String? = null,
    val providers: Set<String> = emptySet(),
    val timezoneId: String = ZoneId.systemDefault().id,
    val isBusy: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val isCached: Boolean = false,
    val isSyncPending: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val accountDeletionCompleted: Boolean = false,
)

internal data class SettingsViewModelDependencies(
    val repositories: RepositoryBundle,
    val accountOperationGate: AccountOperationGate,
    val refreshTemporalState: suspend () -> Unit,
    val clearSnapshotAccount: suspend () -> Unit,
    val clearConfigurations: suspend () -> Unit,
    val updateWidgets: suspend () -> Unit,
    val shutdownAndClearFirestorePersistence: suspend () -> Unit,
    val awaitOutstandingWrites: suspend (String) -> Boolean,
    val reinitializeApplicationGraph: () -> Unit,
)

@Suppress("TooManyFunctions")
class SettingsViewModel internal constructor(
    private val dependencies: SettingsViewModelDependencies,
) : ViewModel() {
    constructor(
        graph: AppGraph,
        reinitializeApplicationGraph: () -> Unit,
        awaitOutstandingWrites: suspend (String) -> Boolean,
    ) : this(
        SettingsViewModelDependencies(
            repositories = graph.repositories,
            accountOperationGate = graph.accountOperationGate,
            refreshTemporalState = graph::refreshTemporalState,
            clearSnapshotAccount = graph.snapshotStore::clearAccount,
            clearConfigurations = graph.configurationStore::clearAll,
            updateWidgets = graph.widgetUpdater::updateAll,
            shutdownAndClearFirestorePersistence = graph::shutdownAndClearFirestorePersistence,
            awaitOutstandingWrites = awaitOutstandingWrites,
            reinitializeApplicationGraph = reinitializeApplicationGraph,
        ),
    )

    private val repositories = dependencies.repositories
    private val status = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> =
        combine(
            repositories.settings.localSettings,
            repositories.settings.syncStatus,
            repositories.auth.session,
            status,
        ) { settings, syncStatus, session, statusValue ->
            statusValue.copy(
                settings = settings,
                accountEmail = session?.email,
                providers = session?.providers.orEmpty(),
                timezoneId = ZoneId.systemDefault().id,
                isCached = syncStatus.fromCache,
                isSyncPending = syncStatus.hasPendingWrites,
                errorMessage = statusValue.errorMessage ?: syncStatus.errorMessage,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateResetTime(minutesAfterMidnight: Int) {
        if (minutesAfterMidnight !in 0 until LogicalDayCalculator.MINUTES_PER_DAY) {
            reportError("Enter a valid time.")
            return
        }
        updateSettings("Reset time changed. The visible logical day may change.") {
            it.copy(resetMinutesAfterMidnight = minutesAfterMidnight)
        }
    }

    fun updateTheme(theme: ThemePreference) {
        updateSettings {
            it.copy(themePreference = theme)
        }
    }

    fun updateDisplayName(displayName: String) {
        if (!canStartMutation()) return
        val normalized = displayName.trim()
        if (normalized.isEmpty() || normalized.length > DISPLAY_NAME_MAX_LENGTH) {
            reportError("Display name must be 1–$DISPLAY_NAME_MAX_LENGTH characters.")
            return
        }
        viewModelScope.launch {
            setBusy()
            runCatching {
                runAccountMutation {
                    repositories.auth.updateDisplayName(normalized)
                    val uid = requireNotNull(repositories.auth.session.value).uid
                    repositories.settings.update(uid) { it.copy(displayName = normalized) }
                }
            }.finishMutation("Display name updated.")
        }
    }

    fun ensureDisplayName(displayName: String) {
        val normalized = displayName.trim()
        val uid =
            repositories.auth.session.value
                ?.uid ?: return
        if (
            normalized.isEmpty() ||
            !canStartMutation() ||
            repositories.settings.localSettings.value.displayName
                .isNotBlank()
        ) {
            return
        }
        viewModelScope.launch {
            runCatching {
                runAccountMutation {
                    repositories.settings.update(uid) {
                        if (it.displayName.isBlank()) it.copy(displayName = normalized) else it
                    }
                }
            }.onFailure {
                if (!status.value.isDeletingAccount) {
                    reportError("Your display name could not be synchronised yet.")
                }
            }
        }
    }

    fun signOut() {
        if (!canStartMutation()) return
        viewModelScope.launch {
            setBusy()
            runCatching {
                runAccountMutation {
                    repositories.auth.signOut()
                    repositories.settings.clear()
                    dependencies.clearSnapshotAccount()
                    dependencies.updateWidgets()
                }
            }.finishMutation()
        }
    }

    fun deleteAccount(
        password: String?,
        googleIdToken: String? = null,
    ) {
        if (status.value.isDeletingAccount || dependencies.accountOperationGate.isDeletionInProgress) return
        val session = repositories.auth.session.value ?: return
        status.value =
            status.value.copy(
                isBusy = true,
                isDeletingAccount = true,
                errorMessage = null,
                message = null,
            )
        viewModelScope.launch {
            var authenticationDeleted = false
            val deletion =
                runCatching {
                    dependencies.accountOperationGate.runDeletion {
                        if ("password" in session.providers) {
                            if (password.isNullOrEmpty()) error("Enter your password to confirm account deletion.")
                            repositories.auth.reauthenticateWithPassword(password)
                        } else if ("google.com" in session.providers) {
                            if (googleIdToken.isNullOrEmpty()) {
                                error("Sign in with Google again to confirm account deletion.")
                            }
                            repositories.auth.reauthenticateWithGoogleIdToken(googleIdToken)
                        }
                        if (!dependencies.awaitOutstandingWrites(session.uid)) {
                            error(
                                "Reconnect and wait for pending changes to synchronise before deleting your account.",
                            )
                        }
                        repositories.accountData.deleteAll(session.uid)
                        repositories.auth.deleteAuthenticationAccount()
                        authenticationDeleted = true
                        withContext(NonCancellable) {
                            val cleanupFailures = mutableListOf<Throwable>()
                            postDeletionCleanupActions().forEach { action ->
                                runCatching { action() }.onFailure(cleanupFailures::add)
                            }
                            runCatching { dependencies.reinitializeApplicationGraph() }
                                .onFailure(cleanupFailures::add)
                            completeAccountDeletion(cleanupFailures.isNotEmpty())
                        }
                    }
                }
            if (!authenticationDeleted) {
                deletion.finishDeletionFailure()
            }
        }
    }

    fun reportError(message: String) {
        status.value = status.value.copy(errorMessage = message, message = null, isBusy = false)
    }

    fun clearMessage() {
        status.value = status.value.copy(errorMessage = null, message = null)
    }

    private fun updateSettings(
        successMessage: String? = null,
        transform: (UserSettings) -> UserSettings,
    ) {
        if (!canStartMutation()) return
        val session = repositories.auth.session.value ?: return
        viewModelScope.launch {
            setBusy()
            runCatching {
                runAccountMutation {
                    repositories.settings.update(session.uid) { current ->
                        val updated = transform(current)
                        if (updated.displayName.isBlank()) {
                            updated.copy(displayName = session.displayName.trim())
                        } else {
                            updated
                        }
                    }
                    dependencies.refreshTemporalState()
                }
            }.finishMutation(successMessage)
        }
    }

    private fun setBusy() {
        status.value = status.value.copy(isBusy = true, errorMessage = null, message = null)
    }

    private fun canStartMutation(): Boolean =
        !status.value.isDeletingAccount &&
            !dependencies.accountOperationGate.isDeletionInProgress

    private suspend fun runAccountMutation(block: suspend () -> Unit): Boolean =
        dependencies.accountOperationGate.runMutation {
            block()
            true
        } == true

    private fun Result<Boolean>.finishMutation(successMessage: String? = null) {
        onSuccess { applied ->
            if (status.value.isDeletingAccount) return@onSuccess
            status.value =
                status.value.copy(
                    isBusy = false,
                    message = successMessage.takeIf { applied },
                )
        }.onFailure { error ->
            if (status.value.isDeletingAccount) return@onFailure
            status.value =
                status.value.copy(
                    isBusy = false,
                    errorMessage = error.message ?: "The change could not be completed.",
                )
        }
    }

    private fun postDeletionCleanupActions(): List<suspend () -> Unit> =
        listOf(
            repositories.settings::clear,
            dependencies.clearSnapshotAccount,
            dependencies.clearConfigurations,
            dependencies.updateWidgets,
            dependencies.shutdownAndClearFirestorePersistence,
        )

    private fun completeAccountDeletion(hadCleanupFailure: Boolean) {
        status.value =
            status.value.copy(
                isBusy = true,
                isDeletingAccount = true,
                message =
                    if (hadCleanupFailure) {
                        "Account deleted. Finishing local cleanup while the app restarts."
                    } else {
                        null
                    },
                errorMessage = null,
                accountDeletionCompleted = true,
            )
    }

    private fun Result<Unit>.finishDeletionFailure() {
        val error = exceptionOrNull() ?: return
        status.value =
            status.value.copy(
                isBusy = false,
                isDeletingAccount = false,
                errorMessage = error.message ?: "The account could not be deleted.",
            )
    }
}
