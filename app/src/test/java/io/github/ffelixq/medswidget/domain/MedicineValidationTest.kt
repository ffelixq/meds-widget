package io.github.ffelixq.medswidget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineValidationTest {
    @Test
    fun `countdowns accept presets and bounded custom durations`() {
        listOf(30, 60, 90, 120, 1, 1_440).forEach { duration ->
            val result =
                MedicineValidator.validate(
                    MedicineDraft(
                        name = "Medicine",
                        afternoonEnabled = true,
                        afternoonCountdownMinutes = duration,
                        nightEnabled = false,
                    ),
                )
            assertTrue(result.isValid)
            assertEquals(duration, result.normalized.afternoonCountdownMinutes)
        }
    }

    @Test
    fun `invalid countdowns are rejected and disabled slots clear configuration`() {
        listOf(0, 1_441).forEach { duration ->
            val result =
                MedicineValidator.validate(
                    MedicineDraft(
                        name = "Medicine",
                        afternoonCountdownMinutes = duration,
                    ),
                )
            assertFalse(result.isValid)
            assertTrue("afternoonCountdownMinutes" in result.errors)
        }
        val disabled =
            MedicineValidator.validate(
                MedicineDraft(
                    name = "Medicine",
                    afternoonEnabled = false,
                    afternoonCountdownMinutes = 120,
                    nightEnabled = true,
                ),
            )
        assertTrue(disabled.isValid)
        assertEquals(null, disabled.normalized.afternoonCountdownMinutes)
    }

    @Test
    fun `valid medicine is trimmed and keeps enabled custom labels`() {
        val result =
            MedicineValidator.validate(
                MedicineDraft(
                    name = "  Vitamin D  ",
                    afternoonEnabled = true,
                    afternoonLabel = "  After lunch ",
                    nightEnabled = true,
                    nightLabel = " Before bed  ",
                ),
            )

        assertTrue(result.isValid)
        assertEquals("Vitamin D", result.normalized.name)
        assertEquals("After lunch", result.normalized.afternoonLabel)
        assertEquals("Before bed", result.normalized.nightLabel)
    }

    @Test
    fun `empty and whitespace-only medicine names are rejected`() {
        val empty = MedicineValidator.validate(MedicineDraft(name = ""))
        val whitespace = MedicineValidator.validate(MedicineDraft(name = " \n\t "))

        assertEquals("Medicine name is required.", empty.errors["name"])
        assertEquals("Medicine name is required.", whitespace.errors["name"])
    }

    @Test
    fun `medicine name accepts the maximum and rejects one character more`() {
        val maximum =
            MedicineValidator.validate(
                MedicineDraft(name = "m".repeat(MEDICINE_NAME_MAX_LENGTH)),
            )
        val excessive =
            MedicineValidator.validate(
                MedicineDraft(name = "m".repeat(MEDICINE_NAME_MAX_LENGTH + 1)),
            )

        assertTrue(maximum.isValid)
        assertFalse(excessive.isValid)
        assertEquals(
            "Medicine name must be $MEDICINE_NAME_MAX_LENGTH characters or fewer.",
            excessive.errors["name"],
        )
    }

    @Test
    fun `at least one dose slot is required`() {
        val result =
            MedicineValidator.validate(
                MedicineDraft(
                    name = "Medicine",
                    afternoonEnabled = false,
                    nightEnabled = false,
                ),
            )

        assertFalse(result.isValid)
        assertEquals("Enable at least one slot.", result.errors["slots"])
    }

    @Test
    fun `enabled custom label is required`() {
        val afternoon =
            MedicineValidator.validate(
                MedicineDraft(
                    name = "Medicine",
                    afternoonEnabled = true,
                    afternoonLabel = " ",
                    nightEnabled = false,
                ),
            )
        val night =
            MedicineValidator.validate(
                MedicineDraft(
                    name = "Medicine",
                    afternoonEnabled = false,
                    nightEnabled = true,
                    nightLabel = "\t",
                ),
            )

        assertEquals("Enabled slots need a label.", afternoon.errors["afternoonLabel"])
        assertEquals("Enabled slots need a label.", night.errors["nightLabel"])
    }

    @Test
    fun `slot label accepts the maximum and rejects one character more`() {
        val maximum =
            MedicineValidator.validate(
                MedicineDraft(
                    name = "Medicine",
                    afternoonEnabled = true,
                    afternoonLabel = "a".repeat(SLOT_LABEL_MAX_LENGTH),
                    nightEnabled = false,
                ),
            )
        val excessive =
            MedicineValidator.validate(
                MedicineDraft(
                    name = "Medicine",
                    afternoonEnabled = true,
                    afternoonLabel = "a".repeat(SLOT_LABEL_MAX_LENGTH + 1),
                    nightEnabled = false,
                ),
            )

        assertTrue(maximum.isValid)
        assertEquals(
            "Slot labels must be $SLOT_LABEL_MAX_LENGTH characters or fewer.",
            excessive.errors["afternoonLabel"],
        )
    }

    @Test
    fun `disabled blank labels are normalized to safe defaults`() {
        val nightDisabled =
            MedicineValidator.validate(
                MedicineDraft(
                    name = "Medicine",
                    afternoonEnabled = true,
                    afternoonLabel = "Afternoon",
                    nightEnabled = false,
                    nightLabel = "",
                ),
            )
        val afternoonDisabled =
            MedicineValidator.validate(
                MedicineDraft(
                    name = "Medicine",
                    afternoonEnabled = false,
                    afternoonLabel = " ",
                    nightEnabled = true,
                    nightLabel = "Night",
                ),
            )

        assertTrue(nightDisabled.isValid)
        assertEquals("Night", nightDisabled.normalized.nightLabel)
        assertNull(nightDisabled.errors["nightLabel"])
        assertTrue(afternoonDisabled.isValid)
        assertEquals("Afternoon", afternoonDisabled.normalized.afternoonLabel)
        assertNull(afternoonDisabled.errors["afternoonLabel"])
    }
}
