package io.github.ffelixq.medswidget.widget

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomaticWidgetConfigurationTest {
    private val packageName = "io.github.ffelixq.medswidget"

    @Test
    fun `automatic widget providers route to their repair kind`() {
        assertEquals(
            AutomaticWidgetKind.ALL_MEDICINES,
            automaticWidgetKind(
                packageName,
                ComponentName(packageName, AllMedicinesWidgetReceiver::class.java.name),
            ),
        )
        assertEquals(
            AutomaticWidgetKind.DASHBOARD,
            automaticWidgetKind(
                packageName,
                ComponentName(packageName, DashboardWidgetReceiver::class.java.name),
            ),
        )
    }

    @Test
    fun `single medicine provider cannot enter automatic repair flow`() {
        assertNull(
            automaticWidgetKind(
                packageName,
                ComponentName(packageName, SingleMedicineWidgetReceiver::class.java.name),
            ),
        )
    }
}
