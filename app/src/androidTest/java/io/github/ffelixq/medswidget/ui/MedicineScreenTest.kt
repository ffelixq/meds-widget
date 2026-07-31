package io.github.ffelixq.medswidget.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ffelixq.medswidget.domain.MedicineDraft
import io.github.ffelixq.medswidget.domain.MedicineValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicineScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addMedicineShowsValidationFromDomainPolicy() {
        var backCalled = false
        composeRule.setContent {
            UiTestTheme {
                MedicineScreen(
                    medicine = null,
                    onBack = { backCalled = true },
                    onSave = MedicineValidator::validate,
                    onArchive = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("save_medicine").performClick()

        composeRule.onNodeWithText("Medicine name is required.").assertIsDisplayed()
        assertFalse(backCalled)
    }

    @Test
    fun atLeastOneSlotMustRemainEnabled() {
        composeRule.setContent {
            UiTestTheme {
                MedicineScreen(
                    medicine = null,
                    onBack = {},
                    onSave = MedicineValidator::validate,
                    onArchive = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("medicine_name").performTextInput("Medicine A")
        composeRule.onNodeWithTag("afternoon_toggle").performClick()
        composeRule.onNodeWithTag("night_toggle").performClick()
        composeRule.onNodeWithTag("save_medicine").performClick()

        composeRule.onNodeWithText("Enable at least one slot.").assertIsDisplayed()
    }

    @Test
    fun slotTogglesExposeAssociatedScreenReaderLabels() {
        composeRule.setContent {
            UiTestTheme {
                MedicineScreen(
                    medicine = null,
                    onBack = {},
                    onSave = MedicineValidator::validate,
                    onArchive = {},
                    onDelete = {},
                )
            }
        }

        composeRule
            .onNodeWithTag("afternoon_toggle")
            .assertContentDescriptionEquals("Afternoon slot")
            .assertIsOn()
        composeRule
            .onNodeWithTag("night_toggle")
            .assertContentDescriptionEquals("Night slot")
            .assertIsOn()
    }

    @Test
    fun validAddSubmitsConfiguredDraftAndNavigatesBack() {
        var submitted: MedicineDraft? = null
        var backCount = 0
        composeRule.setContent {
            UiTestTheme {
                MedicineScreen(
                    medicine = null,
                    onBack = { backCount += 1 },
                    onSave = { draft ->
                        submitted = draft
                        MedicineValidator.validate(draft)
                    },
                    onArchive = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("medicine_name").performTextInput("Vitamin D")
        composeRule.onNodeWithTag("afternoon_label").performTextClearance()
        composeRule.onNodeWithTag("afternoon_label").performTextInput("After lunch")
        composeRule.onNodeWithTag("night_toggle").performClick()
        composeRule.onNodeWithTag("save_medicine").performClick()

        composeRule.waitForIdle()
        assertEquals("Vitamin D", submitted?.name)
        assertTrue(submitted?.afternoonEnabled == true)
        assertEquals("After lunch", submitted?.afternoonLabel)
        assertFalse(submitted?.nightEnabled ?: true)
        assertEquals(1, backCount)
    }

    @Test
    fun perSlotCountdownUsesPresetAndDisabledSlotDoesNotSubmitIt() {
        var submitted: MedicineDraft? = null
        composeRule.setContent {
            UiTestTheme {
                MedicineScreen(
                    medicine = null,
                    onBack = {},
                    onSave = { draft ->
                        submitted = draft
                        MedicineValidator.validate(draft)
                    },
                    onArchive = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("medicine_name").performTextInput("Medicine A")
        composeRule.onNodeWithTag("afternoon_countdown_toggle").performScrollTo().performClick()
        composeRule.onNodeWithText("2h").performClick()
        composeRule.onNodeWithTag("night_toggle").performClick()
        composeRule.onNodeWithTag("save_medicine").performScrollTo().performClick()

        composeRule.waitForIdle()
        assertEquals(120, submitted?.afternoonCountdownMinutes)
        assertNull(submitted?.nightCountdownMinutes)
    }

    @Test
    fun editPrefillsMedicineAndOffersArchiveAndConfirmedDelete() {
        val medicine = testMedicine(name = "Existing medicine")
        var archivedId: String? = null
        var deletedId: String? = null
        var backCount = 0
        composeRule.setContent {
            UiTestTheme {
                MedicineScreen(
                    medicine = medicine,
                    onBack = { backCount += 1 },
                    onSave = MedicineValidator::validate,
                    onArchive = { archivedId = it },
                    onDelete = { deletedId = it },
                )
            }
        }

        composeRule.onNodeWithText("Edit medicine").assertIsDisplayed()
        composeRule.onNodeWithTag("medicine_name").assertTextContains("Existing medicine")
        composeRule.onNodeWithTag("afternoon_label").assertTextContains("After lunch")

        composeRule.onNodeWithText("Archive medicine").performClick()
        assertEquals(medicine.id, archivedId)
        assertEquals(1, backCount)

        composeRule.onNodeWithText("Delete medicine").performClick()
        composeRule.onNodeWithText("Delete medicine?").assertIsDisplayed()
        assertNull(deletedId)
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(medicine.id, deletedId)
        assertEquals(2, backCount)
    }

    @Test
    fun editMedicineSubmitsExistingIdAndUpdatedFields() {
        val medicine = testMedicine(name = "Existing medicine")
        var submitted: MedicineDraft? = null
        var backCount = 0
        composeRule.setContent {
            UiTestTheme {
                MedicineScreen(
                    medicine = medicine,
                    onBack = { backCount += 1 },
                    onSave = { draft ->
                        submitted = draft
                        MedicineValidator.validate(draft)
                    },
                    onArchive = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("medicine_name").performTextClearance()
        composeRule.onNodeWithTag("medicine_name").performTextInput("Updated medicine")
        composeRule.onNodeWithTag("night_label").performTextClearance()
        composeRule.onNodeWithTag("night_label").performTextInput("At bedtime")
        composeRule.onNodeWithTag("save_medicine").performScrollTo().performClick()

        composeRule.waitForIdle()
        assertEquals(medicine.id, submitted?.id)
        assertEquals("Updated medicine", submitted?.name)
        assertEquals("At bedtime", submitted?.nightLabel)
        assertEquals(1, backCount)
    }

    @Test
    fun draftFieldsSurviveSavedStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            UiTestTheme {
                MedicineScreen(
                    medicine = null,
                    onBack = {},
                    onSave = MedicineValidator::validate,
                    onArchive = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("medicine_name").performTextInput("Restored medicine")
        composeRule.onNodeWithTag("afternoon_label").performTextClearance()
        composeRule.onNodeWithTag("afternoon_label").performTextInput("Restored lunch")
        composeRule.onNodeWithTag("night_toggle").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("medicine_name").assertTextContains("Restored medicine")
        composeRule.onNodeWithTag("afternoon_label").assertTextContains("Restored lunch")
        composeRule.onNodeWithTag("night_toggle").assertIsOff()
    }
}
