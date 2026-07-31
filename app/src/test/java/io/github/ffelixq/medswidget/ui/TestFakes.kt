package io.github.ffelixq.medswidget.ui

import io.github.ffelixq.medswidget.data.AccountDataRepository
import io.github.ffelixq.medswidget.data.AuthRepository
import io.github.ffelixq.medswidget.data.CountdownRepository
import io.github.ffelixq.medswidget.data.DoseRepository
import io.github.ffelixq.medswidget.data.MedicineRepository
import io.github.ffelixq.medswidget.data.RepositoryBundle
import io.github.ffelixq.medswidget.data.SettingsRepository
import io.github.ffelixq.medswidget.domain.AuthSession
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownIds
import io.github.ffelixq.medswidget.domain.CountdownLogic
import io.github.ffelixq.medswidget.domain.CountdownState
import io.github.ffelixq.medswidget.domain.CountdownStatus
import io.github.ffelixq.medswidget.domain.DataEnvelope
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseActionPolicy
import io.github.ffelixq.medswidget.domain.DoseCommandDecision
import io.github.ffelixq.medswidget.domain.DoseEvent
import io.github.ffelixq.medswidget.domain.DoseIds
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.DoseState
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.MedicineDraft
import io.github.ffelixq.medswidget.domain.UserSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

internal class FakeAuthRepository(
    initialSession: AuthSession? = null,
    override val isConfigured: Boolean = true,
) : AuthRepository {
    override val session = MutableStateFlow(initialSession)
    var nextFailure: Throwable? = null
    var operationGate: CompletableDeferred<Unit>? = null
    val emailSignIns = mutableListOf<Pair<String, String>>()
    val emailSignUps = mutableListOf<Triple<String, String, String>>()
    val googleTokens = mutableListOf<String>()
    val passwordResetEmails = mutableListOf<String>()
    val displayNameUpdates = mutableListOf<String>()
    val passwordReauthentications = mutableListOf<String>()
    val googleReauthentications = mutableListOf<String>()
    var signOutCount = 0
    var deleteAccountCount = 0

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ) {
        beforeOperation()
        emailSignIns += email to password
        session.value =
            AuthSession(
                uid = "email-user",
                displayName = "Email User",
                email = email,
                providers = setOf("password"),
            )
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
    ) {
        beforeOperation()
        emailSignUps += Triple(email, password, displayName)
        session.value =
            AuthSession(
                uid = "new-user",
                displayName = displayName,
                email = email,
                providers = setOf("password"),
            )
    }

    override suspend fun signInWithGoogleIdToken(idToken: String) {
        beforeOperation()
        googleTokens += idToken
        session.value =
            AuthSession(
                uid = "google-user",
                displayName = "Google User",
                email = "google@example.com",
                providers = setOf("google.com"),
            )
    }

    override suspend fun sendPasswordReset(email: String) {
        beforeOperation()
        passwordResetEmails += email
    }

    override suspend fun updateDisplayName(displayName: String) {
        beforeOperation()
        displayNameUpdates += displayName
        session.value = session.value?.copy(displayName = displayName)
    }

    override suspend fun reauthenticateWithPassword(password: String) {
        beforeOperation()
        passwordReauthentications += password
    }

    override suspend fun reauthenticateWithGoogleIdToken(idToken: String) {
        beforeOperation()
        googleReauthentications += idToken
    }

    override suspend fun deleteAuthenticationAccount() {
        beforeOperation()
        deleteAccountCount += 1
        session.value = null
    }

    override suspend fun signOut() {
        beforeOperation()
        signOutCount += 1
        session.value = null
    }

    private suspend fun beforeOperation() {
        operationGate?.await()
        nextFailure?.let {
            nextFailure = null
            throw it
        }
    }
}

internal data class SaveCall(
    val uid: String,
    val draft: MedicineDraft,
)

internal class FakeMedicineRepository : MedicineRepository {
    private val activeByUser = mutableMapOf<String, MutableStateFlow<DataEnvelope<List<Medicine>>>>()
    val saveCalls = mutableListOf<SaveCall>()
    val archiveCalls = mutableListOf<Triple<String, String, Boolean>>()
    val deleteCalls = mutableListOf<Pair<String, String>>()
    var observeActiveCount = 0
    private var nextId = 1

    override fun observeActive(uid: String): Flow<DataEnvelope<List<Medicine>>> {
        observeActiveCount += 1
        return state(uid)
    }

    override fun observeAll(uid: String): Flow<DataEnvelope<List<Medicine>>> = state(uid)

    override suspend fun save(
        uid: String,
        draft: MedicineDraft,
    ): String {
        saveCalls += SaveCall(uid, draft)
        val current = state(uid).value
        val id = draft.id ?: "medicine-${nextId++}"
        val existing = current.value.firstOrNull { it.id == id }
        val saved =
            Medicine(
                id = id,
                ownerUid = uid,
                name = draft.name,
                afternoonEnabled = draft.afternoonEnabled,
                afternoonLabel = draft.afternoonLabel,
                afternoonCountdownMinutes = draft.afternoonCountdownMinutes,
                nightEnabled = draft.nightEnabled,
                nightLabel = draft.nightLabel,
                nightCountdownMinutes = draft.nightCountdownMinutes,
                archived = existing?.archived ?: false,
                createdAt = existing?.createdAt ?: Instant.parse("2026-07-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-07-29T00:00:00Z"),
            )
        state(uid).value =
            DataEnvelope(
                current.value.filterNot { it.id == id } + saved,
            )
        return id
    }

    override suspend fun archive(
        uid: String,
        medicineId: String,
        archived: Boolean,
    ) {
        archiveCalls += Triple(uid, medicineId, archived)
        val current = state(uid).value
        state(uid).value =
            DataEnvelope(
                current.value
                    .map {
                        if (it.id == medicineId) it.copy(archived = archived) else it
                    }.filterNot(Medicine::archived),
            )
    }

    override suspend fun delete(
        uid: String,
        medicineId: String,
    ) {
        deleteCalls += uid to medicineId
        val current = state(uid).value
        state(uid).value = DataEnvelope(current.value.filterNot { it.id == medicineId })
    }

    fun emit(
        uid: String,
        envelope: DataEnvelope<List<Medicine>>,
    ) {
        state(uid).value = envelope
    }

    fun medicines(uid: String): List<Medicine> = state(uid).value.value

    fun envelope(uid: String): DataEnvelope<List<Medicine>> = state(uid).value

    private fun state(uid: String): MutableStateFlow<DataEnvelope<List<Medicine>>> =
        activeByUser.getOrPut(uid) { MutableStateFlow(DataEnvelope(emptyList())) }
}

internal data class DoseCall(
    val uid: String,
    val logicalDay: LocalDate,
    val medicineId: String,
    val slot: DoseSlot,
    val source: CheckSource,
)

internal class FakeDoseRepository(
    private val clock: Clock,
) : DoseRepository {
    private val statesByUserDay =
        mutableMapOf<Pair<String, LocalDate>, MutableStateFlow<DataEnvelope<List<DoseState>>>>()
    private val historyByUser =
        mutableMapOf<String, MutableStateFlow<DataEnvelope<List<DoseEvent>>>>()
    val checkCalls = mutableListOf<DoseCall>()
    val undoCalls = mutableListOf<DoseCall>()
    var observeDayCount = 0
    private var actionNumber = 0

    override fun observeDay(
        uid: String,
        logicalDay: LocalDate,
    ): Flow<DataEnvelope<List<DoseState>>> {
        observeDayCount += 1
        return dayState(uid, logicalDay)
    }

    override fun observeHistory(uid: String): Flow<DataEnvelope<List<DoseEvent>>> = historyState(uid)

    override suspend fun check(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
    ): Boolean {
        checkCalls += DoseCall(uid, logicalDay, medicine.id, slot, source)
        val flow = dayState(uid, logicalDay)
        val stateId = DoseIds.stateId(logicalDay, medicine.id, slot)
        val current = flow.value.value.firstOrNull { it.id == stateId }
        if (DoseActionPolicy.check(current) != DoseCommandDecision.APPLY_CHECK) return false
        val actionId = nextActionId()
        val now = clock.instant()
        val state =
            DoseState(
                id = stateId,
                ownerUid = uid,
                logicalDay = logicalDay,
                medicineId = medicine.id,
                slot = slot,
                labelSnapshot = medicine.label(slot),
                medicineNameSnapshot = medicine.name,
                isTaken = true,
                checkedAt = now,
                checkedTimezone = clock.zone.id,
                checkedSource = source,
                undoneAt = null,
                lastActionId = actionId,
                updatedAt = now,
            )
        flow.value =
            DataEnvelope(
                flow.value.value.filterNot { it.id == stateId } + state,
                hasPendingWrites = true,
            )
        appendEvent(
            uid = uid,
            state = state,
            action = DoseAction.CHECK,
            source = source,
            actionId = actionId,
            previousActionId = null,
            now = now,
        )
        return true
    }

    override suspend fun undo(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
    ): Boolean {
        undoCalls += DoseCall(uid, logicalDay, medicine.id, slot, source)
        val flow = dayState(uid, logicalDay)
        val stateId = DoseIds.stateId(logicalDay, medicine.id, slot)
        val current = flow.value.value.firstOrNull { it.id == stateId }
        if (DoseActionPolicy.undo(current, source) != DoseCommandDecision.APPLY_UNDO) return false
        val actionId = nextActionId()
        val now = clock.instant()
        val updated =
            requireNotNull(current).copy(
                isTaken = false,
                undoneAt = now,
                lastActionId = actionId,
                updatedAt = now,
            )
        flow.value =
            DataEnvelope(
                flow.value.value.filterNot { it.id == stateId } + updated,
                hasPendingWrites = true,
            )
        appendEvent(
            uid = uid,
            state = updated,
            action = DoseAction.UNDO,
            source = source,
            actionId = actionId,
            previousActionId = current.lastActionId,
            now = now,
        )
        return true
    }

    fun emit(
        uid: String,
        logicalDay: LocalDate,
        envelope: DataEnvelope<List<DoseState>>,
    ) {
        dayState(uid, logicalDay).value = envelope
    }

    fun events(uid: String): List<DoseEvent> = historyState(uid).value.value

    fun envelope(
        uid: String,
        logicalDay: LocalDate,
    ): DataEnvelope<List<DoseState>> = dayState(uid, logicalDay).value

    private fun appendEvent(
        uid: String,
        state: DoseState,
        action: DoseAction,
        source: CheckSource,
        actionId: String,
        previousActionId: String?,
        now: Instant,
    ) {
        val history = historyState(uid)
        history.value =
            DataEnvelope(
                history.value.value +
                    DoseEvent(
                        eventId = actionId,
                        ownerUid = uid,
                        action = action,
                        logicalDay = state.logicalDay,
                        medicineId = state.medicineId,
                        medicineNameSnapshot = state.medicineNameSnapshot,
                        slot = state.slot,
                        labelSnapshot = state.labelSnapshot,
                        occurredAt = now,
                        timezoneId = clock.zone.id,
                        source = source,
                        relatedStateId = state.id,
                        previousActionId = previousActionId,
                        syncedAt = now,
                    ),
            )
    }

    private fun dayState(
        uid: String,
        day: LocalDate,
    ): MutableStateFlow<DataEnvelope<List<DoseState>>> =
        statesByUserDay.getOrPut(uid to day) { MutableStateFlow(DataEnvelope(emptyList())) }

    private fun historyState(uid: String): MutableStateFlow<DataEnvelope<List<DoseEvent>>> =
        historyByUser.getOrPut(uid) { MutableStateFlow(DataEnvelope(emptyList())) }

    private fun nextActionId(): String {
        actionNumber += 1
        return "action-$actionNumber"
    }
}

internal class FakeSettingsRepository(
    initial: UserSettings = UserSettings(),
) : SettingsRepository {
    override val localSettings = MutableStateFlow(initial)
    override val syncStatus = MutableStateFlow(DataEnvelope(initial))
    val activatedUids = mutableListOf<String>()
    val updateUids = mutableListOf<String>()
    var clearCount = 0

    override suspend fun activateAccount(uid: String) {
        activatedUids += uid
    }

    override fun observeCloud(uid: String): Flow<DataEnvelope<UserSettings>> = MutableStateFlow(DataEnvelope(localSettings.value))

    override suspend fun update(
        uid: String,
        transform: (UserSettings) -> UserSettings,
    ) {
        updateUids += uid
        val updated = transform(localSettings.value)
        localSettings.value = updated
        syncStatus.value = DataEnvelope(updated)
    }

    override suspend fun clear() {
        clearCount += 1
        localSettings.value = UserSettings()
        syncStatus.value = DataEnvelope(UserSettings(), fromCache = true)
    }
}

internal class FakeCountdownRepository(
    private val clock: Clock,
) : CountdownRepository {
    private val valuesByUser = mutableMapOf<String, MutableStateFlow<DataEnvelope<List<CountdownState>>>>()
    val starts = mutableListOf<CountdownState>()
    var cancelCount = 0
    var restartCount = 0
    var clearCount = 0

    override fun observeActive(uid: String): Flow<DataEnvelope<List<CountdownState>>> = state(uid)

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
        if (state(uid).value.value.any { it.medicineId == medicine.id && it.slot == slot }) return false
        val countdown =
            CountdownState(
                id = CountdownIds.stateId(logicalDay, medicine.id, slot),
                ownerUid = uid,
                logicalDay = logicalDay,
                medicineId = medicine.id,
                slot = slot,
                durationMinutes = durationMinutes,
                startedAt = startedAt,
                targetAt = CountdownLogic.targetAt(startedAt, durationMinutes),
                startedTimezone = clock.zone.id,
                startedSource = source,
                status = CountdownStatus.RUNNING,
                cancelledAt = null,
                completedAt = null,
                lastActionId = actionId,
            )
        starts += countdown
        state(uid).value = DataEnvelope(state(uid).value.value + countdown, hasPendingWrites = true)
        return true
    }

    override suspend fun cancel(
        uid: String,
        state: CountdownState,
        source: CheckSource,
    ): Boolean {
        cancelCount += 1
        remove(uid, state)
        return true
    }

    override suspend fun restart(
        uid: String,
        state: CountdownState,
        durationMinutes: Int,
        source: CheckSource,
    ): Boolean {
        restartCount += 1
        val updated =
            state.copy(
                durationMinutes = durationMinutes,
                startedAt = clock.instant(),
                targetAt = CountdownLogic.targetAt(clock.instant(), durationMinutes),
                lastActionId = "restart-$restartCount",
            )
        this.state(uid).value =
            DataEnvelope(
                this
                    .state(uid)
                    .value.value
                    .filterNot { it.id == state.id } + updated,
                hasPendingWrites = true,
            )
        return true
    }

    override suspend fun clearForDoseCheck(
        uid: String,
        medicineId: String,
        slot: DoseSlot,
        source: CheckSource,
        state: CountdownState?,
    ): Boolean {
        val current =
            this.state(uid).value.value.firstOrNull {
                it.medicineId == medicineId && it.slot == slot
            } ?: state ?: return false
        clearCount += 1
        remove(uid, current)
        return true
    }

    fun emit(
        uid: String,
        envelope: DataEnvelope<List<CountdownState>>,
    ) {
        state(uid).value = envelope
    }

    fun envelope(uid: String): DataEnvelope<List<CountdownState>> = state(uid).value

    private fun remove(
        uid: String,
        countdown: CountdownState,
    ) {
        state(uid).value = DataEnvelope(state(uid).value.value.filterNot { it.id == countdown.id })
    }

    private fun state(uid: String) = valuesByUser.getOrPut(uid) { MutableStateFlow(DataEnvelope(emptyList())) }
}

internal class FakeAccountDataRepository : AccountDataRepository {
    val deletedUids = mutableListOf<String>()

    override suspend fun deleteAll(uid: String) {
        deletedUids += uid
    }
}

internal data class FakeRepositories(
    val bundle: RepositoryBundle,
    val auth: FakeAuthRepository,
    val medicines: FakeMedicineRepository,
    val doses: FakeDoseRepository,
    val countdowns: FakeCountdownRepository,
    val settings: FakeSettingsRepository,
    val accountData: FakeAccountDataRepository,
)

internal fun fakeRepositories(
    session: AuthSession? = null,
    clock: Clock,
    settings: UserSettings = UserSettings(),
): FakeRepositories {
    val auth = FakeAuthRepository(session)
    val medicines = FakeMedicineRepository()
    val doses = FakeDoseRepository(clock)
    val countdowns = FakeCountdownRepository(clock)
    val settingsRepository = FakeSettingsRepository(settings)
    val accountData = FakeAccountDataRepository()
    return FakeRepositories(
        bundle =
            RepositoryBundle(
                auth = auth,
                medicines = medicines,
                doses = doses,
                countdowns = countdowns,
                settings = settingsRepository,
                accountData = accountData,
                clock = clock,
            ),
        auth = auth,
        medicines = medicines,
        doses = doses,
        countdowns = countdowns,
        settings = settingsRepository,
        accountData = accountData,
    )
}
