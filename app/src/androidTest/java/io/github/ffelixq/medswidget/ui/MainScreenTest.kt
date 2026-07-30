package io.github.ffelixq.medswidget.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.CompletionProgress
import io.github.ffelixq.medswidget.domain.DoseRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MainScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateIsVisibleWhileMedicinesAreUnavailable() {
        composeRule.setContent {
            UiTestTheme {
                MainScreen(
                    state = MainUiState(),
                    onCheck = { _, _ -> },
                    onUndo = {},
                    onAdd = {},
                    onEdit = {},
                    onHistory = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Loading medicines…").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsProgressAndBothAddAffordancesWork() {
        var addCount = 0
        composeRule.setContent {
            UiTestTheme {
                MainScreen(
                    state =
                        MainUiState(
                            isLoading = false,
                            logicalDay = LocalDate.of(2026, 7, 29),
                            progress = CompletionProgress(0, 0),
                        ),
                    onCheck = { _, _ -> },
                    onUndo = {},
                    onAdd = { addCount += 1 },
                    onEdit = {},
                    onHistory = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("0 of 0 completed").assertIsDisplayed()
        composeRule.onNodeWithText("No medicines yet").assertIsDisplayed()
        composeRule.onNodeWithText("Add medicine").performClick()
        composeRule.onNodeWithContentDescription("Add medicine").performClick()

        assertEquals(2, addCount)
    }

    @Test
    fun appDoseCheckUsesAppSourceAndExposesAccessibleStatus() {
        val row = testDoseRow()
        var checkedRow: DoseRow? = null
        var checkedSource: CheckSource? = null
        composeRule.setContent {
            UiTestTheme {
                MainScreen(
                    state = testMainState(rows = listOf(row)),
                    onCheck = { value, source ->
                        checkedRow = value
                        checkedSource = source
                    },
                    onUndo = {},
                    onAdd = {},
                    onEdit = {},
                    onHistory = {},
                    onSettings = {},
                )
            }
        }

        composeRule
            .onNodeWithTag("app_dose_${row.stateId}")
            .assertContentDescriptionEquals("Medicine A, After lunch, not taken")
            .performClick()

        assertSame(row, checkedRow)
        assertEquals(CheckSource.APP, checkedSource)
    }

    @Test
    fun checkedAppDoseRequiresConfirmationBeforeUndo() {
        val row = testDoseRow(isTaken = true)
        var undone: DoseRow? = null
        composeRule.setContent {
            UiTestTheme {
                MainScreen(
                    state = testMainState(rows = listOf(row)),
                    onCheck = { _, _ -> },
                    onUndo = { undone = it },
                    onAdd = {},
                    onEdit = {},
                    onHistory = {},
                    onSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("app_dose_${row.stateId}").performClick()
        composeRule.onNodeWithText("Undo this check?").assertIsDisplayed()
        assertNull(undone)

        composeRule.onNodeWithText("Undo check").performClick()

        assertSame(row, undone)
    }

    @Test
    fun liveSinglePreviewChecksWithPreviewSourceAndAllPreviewIsScrollable() {
        val medicine = testMedicine()
        val rows =
            listOf(
                testDoseRow(medicine = medicine),
                testDoseRow(medicine = medicine, slot = io.github.ffelixq.medswidget.domain.DoseSlot.NIGHT),
            )
        val checks = mutableListOf<Pair<DoseRow, CheckSource>>()
        composeRule.setContent {
            UiTestTheme {
                MainScreen(
                    state = testMainState(medicine, rows),
                    onCheck = { row, source ->
                        checks += row to source
                    },
                    onUndo = {},
                    onAdd = {},
                    onEdit = {},
                    onHistory = {},
                    onSettings = {},
                )
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)

        composeRule
            .onNodeWithTag("preview_single_${rows.first().stateId}")
            .performClick()
        composeRule.onNodeWithTag("preview_all_list").performScrollTo()
        composeRule
            .onNodeWithTag("preview_all_${rows.first().stateId}")
            .performClick()

        assertEquals(
            listOf(
                rows.first() to CheckSource.APP_PREVIEW,
                rows.first() to CheckSource.APP_PREVIEW,
            ),
            checks,
        )
        composeRule.onNodeWithTag("preview_all_list").assert(hasScrollAction())
        composeRule.onNodeWithText("All medicines · 4×2 preview  0/2").assertIsDisplayed()
    }

    @Test
    fun checkedPreviewCannotRequestUndoOrAnotherCheck() {
        val checked = testDoseRow(isTaken = true)
        var checkCount = 0
        var undoCount = 0
        composeRule.setContent {
            UiTestTheme {
                MainScreen(
                    state = testMainState(rows = listOf(checked)),
                    onCheck = { _, _ -> checkCount += 1 },
                    onUndo = { undoCount += 1 },
                    onAdd = {},
                    onEdit = {},
                    onHistory = {},
                    onSettings = {},
                )
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)

        composeRule
            .onNodeWithTag("preview_single_${checked.stateId}")
            .performClick()

        assertEquals(0, checkCount)
        assertEquals(0, undoCount)
    }

    @Test
    fun historyAndSettingsActionsInvokeNavigationCallbacks() {
        var historyCount = 0
        var settingsCount = 0
        composeRule.setContent {
            UiTestTheme {
                MainScreen(
                    state =
                        MainUiState(
                            isLoading = false,
                            logicalDay = LocalDate.of(2026, 7, 29),
                        ),
                    onCheck = { _, _ -> },
                    onUndo = {},
                    onAdd = {},
                    onEdit = {},
                    onHistory = { historyCount += 1 },
                    onSettings = { settingsCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()

        assertEquals(1, historyCount)
        assertEquals(1, settingsCount)
    }
}
