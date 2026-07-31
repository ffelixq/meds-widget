package io.github.ffelixq.medswidget

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.github.ffelixq.medswidget.data.CountdownWriteOutcome
import io.github.ffelixq.medswidget.data.DoseWriteOutcome
import io.github.ffelixq.medswidget.data.RepositoryBundle
import io.github.ffelixq.medswidget.data.UnavailableAccountDataRepository
import io.github.ffelixq.medswidget.data.UnavailableAuthRepository
import io.github.ffelixq.medswidget.data.UnavailableCountdownRepository
import io.github.ffelixq.medswidget.data.UnavailableDoseRepository
import io.github.ffelixq.medswidget.data.UnavailableMedicineRepository
import io.github.ffelixq.medswidget.data.UnavailableSettingsRepository
import io.github.ffelixq.medswidget.domain.CountdownState
import io.github.ffelixq.medswidget.domain.DataEnvelope
import io.github.ffelixq.medswidget.domain.DoseRows
import io.github.ffelixq.medswidget.domain.DoseState
import io.github.ffelixq.medswidget.domain.LogicalDayCalculator
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.firebase.FirebaseAuthRepository
import io.github.ffelixq.medswidget.firebase.FirestoreAccountDataRepository
import io.github.ffelixq.medswidget.firebase.FirestoreCountdownRepository
import io.github.ffelixq.medswidget.firebase.FirestoreDoseRepository
import io.github.ffelixq.medswidget.firebase.FirestoreMedicineRepository
import io.github.ffelixq.medswidget.firebase.FirestoreSettingsRepository
import io.github.ffelixq.medswidget.sync.AccountOperationGate
import io.github.ffelixq.medswidget.sync.CountdownRefreshScheduler
import io.github.ffelixq.medswidget.sync.OutstandingWriteTracker
import io.github.ffelixq.medswidget.sync.ResetBoundaryScheduler
import io.github.ffelixq.medswidget.sync.WidgetPendingSyncScheduler
import io.github.ffelixq.medswidget.widget.WidgetConfigurationStore
import io.github.ffelixq.medswidget.widget.WidgetSnapshot
import io.github.ffelixq.medswidget.widget.WidgetSnapshotStore
import io.github.ffelixq.medswidget.widget.WidgetUpdateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class AccountDaySnapshot(
    val ownerUid: String,
    val logicalDay: LocalDate,
    val medicines: DataEnvelope<List<Medicine>>,
    val doses: DataEnvelope<List<DoseState>>,
    val countdowns: DataEnvelope<List<CountdownState>> = DataEnvelope(emptyList()),
)

internal fun selectAuthenticatedUid(
    authoritativeAuthAvailable: Boolean,
    authoritativeUid: String?,
    sessionUid: String?,
): String? = if (authoritativeAuthAvailable) authoritativeUid else sessionUid

@Suppress("TooManyFunctions")
class AppGraph(
    context: Context,
    val clock: Clock = Clock.systemDefaultZone(),
) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val snapshotStore = WidgetSnapshotStore(context)
    val configurationStore = WidgetConfigurationStore(context)
    val widgetUpdater = WidgetUpdateCoordinator(context)
    val pendingWidgetSyncScheduler = WidgetPendingSyncScheduler(context)
    val accountOperationGate = AccountOperationGate()
    private val outstandingWriteTracker = OutstandingWriteTracker()
    val repositories: RepositoryBundle
    val resetScheduler = ResetBoundaryScheduler(context, clock)
    val countdownRefreshScheduler = CountdownRefreshScheduler(context, clock)
    private val mutableTemporalTick = MutableStateFlow(0)
    val temporalTick: StateFlow<Int> = mutableTemporalTick.asStateFlow()
    private val mutableAccountDaySnapshot = MutableStateFlow<AccountDaySnapshot?>(null)
    val accountDaySnapshot: StateFlow<AccountDaySnapshot?> = mutableAccountDaySnapshot.asStateFlow()
    private val mutableInForeground = MutableStateFlow(false)
    private val firestore: FirebaseFirestore?
    private val firebaseAuthRepository: FirebaseAuthRepository?
    internal val currentAuthenticatedUid: String?
        get() =
            selectAuthenticatedUid(
                authoritativeAuthAvailable = firebaseAuthRepository != null,
                authoritativeUid = firebaseAuthRepository?.currentUid,
                sessionUid =
                    repositories.auth.session.value
                        ?.uid,
            )

    init {
        val firebaseReady = FirebaseApp.getApps(context).isNotEmpty()
        repositories =
            if (firebaseReady) {
                val activeFirestore = FirebaseFirestore.getInstance()
                val auth = FirebaseAuthRepository(FirebaseAuth.getInstance())
                firestore = activeFirestore
                firebaseAuthRepository = auth
                val settings =
                    FirestoreSettingsRepository(
                        context = context,
                        firestore = activeFirestore,
                        scope = applicationScope,
                        outstandingWriteTracker = outstandingWriteTracker,
                    )
                val doseRepository =
                    FirestoreDoseRepository(
                        firestore = activeFirestore,
                        clock = clock,
                        onWriteOutcome = ::handleDoseWriteOutcome,
                        outstandingWriteTracker = outstandingWriteTracker,
                    )
                val countdownRepository =
                    FirestoreCountdownRepository(
                        firestore = activeFirestore,
                        clock = clock,
                        onWriteOutcome = ::handleCountdownWriteOutcome,
                        outstandingWriteTracker = outstandingWriteTracker,
                    )
                RepositoryBundle(
                    auth = auth,
                    medicines =
                        FirestoreMedicineRepository(
                            firestore = activeFirestore,
                            outstandingWriteTracker = outstandingWriteTracker,
                        ),
                    doses = doseRepository,
                    countdowns = countdownRepository,
                    settings = settings,
                    accountData = FirestoreAccountDataRepository(activeFirestore),
                    clock = clock,
                )
            } else {
                firestore = null
                firebaseAuthRepository = null
                RepositoryBundle(
                    auth = UnavailableAuthRepository(),
                    medicines = UnavailableMedicineRepository(),
                    doses = UnavailableDoseRepository(),
                    countdowns = UnavailableCountdownRepository(),
                    settings = UnavailableSettingsRepository(),
                    accountData = UnavailableAccountDataRepository(),
                    clock = clock,
                )
            }
    }

    @Suppress("LongMethod")
    fun start() {
        applicationScope.launch {
            combine(
                repositories.auth.session,
                mutableInForeground,
            ) { session, inForeground ->
                session to inForeground
            }.collectLatest { (session, inForeground) ->
                if (session == null) {
                    mutableAccountDaySnapshot.value = null
                    snapshotStore.clearAccount()
                    widgetUpdater.updateAll()
                    return@collectLatest
                }
                repositories.settings.activateAccount(session.uid)
                val cachedWidgetSnapshot = snapshotStore.read()
                if (
                    !cachedWidgetSnapshot.signedIn ||
                    cachedWidgetSnapshot.ownerUid != session.uid
                ) {
                    val loadingDay =
                        LogicalDayCalculator.logicalDay(
                            clock.instant(),
                            ZoneId.systemDefault(),
                            repositories.settings.localSettings.value.resetMinutesAfterMidnight,
                        )
                    snapshotStore.write(
                        WidgetSnapshot(
                            ownerUid = session.uid,
                            signedIn = true,
                            isLoading = true,
                            logicalDay = loadingDay,
                        ),
                    )
                    widgetUpdater.updateAll()
                }
                if (!inForeground) return@collectLatest
                coroutineScope {
                    launch {
                        repositories.settings.observeCloud(session.uid).collect { /* Persists through repository. */ }
                    }
                    combine(
                        repositories.medicines.observeActive(session.uid),
                        repositories.settings.localSettings,
                        temporalTick,
                    ) { medicines, settings, _ ->
                        medicines to settings
                    }.collectLatest { (medicinesEnvelope, settings) ->
                        val logicalDay =
                            LogicalDayCalculator.logicalDay(
                                clock.instant(),
                                ZoneId.systemDefault(),
                                settings.resetMinutesAfterMidnight,
                            )
                        combine(
                            repositories.doses.observeDay(session.uid, logicalDay),
                            repositories.countdowns.observeActive(session.uid),
                        ) { doses, countdowns -> doses to countdowns }
                            .collectLatest { (dosesEnvelope, countdownsEnvelope) ->
                                val currentDay =
                                    LogicalDayCalculator.logicalDay(
                                        clock.instant(),
                                        ZoneId.systemDefault(),
                                        repositories.settings.localSettings.value.resetMinutesAfterMidnight,
                                    )
                                if (currentDay != logicalDay) {
                                    recomputeTemporalState(updateWidgets = true)
                                    return@collectLatest
                                }
                                val accountSnapshot =
                                    AccountDaySnapshot(
                                        ownerUid = session.uid,
                                        logicalDay = logicalDay,
                                        medicines = medicinesEnvelope,
                                        doses = dosesEnvelope,
                                        countdowns = countdownsEnvelope,
                                    )
                                mutableAccountDaySnapshot.value = accountSnapshot
                                writeWidgetSnapshot(accountSnapshot)
                                widgetUpdater.updateAll()
                            }
                    }
                }
            }
        }
        applicationScope.launch {
            repositories.settings.localSettings.collectLatest { settings ->
                resetScheduler.schedule(settings.resetMinutesAfterMidnight)
            }
        }
    }

    fun setForeground(inForeground: Boolean) {
        mutableInForeground.value = inForeground
        if (inForeground) {
            applicationScope.launch {
                val snapshot = snapshotStore.read()
                if (snapshot.pendingActions.isNotEmpty() || snapshot.pendingCountdownActions.isNotEmpty()) {
                    pendingWidgetSyncScheduler.schedule()
                }
            }
        }
    }

    suspend fun shutdownAndClearFirestorePersistence() {
        firebaseAuthRepository?.close()
        try {
            firestore?.let { activeFirestore ->
                activeFirestore.terminate().await()
                activeFirestore.clearPersistence().await()
            }
        } finally {
            applicationScope.cancel()
        }
    }

    suspend fun awaitOutstandingWrites(uid: String): Boolean =
        outstandingWriteTracker.awaitIdle(
            uid = uid,
            timeoutMillis = OUTSTANDING_WRITE_DRAIN_TIMEOUT_MILLIS,
        )

    private suspend fun writeWidgetSnapshot(
        accountSnapshot: AccountDaySnapshot,
        resolvePendingActions: Boolean = false,
    ) {
        val rows =
            DoseRows.build(
                accountSnapshot.medicines.value,
                accountSnapshot.doses.value,
                accountSnapshot.logicalDay,
                accountSnapshot.countdowns.value,
            )
        val repositoryHasPendingWrites =
            accountSnapshot.medicines.hasPendingWrites ||
                accountSnapshot.doses.hasPendingWrites ||
                accountSnapshot.countdowns.hasPendingWrites
        snapshotStore.writeRepositorySnapshot(
            WidgetSnapshot(
                ownerUid = accountSnapshot.ownerUid,
                signedIn = true,
                logicalDay = accountSnapshot.logicalDay,
                medicines = accountSnapshot.medicines.value.map(WidgetSnapshotStore::fromMedicine),
                rows = rows.map(WidgetSnapshotStore::fromRow),
                fromCache =
                    accountSnapshot.medicines.fromCache ||
                        accountSnapshot.doses.fromCache ||
                        accountSnapshot.countdowns.fromCache,
                hasPendingWrites = repositoryHasPendingWrites,
                repositoryHasPendingWrites = repositoryHasPendingWrites,
                errorMessage =
                    accountSnapshot.doses.errorMessage
                        ?: accountSnapshot.countdowns.errorMessage
                        ?: accountSnapshot.medicines.errorMessage,
            ),
            resolvePendingActions = resolvePendingActions,
        )
        countdownRefreshScheduler.schedule(snapshotStore.read())
    }

    private fun handleDoseWriteOutcome(outcome: DoseWriteOutcome) {
        applicationScope.launch {
            if (snapshotStore.resolveWriteOutcome(outcome)) {
                widgetUpdater.updateAll()
            }
        }
    }

    private fun handleCountdownWriteOutcome(outcome: CountdownWriteOutcome) {
        applicationScope.launch {
            if (snapshotStore.resolveCountdownWriteOutcome(outcome)) {
                val snapshot = snapshotStore.read()
                countdownRefreshScheduler.schedule(snapshot)
                widgetUpdater.updateAll()
            }
        }
    }

    suspend fun refreshTemporalState() {
        recomputeTemporalState(updateWidgets = true)
    }

    suspend fun prepareTemporalStateForWidgetRender() {
        val activeUid = currentAuthenticatedUid
        activeUid?.let { repositories.settings.activateAccount(it) }
        val settings = repositories.settings.localSettings.value
        val logicalDay =
            LogicalDayCalculator.logicalDay(
                clock.instant(),
                ZoneId.systemDefault(),
                settings.resetMinutesAfterMidnight,
            )
        snapshotStore.secureForSession(activeUid, logicalDay)
        recomputeTemporalState(updateWidgets = false)
    }

    private suspend fun recomputeTemporalState(updateWidgets: Boolean) {
        val settings = repositories.settings.localSettings.value
        val day =
            LogicalDayCalculator.logicalDay(
                clock.instant(),
                ZoneId.systemDefault(),
                settings.resetMinutesAfterMidnight,
            )
        val changed = snapshotStore.rollToLogicalDay(day)
        if (changed) {
            mutableAccountDaySnapshot.value = null
            mutableTemporalTick.update { it + 1 }
        }
        if (updateWidgets) {
            widgetUpdater.updateAll()
        }
        countdownRefreshScheduler.schedule(snapshotStore.read())
        resetScheduler.schedule(settings.resetMinutesAfterMidnight)
    }

    suspend fun refreshFromRepositories() {
        val activeUid = currentAuthenticatedUid ?: return
        recomputeTemporalState(updateWidgets = false)
        val settings = repositories.settings.localSettings.value
        val expectedDay =
            LogicalDayCalculator.logicalDay(
                clock.instant(),
                ZoneId.systemDefault(),
                settings.resetMinutesAfterMidnight,
            )
        val current = accountDaySnapshot.value
        if (
            current != null &&
            current.ownerUid == activeUid &&
            current.logicalDay == expectedDay
        ) {
            writeWidgetSnapshot(current)
        }
        widgetUpdater.updateAll()
    }

    /**
     * One-shot, network-constrained reconciliation for optimistic widget actions. WorkManager
     * calls this after connectivity returns, including after process death. Normal foreground
     * listeners remain lifecycle-scoped.
     */
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    suspend fun reconcilePendingWidgetActions(): Boolean {
        if (accountOperationGate.isDeletionInProgress) return true
        val activeUid = currentAuthenticatedUid
        prepareTemporalStateForWidgetRender()
        val cached = snapshotStore.read()
        if (cached.pendingActions.isEmpty() && cached.pendingCountdownActions.isEmpty()) {
            widgetUpdater.updateAll()
            return true
        }
        if (activeUid == null || cached.ownerUid != activeUid) {
            snapshotStore.clearAccount()
            widgetUpdater.updateAll()
            return true
        }
        val expired =
            snapshotStore.expireUnsubmittedActions(
                clock.instant().minusMillis(UNSUBMITTED_ACTION_GRACE_MILLIS),
            )
        if (expired) widgetUpdater.updateAll()
        val pendingSnapshot = snapshotStore.read()
        val pending = pendingSnapshot.pendingActions
        val pendingCountdowns = pendingSnapshot.pendingCountdownActions
        if (pending.isEmpty() && pendingCountdowns.isEmpty() && !expired) return true
        if (pending.any { !it.submitted } || pendingCountdowns.any { !it.submitted }) return false
        repositories.settings.activateAccount(activeUid)
        val logicalDay =
            LogicalDayCalculator.logicalDay(
                clock.instant(),
                ZoneId.systemDefault(),
                repositories.settings.localSettings.value.resetMinutesAfterMidnight,
            )
        val reconciled =
            withTimeoutOrNull(PENDING_RECONCILIATION_TIMEOUT_MILLIS) {
                combine(
                    repositories.medicines.observeActive(activeUid),
                    repositories.doses.observeDay(activeUid, logicalDay),
                    repositories.countdowns.observeActive(activeUid),
                ) { medicines, doses, countdowns ->
                    Triple(medicines, doses, countdowns)
                }.first { (medicines, doses, countdowns) ->
                    !medicines.fromCache &&
                        !doses.fromCache &&
                        !countdowns.fromCache &&
                        !medicines.hasPendingWrites &&
                        !doses.hasPendingWrites &&
                        !countdowns.hasPendingWrites
                }
            } ?: return false
        val accountSnapshot =
            AccountDaySnapshot(
                ownerUid = activeUid,
                logicalDay = logicalDay,
                medicines = reconciled.first,
                doses = reconciled.second,
                countdowns = reconciled.third,
            )
        mutableAccountDaySnapshot.value = accountSnapshot
        writeWidgetSnapshot(accountSnapshot, resolvePendingActions = true)
        widgetUpdater.updateAll()
        val finalSnapshot = snapshotStore.read()
        return finalSnapshot.pendingActions.isEmpty() &&
            finalSnapshot.pendingCountdownActions.isEmpty()
    }

    suspend fun refreshCountdownDisplay() {
        prepareTemporalStateForWidgetRender()
        val snapshot = snapshotStore.read()
        widgetUpdater.updateAll()
        countdownRefreshScheduler.schedule(snapshot)
    }

    private companion object {
        const val PENDING_RECONCILIATION_TIMEOUT_MILLIS = 20_000L
        const val OUTSTANDING_WRITE_DRAIN_TIMEOUT_MILLIS = 20_000L
        const val UNSUBMITTED_ACTION_GRACE_MILLIS = 30_000L
    }
}
