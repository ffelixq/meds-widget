package io.github.ffelixq.medswidget.widget

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetUpdateCoordinatorTest {
    @Test
    fun `update all requests every widget type`() =
        runTest {
            val requestedUpdates = mutableListOf<String>()
            val coordinator =
                WidgetUpdateCoordinator(
                    updateSingleMedicineWidgets = { requestedUpdates += "single" },
                    updateAllMedicinesWidgets = { requestedUpdates += "all" },
                    updateDashboardWidgets = { requestedUpdates += "dashboard" },
                )

            coordinator.updateAll()

            assertEquals(listOf("single", "all", "dashboard"), requestedUpdates)
        }
}
