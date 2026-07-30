package io.github.ffelixq.medswidget.widget

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ffelixq.medswidget.ui.UiTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetMedicineSelectionRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun medicineRowExposesOneSelectableRadioAction() {
        var selectionCount = 0
        composeRule.setContent {
            UiTestTheme {
                WidgetMedicineSelectionRow(
                    medicine = medicine(),
                    selected = false,
                    onSelected = { selectionCount += 1 },
                )
            }
        }

        composeRule
            .onNodeWithTag("widget_medicine_medicine-a")
            .assertIsNotSelected()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performClick()
        composeRule
            .onAllNodes(
                hasClickAction() and
                    hasAnyAncestor(hasTestTag("widget_medicine_medicine-a")),
                useUnmergedTree = true,
            ).assertCountEquals(0)

        assertEquals(1, selectionCount)
    }

    @Test
    fun selectedMedicineRowReportsSelectedState() {
        composeRule.setContent {
            UiTestTheme {
                WidgetMedicineSelectionRow(
                    medicine = medicine(),
                    selected = true,
                    onSelected = {},
                )
            }
        }

        composeRule
            .onNodeWithTag("widget_medicine_medicine-a")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    private fun medicine(): WidgetMedicine =
        WidgetMedicine(
            id = "medicine-a",
            name = "Medicine A",
            afternoonEnabled = true,
            afternoonLabel = "Afternoon",
            nightEnabled = true,
            nightLabel = "Night",
        )
}
