package io.github.ffelixq.medswidget.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signedOutScreenRendersAuthenticationOptionsAndMedicalDisclaimer() {
        composeRule.setContent {
            UiTestTheme {
                AuthScreen(
                    state = AuthUiState(isLoading = false),
                    onSignIn = { _, _ -> },
                    onSignUp = { _, _, _ -> },
                    onReset = {},
                    onGoogle = {},
                )
            }
        }

        composeRule.onNodeWithTag("auth_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Meds Widget").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Meds Widget is a tracking utility. It does not provide medical or dosing advice.",
            ).assertIsDisplayed()
    }

    @Test
    fun missingFirebaseConfigurationDisablesCredentialActions() {
        composeRule.setContent {
            UiTestTheme {
                AuthScreen(
                    state = AuthUiState(isLoading = false, firebaseConfigured = false),
                    onSignIn = { _, _ -> },
                    onSignUp = { _, _, _ -> },
                    onReset = {},
                    onGoogle = {},
                )
            }
        }

        composeRule.onNodeWithTag("auth_submit").assertIsNotEnabled()
        composeRule.onNodeWithText("Continue with Google").assertIsNotEnabled()
        composeRule
            .onNodeWithText("This build does not contain Firebase configuration.")
            .assertIsDisplayed()
    }

    @Test
    fun registrationValidatesEachRequiredFieldThenSubmitsTrimmedValues() {
        var submitted: Triple<String, String, String>? = null
        composeRule.setContent {
            UiTestTheme {
                AuthScreen(
                    state = AuthUiState(isLoading = false),
                    onSignIn = { _, _ -> },
                    onSignUp = { email, password, name ->
                        submitted = Triple(email, password, name)
                    },
                    onReset = {},
                    onGoogle = {},
                )
            }
        }

        composeRule.onNodeWithText("Create an email account").performClick()
        composeRule.onNodeWithTag("auth_submit").performClick()
        composeRule
            .onNodeWithText("Enter a valid email address.")
            .performScrollTo()
            .assertIsDisplayed()
        assertNull(submitted)

        composeRule.onNodeWithTag("email").performTextInput("person@example.com ")
        composeRule.onNodeWithTag("auth_submit").performClick()
        composeRule
            .onNodeWithText("Password must contain at least 6 characters.")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("password").performTextInput("secret1")
        composeRule.onNodeWithTag("auth_submit").performClick()
        composeRule.onNodeWithText("Enter a display name.").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("display_name").performTextInput("  Test Person  ")
        composeRule.onNodeWithTag("auth_submit").performClick()

        assertEquals(
            Triple("person@example.com", "secret1", "Test Person"),
            submitted,
        )
    }

    @Test
    fun passwordResetModeCallsResetWithoutRequestingPassword() {
        var resetEmail: String? = null
        composeRule.setContent {
            UiTestTheme {
                AuthScreen(
                    state = AuthUiState(isLoading = false),
                    onSignIn = { _, _ -> },
                    onSignUp = { _, _, _ -> },
                    onReset = { resetEmail = it },
                    onGoogle = {},
                )
            }
        }

        composeRule.onNodeWithText("Forgot password?").performClick()
        composeRule.onNodeWithTag("email").performTextInput("reset@example.com")
        composeRule.onNodeWithTag("auth_submit").performClick()

        assertEquals("reset@example.com", resetEmail)
        composeRule.onNodeWithText("Send reset email").assertIsDisplayed()
    }

    @Test
    fun registrationFormSurvivesSavedStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            UiTestTheme {
                AuthScreen(
                    state = AuthUiState(isLoading = false),
                    onSignIn = { _, _ -> },
                    onSignUp = { _, _, _ -> },
                    onReset = {},
                    onGoogle = {},
                )
            }
        }
        composeRule.onNodeWithText("Create an email account").performClick()
        composeRule.onNodeWithTag("email").performTextInput("restore@example.com")
        composeRule.onNodeWithTag("password").performTextInput("secret1")
        composeRule.onNodeWithTag("display_name").performTextInput("Restored Person")

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Create account").assertIsDisplayed()
        composeRule.onNodeWithTag("email").assertTextContains("restore@example.com")
        composeRule.onNodeWithTag("password").assertTextContains("secret1")
        composeRule.onNodeWithTag("display_name").assertTextContains("Restored Person")
    }

    @Test
    fun requiredDisplayNameScreenValidatesAndNormalizesInput() {
        var savedName: String? = null
        composeRule.setContent {
            UiTestTheme {
                DisplayNameSetupScreen(
                    isLoading = false,
                    errorMessage = null,
                    onSave = { savedName = it },
                )
            }
        }

        composeRule.onNodeWithTag("save_required_display_name").performClick()
        composeRule.onNodeWithText("Enter a display name.").assertIsDisplayed()
        composeRule.onNodeWithTag("required_display_name").performTextInput("  New User  ")
        composeRule.onNodeWithTag("save_required_display_name").performClick()

        assertEquals("New User", savedName)
    }
}
