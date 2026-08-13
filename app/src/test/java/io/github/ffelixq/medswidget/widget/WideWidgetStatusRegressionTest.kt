package io.github.ffelixq.medswidget.widget

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CountdownState
import io.github.ffelixq.medswidget.domain.CountdownStatus
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.ui.MainActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WideWidgetStatusRegressionTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `4x2 start timer stays distinct from dose check`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val row = pendingRow().copy(countdownMinutes = 120)
            provideComposable {
                WidgetDoseRowContent(
                    row = row,
                    source = CheckSource.WIDGET_4X2,
                    showMedicineName = true,
                    spec = WidgetLayoutSpec.forSize(DpSize(320.dp, 150.dp), WidgetKind.ALL),
                    now = Instant.parse("2026-08-13T02:00:00Z"),
                )
            }
            val parameters =
                actionParametersOf(
                    WidgetActionParameters.MEDICINE_ID to row.medicineId,
                    WidgetActionParameters.SLOT to row.slot.wireValue,
                    WidgetActionParameters.SOURCE to CheckSource.WIDGET_4X2.wireValue,
                )

            onNode(hasTextEqualTo("START TIMER"))
                .assertHasRunCallbackClickAction<StartCountdownAction>(parameters)
            onNode(
                hasContentDescriptionEqualTo(
                    "Medicine A, After lunch, not recorded as taken; tap to mark as taken",
                ),
            ).assertHasRunCallbackClickAction<CheckDoseAction>(parameters)
        }

    @Test
    fun `4x4 running timer remains actionable without becoming a dose check`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val row =
                pendingRow().copy(
                    countdownMinutes = 120,
                    countdown =
                        CountdownState(
                            id = "2026-08-13_medicine-a_afternoon",
                            ownerUid = "user-a",
                            logicalDay = LocalDate.of(2026, 8, 13),
                            medicineId = "medicine-a",
                            slot = DoseSlot.AFTERNOON,
                            durationMinutes = 120,
                            startedAt = Instant.parse("2026-08-13T02:00:00Z"),
                            targetAt = Instant.parse("2026-08-13T04:00:00Z"),
                            startedTimezone = "Asia/Singapore",
                            startedSource = CheckSource.WIDGET_4X4,
                            status = CountdownStatus.RUNNING,
                            cancelledAt = null,
                            completedAt = null,
                            lastActionId = "action-a",
                        ),
                )
            provideComposable {
                WidgetDoseRowContent(
                    row = row,
                    source = CheckSource.WIDGET_4X4,
                    showMedicineName = true,
                    spec = WidgetLayoutSpec.forSize(DpSize(320.dp, 300.dp), WidgetKind.ALL),
                    now = Instant.parse("2026-08-13T02:30:00Z"),
                )
            }

            onNode(hasTextEqualTo("WAIT 1h 30m"))
                .assertHasStartActivityClickAction(Intent(context, MainActivity::class.java))
        }

    @Test
    fun `wide taken status remains visible`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                WidgetDoseRowContent(
                    row =
                        pendingRow().copy(
                            isTaken = true,
                            checkedAt = Instant.parse("2026-08-13T02:05:00Z"),
                            checkedTimezone = "Asia/Singapore",
                        ),
                    source = CheckSource.WIDGET_4X2,
                    showMedicineName = true,
                    spec = WidgetLayoutSpec.forSize(DpSize(320.dp, 150.dp), WidgetKind.ALL),
                )
            }

            onNode(hasTextEqualTo("TAKEN")).assertExists()
        }

    private fun pendingRow(): WidgetDoseRow =
        WidgetDoseRow(
            medicineId = "medicine-a",
            medicineName = "Medicine A",
            slot = DoseSlot.AFTERNOON,
            label = "After lunch",
            isTaken = false,
            checkedAt = null,
        )
}
