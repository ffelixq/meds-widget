package io.github.ffelixq.medswidget.widget

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.ui.MainActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SkippedWidgetStateTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `skipped widget row shows not taken and opens app instead of checking`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val row = skippedRow()
            provideComposable {
                WidgetDoseRowContent(
                    row = row,
                    source = CheckSource.WIDGET_2X2,
                    isSkipped = true,
                )
            }

            onNode(hasTextEqualTo("NOT TAKEN")).assertExists()
            onNode(hasTextEqualTo("START TIMER")).assertDoesNotExist()
            onNode(
                hasContentDescriptionEqualTo(
                    "Medicine A, After lunch, not taken; open the app to change this record",
                ),
            ).assertHasStartActivityClickAction(Intent(context, MainActivity::class.java))
        }

    @Test
    fun `all-medicines widget projects skipped state from authoritative keys`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            val row = skippedRow()
            val snapshot =
                WidgetSnapshot(
                    ownerUid = "user-a",
                    signedIn = true,
                    logicalDay = LocalDate.of(2026, 8, 12),
                    rows = listOf(row),
                )
            provideComposable {
                AllMedicinesWidgetContent(
                    snapshot = snapshot,
                    skippedDoseKeys = setOf(widgetDoseKey(row.medicineId, row.slot)),
                )
            }

            onNode(hasTextEqualTo("NOT TAKEN")).assertExists()
            onNode(hasTextEqualTo("START TIMER")).assertDoesNotExist()
        }

    private fun skippedRow(): WidgetDoseRow =
        WidgetDoseRow(
            medicineId = "medicine-a",
            medicineName = "Medicine A",
            slot = DoseSlot.AFTERNOON,
            label = "After lunch",
            isTaken = false,
            checkedAt = null,
            countdownMinutes = 120,
        )
}
