package io.github.ffelixq.medswidget.data

import io.github.ffelixq.medswidget.domain.AuthSession
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownAction
import io.github.ffelixq.medswidget.domain.CountdownState
import io.github.ffelixq.medswidget.domain.DataEnvelope
import io.github.ffelixq.medswidget.domain.DoseAction
import io.github.ffelixq.medswidget.domain.DoseEvent
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.DoseState
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.MedicineDraft
import io.github.ffelixq.medswidget.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

interface AuthRepository {
    val session: StateFlow<AuthSession?>
    val isConfigured: Boolean

    suspend fun signInWithEmail(
        email: String,
        password: String,
    )

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
    )

    suspend fun signInWithGoogleIdToken(idToken: String)

    suspend fun sendPasswordReset(email: String)

    suspend fun updateDisplayName(displayName: String)

    suspend fun reauthenticateWithPassword(password: String)

    suspend fun reauthenticateWithGoogleIdToken(idToken: String)

    suspend fun deleteAuthenticationAccount()

    suspend fun signOut()
}

interface MedicineRepository {
    fun observeActive(uid: String): Flow<DataEnvelope<List<Medicine>>>

    fun observeAll(uid: String): Flow<DataEnvelope<List<Medicine>>>

    suspend fun save(
        uid: String,
        draft: MedicineDraft,
    ): String

    suspend fun archive(
        uid: String,
        medicineId: String,
        archived: Boolean,
    )

    suspend fun delete(
        uid: String,
        medicineId: String,
    )
}

interface DoseRepository {
    fun observeDay(
        uid: String,
        logicalDay: LocalDate,
    ): Flow<DataEnvelope<List<DoseState>>>

    fun observeHistory(uid: String): Flow<DataEnvelope<List<DoseEvent>>>

    suspend fun check(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
    ): Boolean

    /**
     * Correlated check used by a widget's optimistic cache. Implementations that can report
     * asynchronous write outcomes should preserve [actionId] and [occurredAt].
     */
    suspend fun checkWithAction(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
        actionId: String,
        occurredAt: Instant,
    ): Boolean = check(uid, logicalDay, medicine, slot, source)

    suspend fun undo(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource = CheckSource.APP,
    ): Boolean
}

interface CountdownRepository {
    /** Active timers include timers from an earlier logical day until explicitly resolved. */
    fun observeActive(uid: String): Flow<DataEnvelope<List<CountdownState>>>

    @Suppress("LongParameterList")
    suspend fun start(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
        actionId: String,
        startedAt: Instant,
        durationMinutes: Int,
    ): Boolean

    suspend fun cancel(
        uid: String,
        state: CountdownState,
        source: CheckSource = CheckSource.APP,
    ): Boolean

    suspend fun restart(
        uid: String,
        state: CountdownState,
        durationMinutes: Int,
        source: CheckSource = CheckSource.APP,
    ): Boolean

    suspend fun clearForDoseCheck(
        uid: String,
        medicineId: String,
        slot: DoseSlot,
        source: CheckSource,
        state: CountdownState? = null,
    ): Boolean
}

data class DoseWriteOutcome(
    val ownerUid: String,
    val actionId: String,
    val medicineId: String,
    val slot: DoseSlot,
    val action: DoseAction,
    val successful: Boolean,
    val errorMessage: String? = null,
)

data class CountdownWriteOutcome(
    val ownerUid: String,
    val actionId: String,
    val medicineId: String,
    val slot: DoseSlot,
    val action: CountdownAction,
    val successful: Boolean,
    val errorMessage: String? = null,
)

interface SettingsRepository {
    val localSettings: StateFlow<UserSettings>
    val syncStatus: StateFlow<DataEnvelope<UserSettings>>

    suspend fun activateAccount(uid: String)

    fun observeCloud(uid: String): Flow<DataEnvelope<UserSettings>>

    suspend fun update(
        uid: String,
        transform: (UserSettings) -> UserSettings,
    )

    suspend fun clear()
}

interface AccountDataRepository {
    suspend fun deleteAll(uid: String)
}

data class RepositoryBundle(
    val auth: AuthRepository,
    val medicines: MedicineRepository,
    val doses: DoseRepository,
    val settings: SettingsRepository,
    val accountData: AccountDataRepository,
    val countdowns: CountdownRepository = UnavailableCountdownRepository(),
    val clock: Clock = Clock.systemDefaultZone(),
)
