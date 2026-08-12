package io.github.ffelixq.medswidget.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccessibilityPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("meds-widget-accessibility", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `experience mode updates observable state immediately and persists`() {
        val preferences = AccessibilityPreferences(context)

        assertEquals(ExperienceMode.CAREGIVER, preferences.state.value.experienceMode)

        preferences.setExperienceMode(ExperienceMode.PATIENT)

        assertEquals(ExperienceMode.PATIENT, preferences.state.value.experienceMode)
        assertEquals(
            ExperienceMode.PATIENT.name,
            context
                .getSharedPreferences("meds-widget-accessibility", Context.MODE_PRIVATE)
                .getString("experience_mode", null),
        )
    }

    @Test
    fun `text size updates observable state immediately and persists`() {
        val preferences = AccessibilityPreferences(context)

        preferences.setTextSize(AppTextSize.EXTRA_LARGE)

        assertEquals(AppTextSize.EXTRA_LARGE, preferences.state.value.textSize)
        assertEquals(
            AppTextSize.EXTRA_LARGE.name,
            context
                .getSharedPreferences("meds-widget-accessibility", Context.MODE_PRIVATE)
                .getString("text_size", null),
        )
    }
}
