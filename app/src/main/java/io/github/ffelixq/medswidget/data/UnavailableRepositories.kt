package io.github.ffelixq.medswidget.data

import io.github.ffelixq.medswidget.domain.AuthSession
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DataEnvelope
import io.github.ffelixq.medswidget.domain.DoseEvent
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.DoseState
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.MedicineDraft
import io.github.ffelixq.medswidget.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

private const val CONFIGURATION_MESSAGE =
    "Firebase is not configured in this build. Add the local google-services.json file and rebuild."

class UnavailableAuthRepository : AuthRepository {
    override val session = MutableStateFlow<AuthSession?>(null)
    override val isConfigured: Boolean = false

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ) = unavailable()

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
    ) = unavailable()

    override suspend fun signInWithGoogleIdToken(idToken: String) = unavailable()

    override suspend fun sendPasswordReset(email: String) = unavailable()

    override suspend fun updateDisplayName(displayName: String) = unavailable()

    override suspend fun reauthenticateWithPassword(password: String) = unavailable()

    override suspend fun reauthenticateWithGoogleIdToken(idToken: String) = unavailable()

    override suspend fun deleteAuthenticationAccount() = unavailable()

    override suspend fun signOut() = Unit
}

class UnavailableMedicineRepository : MedicineRepository {
    override fun observeActive(uid: String): Flow<DataEnvelope<List<Medicine>>> =
        flowOf(DataEnvelope(emptyList(), errorMessage = CONFIGURATION_MESSAGE))

    override fun observeAll(uid: String): Flow<DataEnvelope<List<Medicine>>> = observeActive(uid)

    override suspend fun save(
        uid: String,
        draft: MedicineDraft,
    ): String = unavailable()

    override suspend fun archive(
        uid: String,
        medicineId: String,
        archived: Boolean,
    ) = unavailable()

    override suspend fun delete(
        uid: String,
        medicineId: String,
    ) = unavailable()
}

class UnavailableDoseRepository : DoseRepository {
    override fun observeDay(
        uid: String,
        logicalDay: LocalDate,
    ): Flow<DataEnvelope<List<DoseState>>> = flowOf(DataEnvelope(emptyList(), errorMessage = CONFIGURATION_MESSAGE))

    override fun observeHistory(uid: String): Flow<DataEnvelope<List<DoseEvent>>> =
        flowOf(DataEnvelope(emptyList(), errorMessage = CONFIGURATION_MESSAGE))

    override suspend fun check(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
    ): Boolean = unavailable()

    override suspend fun undo(
        uid: String,
        logicalDay: LocalDate,
        medicine: Medicine,
        slot: DoseSlot,
        source: CheckSource,
    ): Boolean = unavailable()
}

class UnavailableSettingsRepository : SettingsRepository {
    override val localSettings = MutableStateFlow(UserSettings())
    override val syncStatus =
        MutableStateFlow(
            DataEnvelope(
                value = UserSettings(),
                fromCache = true,
                errorMessage = CONFIGURATION_MESSAGE,
            ),
        )

    override suspend fun activateAccount(uid: String) = Unit

    override fun observeCloud(uid: String): Flow<DataEnvelope<UserSettings>> =
        flowOf(DataEnvelope(localSettings.value, errorMessage = CONFIGURATION_MESSAGE))

    override suspend fun update(
        uid: String,
        transform: (UserSettings) -> UserSettings,
    ) {
        val updated = transform(localSettings.value)
        localSettings.value = updated
        syncStatus.value =
            DataEnvelope(
                value = updated,
                fromCache = true,
                errorMessage = CONFIGURATION_MESSAGE,
            )
    }

    override suspend fun clear() {
        localSettings.value = UserSettings()
        syncStatus.value =
            DataEnvelope(
                value = UserSettings(),
                fromCache = true,
                errorMessage = CONFIGURATION_MESSAGE,
            )
    }
}

class UnavailableAccountDataRepository : AccountDataRepository {
    override suspend fun deleteAll(uid: String) = unavailable()
}

private fun unavailable(): Nothing = throw IllegalStateException(CONFIGURATION_MESSAGE)
