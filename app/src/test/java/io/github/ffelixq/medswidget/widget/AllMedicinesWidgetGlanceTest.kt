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
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
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
class AllMedicinesWidgetGlanceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `signed-out widget renders a safe sign-in state`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { AllMedicinesWidgetContent(WidgetSnapshot()) }

            onNode(hasTextEqualTo("Today’s medicines")).assertExists()
            onNode(hasTextEqualTo("Open the app to sign in")).assertExists()
        }

    @Test
    fun `loading widget renders an explicit loading state without empty progress`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                AllMedicinesWidgetContent(
                    WidgetSnapshot(
                        ownerUid = "user-a",
                        signedIn = true,
                        isLoading = true,
                    ),
                )
            }

            onNode(hasTextEqualTo("Loading")).assertExists()
            onNode(hasTextEqualTo("Loading medicines…")).assertExists()
            onNode(hasTextEqualTo("0/0")).assertDoesNotExist()
        }

    @Test
    fun `signed-in empty widget explains how to add a medicine`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                AllMedicinesWidgetContent(
                    WidgetSnapshot(
                        ownerUid = "user-a",
                        signedIn = true,
                        logicalDay = LocalDate.of(2026, 7, 29),
                    ),
                )
            }

            onNode(hasTextEqualTo("0/0")).assertExists()
            onNode(hasText("No active medicines")).assertExists()
        }

    @Test
    fun `multiple medicines and every enabled dose appear with accurate progress`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { AllMedicinesWidgetContent(contentSnapshot()) }

            onNode(hasTextEqualTo("1/3")).assertExists()
            onAllNodes(hasTextEqualTo("Medicine A")).assertCountEquals(2)
            onNode(hasTextEqualTo("Medicine B")).assertExists()
            onNode(hasText("After lunch")).assertExists()
            onNode(hasText("Before bed")).assertExists()
            onNode(hasText("Sleep")).assertExists()
            val checked = contentSnapshot().rows.first()
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
    fun `all widget renders compact standard and larger resized bounds`() {
        listOf(
            DpSize(250.dp, 110.dp),
            DpSize(330.dp, 150.dp),
            DpSize(500.dp, 220.dp),
        ).forEach { size ->
            runGlanceAppWidgetUnitTest {
                setContext(context)
                provideComposable { AllMedicinesWidgetContent(contentSnapshot(), size) }
                onNode(hasTextEqualTo("Today’s medicines")).assertExists()
                onNode(hasTextEqualTo("1/3")).assertExists()
            }
        }
    }

    @Test
    fun `all widget exposes separate countdown start and dose check actions`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val row = contentSnapshot().rows.last().copy(countdownMinutes = 90)
            provideComposable { AllMedicinesWidgetContent(contentSnapshot().copy(rows = listOf(row))) }
            val parameters =
                actionParametersOf(
                    WidgetActionParameters.MEDICINE_ID to row.medicineId,
                    WidgetActionParameters.SLOT to row.slot.wireValue,
                    WidgetActionParameters.SOURCE to "widget_4x2",
                )
            onNode(hasTextEqualTo("Start 1h 30m"))
                .assertHasRunCallbackClickAction<StartCountdownAction>(parameters)
            onNode(
                hasContentDescriptionEqualTo(
                    context.getString(
                        R.string.widget_dose_not_taken_description,
                        row.medicineName,
                        row.label,
                    ),
                ),
            ).assertHasRunCallbackClickAction<CheckDoseAction>(parameters)
        }

    @Test
    fun `all-medicines content uses a lazy column and retains every row`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val manyRows =
                (1..12).map { index ->
                    WidgetDoseRow(
                        medicineId = "medicine-$index",
                        medicineName = "Medicine $index",
                        slot = DoseSlot.AFTERNOON,
                        label = "Dose $index",
                        isTaken = false,
                        checkedAt = null,
                    )
                }
            provideComposable {
                AllMedicinesWidgetContent(
                    WidgetSnapshot(
                        ownerUid = "user-a",
                        signedIn = true,
                        logicalDay = LocalDate.of(2026, 7, 29),
                        rows = manyRows,
                    ),
                )
            }

            onNode(
                GlanceNodeMatcher<MappedNode>("is a LazyColumn") {
                    it.value.emittable.javaClass.simpleName == "EmittableLazyColumn"
                },
            ).assertExists()
            onAllNodes(hasTextEqualTo("☐")).assertCountEquals(12)
            onNode(hasTextEqualTo("Dose 12")).assertExists()
        }

    @Test
    fun `unchecked row checks while checked row opens app instead of undoing`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { AllMedicinesWidgetContent(contentSnapshot()) }

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
            val secondMedicineDescription =
                context.getString(
                    R.string.widget_dose_not_taken_description,
                    "Medicine B",
                    "Sleep",
                )
            onNode(hasContentDescriptionEqualTo(checkedDescription))
                .assertHasStartActivityClickAction(Intent(context, MainActivity::class.java))
            onNode(hasContentDescriptionEqualTo(uncheckedDescription))
                .assertHasRunCallbackClickAction<CheckDoseAction>(
                    actionParametersOf(
                        WidgetActionParameters.MEDICINE_ID to "medicine-a",
                        WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
                        WidgetActionParameters.SOURCE to "widget_4x2",
                    ),
                )
            onNode(hasContentDescriptionEqualTo(secondMedicineDescription)).assertExists()
            onNode(hasContentDescriptionEqualTo(secondMedicineDescription))
                .assertHasRunCallbackClickAction<CheckDoseAction>(
                    actionParametersOf(
                        WidgetActionParameters.MEDICINE_ID to "medicine-b",
                        WidgetActionParameters.SLOT to DoseSlot.NIGHT.wireValue,
                        WidgetActionParameters.SOURCE to "widget_4x2",
                    ),
                )
        }

    @Test
    fun `long medicine names use the production row truncation transform`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val longName = "A medicine name that is intentionally much too long"
            val snapshot =
                contentSnapshot().copy(
                    rows =
                        listOf(
                            contentSnapshot().rows.first().copy(medicineName = longName),
                        ),
                )
            provideComposable { AllMedicinesWidgetContent(snapshot) }

            onNode(hasTextEqualTo(DisplayTransform.truncate(longName, 26))).assertExists()
            onNode(hasTextEqualTo(longName)).assertDoesNotExist()
        }

    @Test
    fun `long dose label cannot hide completion time in all-medicines rows`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val checked =
                contentSnapshot().rows.first().copy(
                    label = "A custom afternoon label that is intentionally very long",
                )
            provideComposable {
                AllMedicinesWidgetContent(contentSnapshot().copy(rows = listOf(checked)))
            }

            onNode(hasTextEqualTo(DisplayTransform.truncate(checked.label, 34))).assertExists()
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
    fun `header prioritizes syncing and otherwise reports cached state`() {
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                AllMedicinesWidgetContent(
                    contentSnapshot().copy(hasPendingWrites = true, fromCache = true),
                )
            }
            onNode(hasText("Syncing")).assertExists()
            onNode(hasText("Cached")).assertDoesNotExist()
        }
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                AllMedicinesWidgetContent(contentSnapshot().copy(errorMessage = "Offline"))
            }
            onNode(hasText("Cached")).assertExists()
        }
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
                    WidgetMedicine(
                        id = "medicine-b",
                        name = "Medicine B",
                        afternoonEnabled = false,
                        afternoonLabel = "Afternoon",
                        nightEnabled = true,
                        nightLabel = "Sleep",
                    ),
                ),
            rows =
                listOf(
                    WidgetDoseRow(
                        medicineId = "medicine-a",
                        medicineName = "Medicine A",
                        slot = DoseSlot.AFTERNOON,
                        label = "After lunch",
                        isTaken = true,
                        checkedAt = Instant.parse("2026-07-29T05:00:00Z"),
                        checkedTimezone = "Asia/Singapore",
                    ),
                    WidgetDoseRow(
                        medicineId = "medicine-a",
                        medicineName = "Medicine A",
                        slot = DoseSlot.NIGHT,
                        label = "Before bed",
                        isTaken = false,
                        checkedAt = null,
                    ),
                    WidgetDoseRow(
                        medicineId = "medicine-b",
                        medicineName = "Medicine B",
                        slot = DoseSlot.NIGHT,
                        label = "Sleep",
                        isTaken = false,
                        checkedAt = null,
                    ),
                ),
        )
}
