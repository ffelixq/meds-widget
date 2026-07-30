package io.github.ffelixq.medswidget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.widget.SingleWidgetConfigurationActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationLifecycleTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(90)

    @Test
    fun applicationStartsAndMainActivitySurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertMainContentAttached(scenario)

            scenario.recreate()

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertMainContentAttached(scenario)
        }
    }

    @Test
    fun widgetConfigurationWithoutWidgetIdFinishesCanceled() {
        val application = ApplicationProvider.getApplicationContext<MedsApplication>()
        val intent = Intent(application, SingleWidgetConfigurationActivity::class.java)

        ActivityScenario
            .launchActivityForResult<SingleWidgetConfigurationActivity>(intent)
            .use { scenario ->
                assertEquals(Lifecycle.State.DESTROYED, scenario.state)
                assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
            }
    }

    @Test
    fun widgetConfigurationRejectsAnIdNotBoundToTheSingleMedicineProvider() {
        val application = ApplicationProvider.getApplicationContext<MedsApplication>()
        val widgetId = 901
        val intent =
            Intent(application, SingleWidgetConfigurationActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

        ActivityScenario
            .launchActivityForResult<SingleWidgetConfigurationActivity>(intent)
            .use { scenario ->
                assertEquals(Lifecycle.State.DESTROYED, scenario.state)
                assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
            }
    }

    private fun assertMainContentAttached(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(content.isAttachedToWindow)
            assertTrue(content.isShown)
            assertTrue(content.childCount > 0)
        }
    }
}
