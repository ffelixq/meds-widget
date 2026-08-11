package io.github.ffelixq.medswidget.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShowcaseAppShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun widgetPreviewsLiveInMoreInsteadOfToday() {
        setContent()

        composeRule.onNodeWithText("0 of 2").assertIsDisplayed()
        composeRule.onAllNodesWithText("Widget previews").assertCountEquals(0)

        composeRule.onNodeWithText("More").performClick()

        composeRule.onNodeWithText("Widget Studio").assertIsDisplayed()
        composeRule.onNodeWithText("Widget previews").assertIsDisplayed()
    }

    @Test
    fun pendingDoseOpensShowcaseDetailActions() {
        setContent()

        composeRule.onNodeWithContentDescription("Dose details").performClick()

        composeRule.onNodeWithText("Take now").assertIsDisplayed()
        composeRule.onNodeWithText("Skip dose").assertIsDisplayed()
        composeRule.onNodeWithText("Details").assertIsDisplayed()
    }

    private fun setContent() {
        composeRule.setContent {
            UiTestTheme {
                ShowcaseAppShell(
                    mainState = testMainState(),
                    historyState = HistoryUiState(isLoading = false),
                    onCheck = { _, _ -> },
                    onUndo = {},
                    onSkip = { _, _ -> },
                    onStartCountdown = { _, _ -> },
                    onCancelCountdown = {},
                    onRestartCountdown = {},
                    onRefill = { _, _ -> },
                    onAdd = {},
                    onEdit = {},
                    onOpenDetailedHistory = {},
                    onOpenSettings = {},
                )
            }
        }
    }
}
