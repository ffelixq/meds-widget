package io.github.ffelixq.medswidget.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ffelixq.medswidget.domain.CompletionProgress
import io.github.ffelixq.medswidget.domain.DoseRow
import io.github.ffelixq.medswidget.domain.HistoryEntry
import io.github.ffelixq.medswidget.domain.Medicine
import io.github.ffelixq.medswidget.domain.MedicineValidator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FakeAuthenticatedFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun registerCreateCheckUndoAndOpenHistoryThroughFakeAuthenticatedNavigation() {
        composeRule.setContent {
            UiTestTheme {
                FakeAuthenticatedHost()
            }
        }

        composeRule.onNodeWithText("Create an email account").performClick()
        composeRule.onNodeWithTag("email").performTextInput("flow@example.com")
        composeRule.onNodeWithTag("password").performTextInput("secret1")
        composeRule.onNodeWithTag("display_name").performTextInput("Flow User")
        composeRule.onNodeWithTag("auth_submit").performClick()

        composeRule.onNodeWithText("No medicines yet").assertIsDisplayed()
        composeRule.onNodeWithText("Add medicine").performClick()
        composeRule.onNodeWithTag("medicine_name").performTextInput("Flow medicine")
        composeRule.onNodeWithTag("night_toggle").performClick()
        composeRule.onNodeWithTag("save_medicine").performClick()

        val stateId = "2026-07-29_flow-medicine_afternoon"
        assertEquals(
            4,
            composeRule.onAllNodesWithText("Flow medicine").fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithTag("app_dose_$stateId").assertIsDisplayed()
        composeRule.onNodeWithTag("app_dose_$stateId").performClick()
        composeRule
            .onNodeWithTag("app_dose_$stateId")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Undo this check?").assertIsDisplayed()
        composeRule.onNodeWithText("Undo check").performClick()

        composeRule.onNodeWithText("History").performClick()

        composeRule.onNodeWithText("Flow medicine").assertIsDisplayed()
        composeRule.onNodeWithText("Undone", substring = true).assertIsDisplayed()
    }

    @Composable
    private fun FakeAuthenticatedHost() {
        var route by remember { mutableStateOf(FakeRoute.AUTH) }
        var medicine by remember { mutableStateOf<Medicine?>(null) }
        var row by remember { mutableStateOf<DoseRow?>(null) }
        var history by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
        val day = LocalDate.of(2026, 7, 29)
        val checkedAt = Instant.parse("2026-07-29T05:00:00Z")

        when (route) {
            FakeRoute.AUTH -> {
                AuthScreen(
                    state = AuthUiState(isLoading = false),
                    onSignIn = { _, _ -> route = FakeRoute.MAIN },
                    onSignUp = { _, _, _ -> route = FakeRoute.MAIN },
                    onReset = {},
                    onGoogle = { route = FakeRoute.MAIN },
                )
            }

            FakeRoute.MAIN -> {
                val currentMedicine = medicine
                val currentRows = listOfNotNull(row)
                MainScreen(
                    state =
                        MainUiState(
                            isLoading = false,
                            logicalDay = day,
                            medicines = listOfNotNull(currentMedicine),
                            rows = currentRows,
                            progress =
                                CompletionProgress(
                                    completed = currentRows.count(DoseRow::isTaken),
                                    total = currentRows.size,
                                ),
                        ),
                    onCheck = { selected, source ->
                        val nowTaken = selected.copy(isTaken = true, checkedAt = checkedAt)
                        row = nowTaken
                        history =
                            listOf(
                                HistoryEntry(
                                    eventId = "check-1",
                                    logicalDay = day,
                                    medicineName = selected.medicineName,
                                    label = selected.label,
                                    slot = selected.slot,
                                    checkedAt = checkedAt,
                                    checkedTimezone = "Asia/Singapore",
                                    checkedSource = source,
                                ),
                            )
                    },
                    onUndo = {
                        row = it.copy(isTaken = false)
                        history = history.map { entry -> entry.copy(undoneAt = checkedAt.plusSeconds(60)) }
                    },
                    onAdd = { route = FakeRoute.ADD },
                    onEdit = {},
                    onHistory = { route = FakeRoute.HISTORY },
                    onSettings = {},
                )
            }

            FakeRoute.ADD -> {
                MedicineScreen(
                    medicine = null,
                    onBack = { route = FakeRoute.MAIN },
                    onSave = { draft ->
                        val result = MedicineValidator.validate(draft)
                        if (result.isValid) {
                            val saved =
                                testMedicine(
                                    id = "flow-medicine",
                                    name = result.normalized.name,
                                    afternoonEnabled = result.normalized.afternoonEnabled,
                                    nightEnabled = result.normalized.nightEnabled,
                                )
                            medicine = saved
                            row = testDoseRow(medicine = saved, day = day)
                        }
                        result
                    },
                    onArchive = {},
                    onDelete = {},
                )
            }

            FakeRoute.HISTORY -> {
                HistoryScreen(
                    state = HistoryUiState(isLoading = false, entries = history),
                    onBack = { route = FakeRoute.MAIN },
                )
            }
        }
    }

    private enum class FakeRoute {
        AUTH,
        MAIN,
        ADD,
        HISTORY,
    }
}
