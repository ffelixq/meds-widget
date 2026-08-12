package io.github.ffelixq.medswidget.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShowcaseAppShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun widgetPreviewsLiveInCaregiverMoreInsteadOfToday() {
        setContent()

        composeRule.onNodeWithText("0 of 2").assertIsDisplayed()
        composeRule.onAllNodesWithText("Widget previews").assertCountEquals(0)

        composeRule.onNodeWithText("More").performClick()

        composeRule.onNodeWithText("Widget Studio").assertIsDisplayed()
        composeRule.onNodeWithText("Widget previews").assertIsDisplayed()
    }

    @Test
    fun pendingDoseOpensPlainLanguageDetailActions() {
        setContent()

        composeRule.onNodeWithContentDescription("Medicine details").performClick()

        composeRule.onNodeWithText("✓  I TOOK IT").assertIsDisplayed()
        composeRule.onNodeWithText("Remind me later").assertIsDisplayed()
        composeRule.onNodeWithText("I am not taking this dose").assertIsDisplayed()
        composeRule.onNodeWithText("Details").assertIsDisplayed()
    }

    @Test
    fun patientModeHidesMedicineSetupAndKeepsLargeDailyActions() {
        setContent(mode = ExperienceMode.PATIENT)

        composeRule.onNodeWithText("✓  I TOOK IT").assertIsDisplayed()
        composeRule.onNodeWithText("Remind me later").assertIsDisplayed()
        composeRule.onAllNodesWithText("Medicines").assertCountEquals(0)

        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Open caregiver tools").assertIsDisplayed()
        composeRule.onAllNodesWithText("Widget Studio").assertCountEquals(0)
    }

    @Test
    fun patientModeCanSwitchBackToCaregiverTools() {
        var selectedMode: ExperienceMode? = null
        setContent(
            mode = ExperienceMode.PATIENT,
            onExperienceMode = { selectedMode = it },
        )

        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Open caregiver tools").performClick()

        assertEquals(ExperienceMode.CAREGIVER, selectedMode)
    }

    @Test
    fun remindLaterOffersSimpleChoices() {
        setContent(mode = ExperienceMode.PATIENT)

        composeRule.onNodeWithText("Remind me later").performClick()

        composeRule.onNodeWithText("In 15 minutes").assertIsDisplayed()
        composeRule.onNodeWithText("In 30 minutes").assertIsDisplayed()
        composeRule.onNodeWithText("In 1 hour").assertIsDisplayed()
    }

    private fun setContent(
        mode: ExperienceMode = ExperienceMode.CAREGIVER,
        onExperienceMode: (ExperienceMode) -> Unit = {},
    ) {
        composeRule.setContent {
            UiTestTheme {
                ShowcaseAppShell(
                    mainState = testMainState(),
                    historyState = HistoryUiState(isLoading = false),
                    accessibilityState = AccessibilityPreferencesState(experienceMode = mode),
                    onCheck = { _, _ -> },
                    onUndo = {},
                    onSkip = { _, _ -> },
                    onStartCountdown = { _, _ -> },
                    onCancelCountdown = {},
                    onRestartCountdown = {},
                    onRemindLater = { _, _ -> },
                    onExperienceMode = onExperienceMode,
                    onTextSize = {},
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
