package io.github.ffelixq.medswidget.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ffelixq.medswidget.domain.ThemePreference
import io.github.ffelixq.medswidget.domain.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resetTimeAndThemeControlsInvokeCallbacksWithParsedValues() {
        var resetMinutes: Int? = null
        var theme: ThemePreference? = null
        setSettingsContent(
            onResetTime = { resetMinutes = it },
            onTheme = { theme = it },
        )

        composeRule.onNodeWithTag("reset_hour").performTextReplacement("6")
        composeRule.onNodeWithTag("reset_minute").performTextReplacement("45")
        composeRule.onNodeWithText("Save reset time").performClick()
        composeRule.onNodeWithText("Dark").performScrollTo().performClick()

        assertEquals(405, resetMinutes)
        assertEquals(ThemePreference.DARK, theme)
        composeRule
            .onNodeWithText("Current timezone: Asia/Singapore")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun themeOptionsExposeOneSelectableRadioAction() {
        var selectedTheme: ThemePreference? = null
        setSettingsContent(onTheme = { selectedTheme = it })

        val system = composeRule.onNodeWithTag("theme_system")
        system
            .assertIsSelected()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule
            .onNodeWithTag("theme_dark")
            .assertIsNotSelected()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performClick()

        composeRule
            .onAllNodes(
                hasClickAction() and hasAnyAncestor(hasTestTag("theme_dark")),
                useUnmergedTree = true,
            ).assertCountEquals(0)
        assertEquals(ThemePreference.DARK, selectedTheme)
    }

    @Test
    fun displayNameAndSignOutControlsInvokeAccountCallbacks() {
        var displayName: String? = null
        var signOutCount = 0
        setSettingsContent(
            onDisplayName = { displayName = it },
            onSignOut = { signOutCount += 1 },
        )

        composeRule
            .onNodeWithTag("settings_display_name")
            .performScrollTo()
            .performTextReplacement("Updated Person")
        composeRule.onNodeWithText("Save display name").performScrollTo().performClick()
        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()

        assertEquals("Updated Person", displayName)
        assertEquals(1, signOutCount)
    }

    @Test
    fun passwordAccountDeletionRequiresExplicitDialogConfirmation() {
        var deletedPassword: String? = null
        setSettingsContent(
            providers = setOf("password"),
            onDeletePassword = { deletedPassword = it },
        )

        composeRule.onNodeWithText("Delete account").performScrollTo().performClick()
        composeRule.onNodeWithText("Permanently delete account?").assertIsDisplayed()
        assertNull(deletedPassword)
        composeRule
            .onNodeWithTag("delete_account_password")
            .performTextReplacement("current-password")
        composeRule.onAllNodesWithText("Delete account").let { buttons ->
            buttons[buttons.fetchSemanticsNodes().lastIndex].performClick()
        }

        assertEquals("current-password", deletedPassword)
    }

    @Test
    fun googleAccountDeletionUsesGoogleReauthenticationCallback() {
        var googleDeleteCount = 0
        setSettingsContent(
            providers = setOf("google.com"),
            onDeleteGoogle = { googleDeleteCount += 1 },
        )

        composeRule.onNodeWithText("Delete account").performScrollTo().performClick()
        composeRule
            .onNodeWithText("You will be asked to sign in with Google again.")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Delete account").let { buttons ->
            buttons[buttons.fetchSemanticsNodes().lastIndex].performClick()
        }

        assertEquals(1, googleDeleteCount)
    }

    @Test
    fun privacyAndSyncMessagesAreVisible() {
        setSettingsContent(
            message = "Display name updated.",
            errorMessage = "Waiting for connectivity",
        )

        composeRule
            .onNodeWithText("Widget content is visible on your unlocked home screen.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Display name updated.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Waiting for connectivity")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun accountDeletionProgressDisablesNavigationAndAccountActions() {
        var backCount = 0
        var signOutCount = 0
        var passwordDeleteCount = 0
        setSettingsContent(
            isDeletingAccount = true,
            onBack = { backCount += 1 },
            onSignOut = { signOutCount += 1 },
            onDeletePassword = { passwordDeleteCount += 1 },
        )

        composeRule.onNodeWithTag("account_deletion_progress").assertIsDisplayed()
        composeRule.onNodeWithText("Deleting account…").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsNotEnabled()
        composeRule.onNodeWithText("Sign out").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Delete account").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Save reset time").assertIsNotEnabled()

        assertEquals(0, backCount)
        assertEquals(0, signOutCount)
        assertEquals(0, passwordDeleteCount)
    }

    @Test
    fun blockingDeletionScreenDoesNotExposeAuthenticationOrAccountActions() {
        composeRule.setContent {
            UiTestTheme {
                AccountDeletionProgressScreen()
            }
        }

        composeRule.onNodeWithTag("account_deletion_blocking_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Deleting account…").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").assertIsNotDisplayed()
        composeRule.onNodeWithText("Sign out").assertIsNotDisplayed()
        composeRule.onNodeWithText("Delete account").assertIsNotDisplayed()
    }

    @Test
    fun editedAccountAndResetFieldsSurviveSavedStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            UiTestTheme {
                SettingsScreen(
                    state = settingsState(),
                    onBack = {},
                    onResetTime = {},
                    onTheme = {},
                    onDisplayName = {},
                    onSignOut = {},
                    onDeletePasswordAccount = {},
                    onDeleteGoogleAccount = {},
                )
            }
        }
        composeRule.onNodeWithTag("reset_hour").performTextReplacement("5")
        composeRule.onNodeWithTag("reset_minute").performTextReplacement("30")
        composeRule
            .onNodeWithTag("settings_display_name")
            .performScrollTo()
            .performTextReplacement("Restored Person")
        composeRule.waitForIdle()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("reset_hour").assertTextContains("5")
        composeRule.onNodeWithTag("reset_minute").assertTextContains("30")
        composeRule.onNodeWithTag("settings_display_name").assertTextContains("Restored Person")
    }

    private fun setSettingsContent(
        providers: Set<String> = setOf("password"),
        isDeletingAccount: Boolean = false,
        message: String? = null,
        errorMessage: String? = null,
        onBack: () -> Unit = {},
        onResetTime: (Int) -> Unit = {},
        onTheme: (ThemePreference) -> Unit = {},
        onDisplayName: (String) -> Unit = {},
        onSignOut: () -> Unit = {},
        onDeletePassword: (String?) -> Unit = {},
        onDeleteGoogle: () -> Unit = {},
    ) {
        composeRule.setContent {
            UiTestTheme {
                SettingsScreen(
                    state =
                        settingsState(
                            providers = providers,
                            isDeletingAccount = isDeletingAccount,
                            message = message,
                            errorMessage = errorMessage,
                        ),
                    onBack = onBack,
                    onResetTime = onResetTime,
                    onTheme = onTheme,
                    onDisplayName = onDisplayName,
                    onSignOut = onSignOut,
                    onDeletePasswordAccount = onDeletePassword,
                    onDeleteGoogleAccount = onDeleteGoogle,
                )
            }
        }
    }

    private fun settingsState(
        providers: Set<String> = setOf("password"),
        isDeletingAccount: Boolean = false,
        message: String? = null,
        errorMessage: String? = null,
    ): SettingsUiState =
        SettingsUiState(
            settings =
                UserSettings(
                    resetMinutesAfterMidnight = 90,
                    timezoneId = "Asia/Singapore",
                    displayName = "Initial Person",
                    themePreference = ThemePreference.SYSTEM,
                ),
            accountEmail = "test@example.com",
            providers = providers,
            timezoneId = "Asia/Singapore",
            isBusy = isDeletingAccount,
            isDeletingAccount = isDeletingAccount,
            message = message,
            errorMessage = errorMessage,
        )
}
