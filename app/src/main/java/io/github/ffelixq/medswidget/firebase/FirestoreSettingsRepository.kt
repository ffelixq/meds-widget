package io.github.ffelixq.medswidget.firebase

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import io.github.ffelixq.medswidget.data.SettingsRepository
import io.github.ffelixq.medswidget.domain.DataEnvelope
import io.github.ffelixq.medswidget.domain.SCHEMA_VERSION
import io.github.ffelixq.medswidget.domain.ThemePreference
import io.github.ffelixq.medswidget.domain.UserSettings
import io.github.ffelixq.medswidget.sync.OutstandingWriteTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

private val Context.settingsDataStore by preferencesDataStore("meds_settings")

class FirestoreSettingsRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope,
    private val outstandingWriteTracker: OutstandingWriteTracker = OutstandingWriteTracker(),
) : SettingsRepository {
    @Volatile
    private var activeUid: String? = null

    private val mutableSyncStatus =
        MutableStateFlow(
            DataEnvelope(
                value = UserSettings(),
                fromCache = true,
            ),
        )
    override val syncStatus: StateFlow<DataEnvelope<UserSettings>> = mutableSyncStatus.asStateFlow()

    override val localSettings: StateFlow<UserSettings> =
        context.settingsDataStore.data
            .map(::fromPreferences)
            .stateIn(scope, SharingStarted.Eagerly, UserSettings())

    override suspend fun activateAccount(uid: String) {
        activeUid = uid
        context.settingsDataStore.edit { values ->
            if (values[OWNER_UID] != uid) {
                values.clear()
                values[OWNER_UID] = uid
                mutableSyncStatus.value =
                    DataEnvelope(
                        value = UserSettings(),
                        fromCache = true,
                    )
            }
        }
    }

    override fun observeCloud(uid: String): Flow<DataEnvelope<UserSettings>> =
        callbackFlow {
            val registration =
                FirestorePaths
                    .settings(firestore, uid)
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                        if (activeUid != uid) return@addSnapshotListener
                        if (error != null) {
                            val envelope =
                                DataEnvelope(
                                    value = localSettings.value,
                                    fromCache = true,
                                    errorMessage = "Settings could not be refreshed.",
                                )
                            mutableSyncStatus.value = envelope
                            trySend(envelope)
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            val settings = snapshot.toSettings(localSettings.value)
                            launch { persist(uid, settings) }
                            val envelope =
                                DataEnvelope(
                                    value = settings,
                                    fromCache = snapshot.metadata.isFromCache,
                                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                                )
                            mutableSyncStatus.value = envelope
                            trySend(envelope)
                        } else {
                            val envelope =
                                DataEnvelope(
                                    value = localSettings.value,
                                    fromCache = snapshot?.metadata?.isFromCache == true,
                                )
                            mutableSyncStatus.value = envelope
                            trySend(envelope)
                        }
                    }
            awaitClose { registration.remove() }
        }

    override suspend fun update(
        uid: String,
        transform: (UserSettings) -> UserSettings,
    ) {
        activateAccount(uid)
        val updated = transform(localSettings.value).copy(timezoneId = ZoneId.systemDefault().id)
        require(updated.displayName.isNotBlank()) { "Complete your display name before changing settings." }
        persist(uid, updated)
        mutableSyncStatus.value =
            DataEnvelope(
                value = updated,
                fromCache = true,
                hasPendingWrites = true,
            )
        dispatchWrite(uid, updated)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun dispatchWrite(
        uid: String,
        updated: UserSettings,
    ) {
        val outstandingTicket = outstandingWriteTracker.begin(uid)
        try {
            FirestorePaths
                .settings(firestore, uid)
                .set(
                    mapOf(
                        "ownerUid" to uid,
                        "resetMinutesAfterMidnight" to updated.resetMinutesAfterMidnight,
                        "timezoneId" to updated.timezoneId,
                        "displayName" to updated.displayName,
                        "themePreference" to updated.themePreference.wireValue,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "schemaVersion" to SCHEMA_VERSION,
                    ),
                ).addOnCompleteListener { completedTask ->
                    try {
                        if (!completedTask.isSuccessful) recordWriteFailure(uid, updated)
                    } finally {
                        outstandingWriteTracker.complete(outstandingTicket)
                    }
                }
        } catch (error: RuntimeException) {
            try {
                recordWriteFailure(uid, updated)
            } finally {
                outstandingWriteTracker.complete(outstandingTicket)
            }
            throw error
        }
    }

    private fun recordWriteFailure(
        uid: String,
        settings: UserSettings,
    ) {
        if (activeUid == uid) {
            mutableSyncStatus.value =
                DataEnvelope(
                    value = settings,
                    fromCache = true,
                    errorMessage = "Settings are saved on this device but could not be synchronised.",
                )
        }
    }

    override suspend fun clear() {
        activeUid = null
        context.settingsDataStore.edit { it.clear() }
        mutableSyncStatus.value =
            DataEnvelope(
                value = UserSettings(),
                fromCache = true,
            )
    }

    private suspend fun persist(
        uid: String,
        settings: UserSettings,
    ) {
        context.settingsDataStore.edit { values ->
            if (values[OWNER_UID] != uid) return@edit
            values[RESET_MINUTES] = settings.resetMinutesAfterMidnight
            values[TIMEZONE] = settings.timezoneId
            values[DISPLAY_NAME] = settings.displayName
            values[THEME] = settings.themePreference.wireValue
        }
    }

    private fun fromPreferences(values: Preferences): UserSettings =
        UserSettings(
            resetMinutesAfterMidnight = values[RESET_MINUTES] ?: 0,
            timezoneId = values[TIMEZONE] ?: ZoneId.systemDefault().id,
            displayName = values[DISPLAY_NAME].orEmpty(),
            themePreference = ThemePreference.fromWire(values[THEME].orEmpty()),
        )

    private companion object {
        val OWNER_UID = stringPreferencesKey("owner_uid")
        val RESET_MINUTES = intPreferencesKey("reset_minutes")
        val TIMEZONE = stringPreferencesKey("timezone")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val THEME = stringPreferencesKey("theme")
    }
}
