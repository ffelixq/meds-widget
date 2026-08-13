package io.github.ffelixq.medswidget.widget

import android.app.Application
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import io.github.ffelixq.medswidget.domain.DoseSlot
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DashboardWidgetGlanceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `dashboard preserves dose callback after persistent row rendering`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val row = doseRow(1)
            provideComposable {
                DashboardWidgetContent(
                    snapshot = signedInSnapshot(listOf(row)),
                    availableSize = DpSize(320.dp, 320.dp),
                )
            }

            onNode(
                hasContentDescriptionEqualTo(
                    "Medicine 1, Dose 1, not recorded as taken; tap to mark as taken",
                ),
            ).assertHasRunCallbackClickAction<CheckDoseAction>(
                actionParametersOf(
                    WidgetActionParameters.MEDICINE_ID to row.medicineId,
                    WidgetActionParameters.SLOT to row.slot.wireValue,
                    WidgetActionParameters.SOURCE to "widget_4x4",
                ),
            )
        }

    @Test
    fun `dashboard shows bounded overflow for many rows`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                DashboardWidgetContent(
                    snapshot = signedInSnapshot((1..8).map(::doseRow)),
                    availableSize = DpSize(320.dp, 320.dp),
                )
            }

            onNode(hasTextEqualTo("Dose 6")).assertExists()
            onNode(hasTextEqualTo("Dose 7")).assertDoesNotExist()
            onNode(hasTextEqualTo("+2 more · Open app")).assertExists()
        }

    private fun signedInSnapshot(rows: List<WidgetDoseRow>): WidgetSnapshot =
        WidgetSnapshot(
            ownerUid = "user-a",
            signedIn = true,
            logicalDay = LocalDate.of(2026, 8, 13),
            rows = rows,
        )

    private fun doseRow(index: Int): WidgetDoseRow =
        WidgetDoseRow(
            medicineId = "medicine-$index",
            medicineName = "Medicine $index",
            slot = DoseSlot.AFTERNOON,
            label = "Dose $index",
            isTaken = false,
            checkedAt = null,
        )
}
