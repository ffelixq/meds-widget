package io.github.ffelixq.medswidget.widget

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import io.github.ffelixq.medswidget.R
import io.github.ffelixq.medswidget.domain.DisplayTransform
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.ui.MainActivity
import io.github.ffelixq.medswidget.util.TimeFormatting
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SingleMedicineWidgetGlanceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `signed-out widget renders a safe sign-in state`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                SingleMedicineWidgetContent(WidgetSnapshot(), null, 41)
            }

            onNode(hasTextEqualTo("Meds Widget")).assertExists()
            onNode(hasTextEqualTo("Open the app to sign in")).assertExists()
        }

    @Test
    fun `loading widget renders an explicit loading state`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                SingleMedicineWidgetContent(
                    WidgetSnapshot(
                        ownerUid = "user-a",
                        signedIn = true,
                        isLoading = true,
                    ),
                    null,
                    41,
                )
            }

            onNode(hasTextEqualTo("Loading")).assertExists()
            onNode(hasTextEqualTo("Loading medicines…")).assertExists()
            onNode(hasTextEqualTo("Open the app to sign in")).assertDoesNotExist()
        }

    @Test
    fun `missing configuration asks for one medicine`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                SingleMedicineWidgetContent(contentSnapshot(), null, 41)
            }

            onNode(hasTextEqualTo("Choose a medicine")).assertExists()
            onNode(hasText("reconfigure this widget")).assertExists()
        }

    @Test
    fun `configuration from another account is rejected`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                SingleMedicineWidgetContent(
                    contentSnapshot(),
                    SingleWidgetConfiguration(41, "user-b", "medicine-a"),
                    41,
                )
            }

            onNode(hasTextEqualTo("Choose a medicine")).assertExists()
            onNode(hasTextEqualTo("Medicine A")).assertDoesNotExist()
        }

    @Test
    fun `deleted or archived configured medicine renders unavailable state`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                SingleMedicineWidgetContent(
                    contentSnapshot(),
                    SingleWidgetConfiguration(41, "user-a", "missing"),
                    41,
                )
            }

            onNode(hasTextEqualTo("Medicine unavailable")).assertExists()
            onNode(hasText("archived or deleted")).assertExists()
        }

    @Test
    fun `configured widget renders only its selected medicine`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                SingleMedicineWidgetContent(
                    twoMedicineSnapshot(),
                    SingleWidgetConfiguration(41, "user-a", "medicine-a"),
                    41,
                )
            }

            onNode(hasTextEqualTo("Medicine A")).assertExists()
            onNode(hasText("After lunch")).assertExists()
            onNode(hasText("Before bed")).assertExists()
            onNode(hasTextEqualTo("Medicine B")).assertDoesNotExist()
            onNode(hasText("Sleep")).assertDoesNotExist()
        }

    @Test
    fun `separate widget instances retain distinct configurations and action parameters`() {
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                SingleMedicineWidgetContent(
                    twoMedicineSnapshot(),
                    SingleWidgetConfiguration(41, "user-a", "medicine-a"),
                    41,
                )
            }

            onNode(
                hasContentDescriptionEqualTo(
                    context.getString(
                        R.string.widget_dose_not_taken_description,
                        "Medicine A",
                        "After lunch",
                    ),
                ),
            ).assertHasRunCallbackClickAction<CheckDoseAction>(
                actionParametersOf(
                    WidgetActionParameters.MEDICINE_ID to "medicine-a",
                    WidgetActionParameters.SLOT to DoseSlot.AFTERNOON.wireValue,
                    WidgetActionParameters.SOURCE to "widget_2x2",
                    WidgetActionParameters.APP_WIDGET_ID to 41,
                ),
            )
        }
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                SingleMedicineWidgetContent(
                    twoMedicineSnapshot(),
                    SingleWidgetConfiguration(52, "user-a", "medicine-b"),
                    52,
                )
            }

            onNode(
                hasContentDescriptionEqualTo(
                    context.getString(
                        R.string.widget_dose_not_taken_description,
                        "Medicine B",
                        "Sleep",
                    ),
                ),
            ).assertHasRunCallbackClickAction<CheckDoseAction>(
                actionParametersOf(
                    WidgetActionParameters.MEDICINE_ID to "medicine-b",
                    WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
                    WidgetActionParameters.SOURCE to "widget_2x2",
                    WidgetActionParameters.APP_WIDGET_ID to 52,
                ),
            )
        }
    }

    @Test
    fun `afternoon-only medicine renders one actionable row`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val snapshot = contentSnapshot().copy(rows = listOf(contentSnapshot().rows.first()))
            provideComposable {
                SingleMedicineWidgetContent(
                    snapshot,
                    SingleWidgetConfiguration(41, "user-a", "medicine-a"),
                    41,
                )
            }

            onNode(hasText("After lunch")).assertExists()
            onNode(hasText("Before bed")).assertDoesNotExist()
            onAllNodes(hasTextEqualTo("☐")).assertCountEquals(1)
        }

    @Test
    fun `night-only medicine renders one actionable row`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val snapshot = contentSnapshot().copy(rows = listOf(contentSnapshot().rows.last()))
            provideComposable {
                SingleMedicineWidgetContent(
                    snapshot,
                    SingleWidgetConfiguration(41, "user-a", "medicine-a"),
                    41,
                )
            }

            onNode(hasText("After lunch")).assertDoesNotExist()
            onNode(hasText("Before bed")).assertExists()
            onAllNodes(hasTextEqualTo("☐")).assertCountEquals(1)
        }

    @Test
    fun `unchecked widget row checks while checked row opens app instead of undoing`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val checked =
                contentSnapshot().rows.first().copy(
                    isTaken = true,
                    checkedAt = Instant.parse("2026-07-29T05:00:00Z"),
                    checkedTimezone = "Asia/Singapore",
                )
            val snapshot = contentSnapshot().copy(rows = listOf(checked, contentSnapshot().rows.last()))
            provideComposable {
                SingleMedicineWidgetContent(
                    snapshot,
                    SingleWidgetConfiguration(41, "user-a", "medicine-a"),
                    41,
                )
            }

            val checkedDescription =
                context.getString(
                    R.string.widget_dose_taken_description,
                    "Medicine A",
                    "After lunch",
                )
            val uncheckedDescription =
                context.getString(
                    R.string.widget_dose_not_taken_description,
                    "Medicine A",
                    "Before bed",
                )
            onNode(hasContentDescriptionEqualTo(checkedDescription))
                .assertHasStartActivityClickAction(Intent(context, MainActivity::class.java))
            onNode(hasContentDescriptionEqualTo(uncheckedDescription))
                .assertHasRunCallbackClickAction<CheckDoseAction>(
                    actionParametersOf(
                        WidgetActionParameters.MEDICINE_ID to "medicine-a",
                        WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
                        WidgetActionParameters.SOURCE to "widget_2x2",
                        WidgetActionParameters.APP_WIDGET_ID to 41,
                    ),
                )
            onNode(
                hasTextEqualTo(
                    TimeFormatting.compact(
                        context,
                        requireNotNull(checked.checkedAt),
                        checked.checkedTimezone,
                    ),
                ),
            ).assertExists()
        }

    @Test
    fun `long dose label cannot hide a recorded completion time`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val checked =
                contentSnapshot().rows.first().copy(
                    label = "A custom afternoon label that is intentionally very long",
                    isTaken = true,
                    checkedAt = Instant.parse("2026-07-29T05:00:00Z"),
                    checkedTimezone = "Asia/Singapore",
                )
            provideComposable {
                SingleMedicineWidgetContent(
                    contentSnapshot().copy(rows = listOf(checked)),
                    SingleWidgetConfiguration(41, "user-a", "medicine-a"),
                    41,
                )
            }

            onNode(hasTextEqualTo(DisplayTransform.truncate(checked.label, 30))).assertExists()
            onNode(
                hasTextEqualTo(
                    TimeFormatting.compact(
                        context,
                        requireNotNull(checked.checkedAt),
                        checked.checkedTimezone,
                    ),
                ),
            ).assertExists()
        }

    @Test
    fun `long medicine names use the production truncation transform`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val longName = "A medicine name that is intentionally much too long"
            val snapshot =
                contentSnapshot().copy(
                    medicines = listOf(contentSnapshot().medicines.single().copy(name = longName)),
                )
            provideComposable {
                SingleMedicineWidgetContent(
                    snapshot,
                    SingleWidgetConfiguration(41, "user-a", "medicine-a"),
                    41,
                )
            }

            onNode(hasTextEqualTo(DisplayTransform.truncate(longName, 28))).assertExists()
            onNode(hasTextEqualTo(longName)).assertDoesNotExist()
        }

    @Test
    fun `compact sync and cached status render in the header`() {
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                SingleMedicineWidgetContent(
                    contentSnapshot().copy(hasPendingWrites = true, fromCache = true),
                    SingleWidgetConfiguration(41, "user-a", "medicine-a"),
                    41,
                )
            }
            onNode(hasTextEqualTo("Syncing")).assertExists()
        }
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                SingleMedicineWidgetContent(
                    contentSnapshot().copy(fromCache = true),
                    SingleWidgetConfiguration(41, "user-a", "medicine-a"),
                    41,
                )
            }
            onNode(hasTextEqualTo("Cached")).assertExists()
        }
    }

    private fun twoMedicineSnapshot(): WidgetSnapshot {
        val base = contentSnapshot()
        return base.copy(
            medicines =
                base.medicines +
                    WidgetMedicine(
                        id = "medicine-b",
                        name = "Medicine B",
                        afternoonEnabled = false,
                        afternoonLabel = "Afternoon",
                        nightEnabled = true,
                        nightLabel = "Sleep",
                    ),
            rows =
                base.rows +
                    WidgetDoseRow(
                        medicineId = "medicine-b",
                        medicineName = "Medicine B",
                        slot = DoseSlot.NIGHT,
                        label = "Sleep",
                        isTaken = false,
                        checkedAt = null,
                    ),
        )
    }

    private fun contentSnapshot(): WidgetSnapshot =
        WidgetSnapshot(
            ownerUid = "user-a",
            signedIn = true,
            logicalDay = LocalDate.of(2026, 7, 29),
            medicines =
                listOf(
                    WidgetMedicine(
                        id = "medicine-a",
                        name = "Medicine A",
                        afternoonEnabled = true,
                        afternoonLabel = "After lunch",
                        nightEnabled = true,
                        nightLabel = "Before bed",
                    ),
                ),
            rows =
                listOf(
                    WidgetDoseRow(
                        medicineId = "medicine-a",
                        medicineName = "Medicine A",
                        slot = DoseSlot.AFTERNOON,
                        label = "After lunch",
                        isTaken = false,
                        checkedAt = null,
                    ),
                    WidgetDoseRow(
                        medicineId = "medicine-a",
                        medicineName = "Medicine A",
                        slot = DoseSlot.NIGHT,
                        label = "Before bed",
                        isTaken = false,
                        checkedAt = null,
                    ),
                ),
        )
}
