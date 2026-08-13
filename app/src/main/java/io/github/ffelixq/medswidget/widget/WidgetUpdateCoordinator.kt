package io.github.ffelixq.medswidget.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

interface WidgetUpdater {
    suspend fun updateAll()
}

class WidgetUpdateCoordinator internal constructor(
    private val updateSingleMedicineWidgets: suspend () -> Unit,
    private val updateAllMedicinesWidgets: suspend () -> Unit,
    private val updateDashboardWidgets: suspend () -> Unit,
) : WidgetUpdater {
    constructor(context: Context) : this(
        updateSingleMedicineWidgets = { SingleMedicineWidget().updateAll(context) },
        updateAllMedicinesWidgets = { AllMedicinesWidget().updateAll(context) },
        updateDashboardWidgets = { DashboardWidget().updateAll(context) },
    )

    override suspend fun updateAll() {
        updateSingleMedicineWidgets()
        updateAllMedicinesWidgets()
        updateDashboardWidgets()
    }
}
