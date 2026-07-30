package io.github.ffelixq.medswidget.widget

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetUpdateCoordinatorTest {
    @Test
    fun `update all requests both widget types`() =
        runTest {
            val requestedUpdates = mutableListOf<String>()
            val coordinator =
                WidgetUpdateCoordinator(
                    updateSingleMedicineWidgets = { requestedUpdates += "single" },
                    updateAllMedicinesWidgets = { requestedUpdates += "all" },
                )

            coordinator.updateAll()

            assertEquals(listOf("single", "all"), requestedUpdates)
        }
}
