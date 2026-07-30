package io.github.ffelixq.medswidget.util

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class TimeFormattingTest {
    @Test
    fun `compact time uses the recorded timezone instead of the device timezone`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        val instant = Instant.parse("2026-07-29T05:00:00Z")

        val singapore = TimeFormatting.compact(context, instant, "Asia/Singapore")
        val losAngeles = TimeFormatting.compact(context, instant, "America/Los_Angeles")

        assertEquals("13:00", singapore)
        assertEquals("22:00", losAngeles)
    }
}
