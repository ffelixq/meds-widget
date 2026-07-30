package io.github.ffelixq.medswidget.widget

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetConfigurationStoreTest {
    private lateinit var store: WidgetConfigurationStore

    @Before
    fun setUp() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            store = WidgetConfigurationStore(context)
            store.clearAll()
        }

    @Test
    fun `two widget IDs can select different medicines`() =
        runTest {
            val first = SingleWidgetConfiguration(41, "user-a", "medicine-a")
            val second = SingleWidgetConfiguration(52, "user-a", "medicine-b")

            store.set(first)
            store.set(second)

            assertEquals(first, store.get(41))
            assertEquals(second, store.get(52))
        }

    @Test
    fun `two widget IDs can independently select the same medicine`() =
        runTest {
            val first = SingleWidgetConfiguration(41, "user-a", "medicine-a")
            val second = SingleWidgetConfiguration(60, "user-a", "medicine-a")

            store.set(first)
            store.set(second)

            assertEquals(first, store.get(41))
            assertEquals(second, store.get(60))
        }

    @Test
    fun `reconfiguring one widget does not change another`() =
        runTest {
            store.set(SingleWidgetConfiguration(41, "user-a", "medicine-a"))
            store.set(SingleWidgetConfiguration(52, "user-a", "medicine-b"))

            store.set(SingleWidgetConfiguration(41, "user-a", "medicine-c"))

            assertEquals("medicine-c", store.get(41)?.medicineId)
            assertEquals("medicine-b", store.get(52)?.medicineId)
        }

    @Test
    fun `configuration retains account ownership for account switch isolation`() =
        runTest {
            store.set(SingleWidgetConfiguration(41, "user-a", "medicine-a"))

            val configuration = store.get(41)

            assertEquals("user-a", configuration?.ownerUid)
            assertEquals(41, configuration?.appWidgetId)
        }

    @Test
    fun `removing a widget clears only that widget mapping`() =
        runTest {
            val retained = SingleWidgetConfiguration(52, "user-a", "medicine-b")
            store.set(SingleWidgetConfiguration(41, "user-a", "medicine-a"))
            store.set(retained)

            store.remove(41)

            assertNull(store.get(41))
            assertEquals(retained, store.get(52))
        }

    @Test
    fun `clear all removes mappings during account deletion`() =
        runTest {
            store.set(SingleWidgetConfiguration(41, "user-a", "medicine-a"))
            store.set(SingleWidgetConfiguration(52, "user-a", "medicine-b"))

            store.clearAll()

            assertNull(store.get(41))
            assertNull(store.get(52))
        }

    @Test
    fun `unknown widget ID has missing configuration state`() =
        runTest {
            assertNull(store.get(999))
        }
}
