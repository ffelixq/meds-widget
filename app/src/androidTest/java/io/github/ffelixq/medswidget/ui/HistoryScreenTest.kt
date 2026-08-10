package io.github.ffelixq.medswidget.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ffelixq.medswidget.domain.CheckSource
import io.github.ffelixq.medswidget.domain.DoseSlot
import io.github.ffelixq.medswidget.domain.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateIsVisibleWhileHistoryIsUnavailable() {
        composeRule.setContent {
            UiTestTheme {
                HistoryScreen(
                    state = HistoryUiState(),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Loading history…").assertIsDisplayed()
    }

    @Test
    fun emptyHistoryExplainsThatNoDosesExist() {
        composeRule.setContent {
            UiTestTheme {
                HistoryScreen(
                    state = HistoryUiState(isLoading = false),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("History & adherence").assertIsDisplayed()
        composeRule.onNodeWithText("Choose date").assertIsDisplayed()
        composeRule.onNodeWithText("No taken or skipped doses have been recorded yet.").assertIsDisplayed()
    }

    @Test
    fun historyShowsSnapshotSourceAndUndoAudit() {
        val logicalDay = LocalDate.of(2026, 7, 29)
        val entry =
            HistoryEntry(
                eventId = "check-1",
                logicalDay = logicalDay,
                medicineName = "Original medicine name",
                label = "After lunch",
                slot = DoseSlot.AFTERNOON,
                checkedAt = Instant.parse("2026-07-29T05:00:00Z"),
                checkedTimezone = "Asia/Singapore",
                checkedSource = CheckSource.WIDGET_2X2,
                undoneAt = Instant.parse("2026-07-29T05:30:00Z"),
                undoSource = CheckSource.APP,
            )
        composeRule.setContent {
            UiTestTheme {
                HistoryScreen(
                    state =
                        HistoryUiState(
                            isLoading = false,
                            logicalDay = logicalDay,
                            entries = listOf(entry),
                        ),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Original medicine name").assertIsDisplayed()
        composeRule.onNodeWithText("After lunch").assertIsDisplayed()
        composeRule.onNodeWithText("from 2×2 widget", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Undone", substring = true).assertIsDisplayed()
    }

    @Test
    fun historyMovesOneLogicalDayAtATimeWithoutLongScrolling() {
        val today = LocalDate.of(2026, 7, 29)
        val yesterdayEntry =
            HistoryEntry(
                eventId = "check-yesterday",
                logicalDay = today.minusDays(1),
                medicineName = "Yesterday medicine",
                label = "Before bed",
                slot = DoseSlot.NIGHT,
                checkedAt = Instant.parse("2026-07-28T14:00:00Z"),
                checkedTimezone = "Asia/Singapore",
                checkedSource = CheckSource.APP,
            )
        composeRule.setContent {
            UiTestTheme {
                HistoryScreen(
                    state =
                        HistoryUiState(
                            isLoading = false,
                            logicalDay = today,
                            entries = listOf(yesterdayEntry),
                        ),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("No taken or skipped doses were recorded for this day.").assertIsDisplayed()
        composeRule.onNodeWithTag("history_previous_day").performClick()

        composeRule.onNodeWithText("Yesterday").assertIsDisplayed()
        composeRule.onNodeWithText("Yesterday medicine").assertIsDisplayed()
        composeRule.onNodeWithText("Before bed").assertIsDisplayed()
    }

    @Test
    fun historyShowsListenerErrorAndBackNavigation() {
        var backCount = 0
        composeRule.setContent {
            UiTestTheme {
                HistoryScreen(
                    state =
                        HistoryUiState(
                            isLoading = false,
                            errorMessage = "Showing cached history while offline",
                        ),
                    onBack = { backCount += 1 },
                )
            }
        }

        composeRule
            .onNodeWithText("Showing cached history while offline")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backCount)
    }
}
