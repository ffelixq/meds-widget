package io.github.ffelixq.medswidget.security

import android.app.Activity
import android.app.Application
import android.view.WindowManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class SensitiveWindowProtectionTest {
    @Test
    fun healthWindowBlocksScreenCapture() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        SensitiveWindowProtection.apply(activity)

        val flags = activity.window.attributes.flags
        assertTrue(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
}
