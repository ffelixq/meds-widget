package io.github.ffelixq.medswidget.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ExperienceMode(
    val label: String,
) {
    PATIENT("Patient mode"),
    CAREGIVER("Caregiver mode"),
}

enum class AppTextSize(
    val label: String,
    val fontScaleMultiplier: Float,
) {
    SYSTEM("Phone setting", 1.0f),
    LARGE("Large", 1.15f),
    EXTRA_LARGE("Extra large", 1.30f),
}

data class AccessibilityPreferencesState(
    val experienceMode: ExperienceMode = ExperienceMode.CAREGIVER,
    val textSize: AppTextSize = AppTextSize.SYSTEM,
)

class AccessibilityPreferences(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(readState())
    val state: StateFlow<AccessibilityPreferencesState> = mutableState.asStateFlow()
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            mutableState.value = readState()
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setExperienceMode(mode: ExperienceMode) {
        preferences.edit { putString(KEY_MODE, mode.name) }
    }

    fun setTextSize(size: AppTextSize) {
        preferences.edit { putString(KEY_TEXT_SIZE, size.name) }
    }

    private fun readState(): AccessibilityPreferencesState =
        AccessibilityPreferencesState(
            experienceMode =
                runCatching {
                    ExperienceMode.valueOf(
                        preferences.getString(KEY_MODE, ExperienceMode.CAREGIVER.name).orEmpty(),
                    )
                }.getOrDefault(ExperienceMode.CAREGIVER),
            textSize =
                runCatching {
                    AppTextSize.valueOf(
                        preferences.getString(KEY_TEXT_SIZE, AppTextSize.SYSTEM.name).orEmpty(),
                    )
                }.getOrDefault(AppTextSize.SYSTEM),
        )

    companion object {
        private const val PREFERENCES_NAME = "meds-widget-accessibility"
        private const val KEY_MODE = "experience_mode"
        private const val KEY_TEXT_SIZE = "text_size"
    }
}
