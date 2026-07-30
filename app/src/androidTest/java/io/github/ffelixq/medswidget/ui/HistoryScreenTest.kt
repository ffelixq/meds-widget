package io.github.ffelixq.medswidget.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun emptyHistoryExplainsThatNoChecksExist() {
        composeRule.setContent {
            UiTestTheme {
                HistoryScreen(
                    state = HistoryUiState(isLoading = false),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("History").assertIsDisplayed()
        composeRule.onNodeWithText("No checks have been recorded yet.").assertIsDisplayed()
    }

    @Test
    fun historyShowsSnapshotSourceTimezoneAndUndoAudit() {
        val entry =
            HistoryEntry(
                eventId = "check-1",
                logicalDay = LocalDate.of(2026, 7, 29),
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
                    state = HistoryUiState(isLoading = false, entries = listOf(entry)),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Original medicine name").assertIsDisplayed()
        composeRule.onNodeWithText("After lunch").assertIsDisplayed()
        composeRule.onNodeWithText("from 2×2 widget", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Timezone: Asia/Singapore").assertIsDisplayed()
        composeRule.onNodeWithText("Undone", substring = true).assertIsDisplayed()
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
