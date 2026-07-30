package io.github.ffelixq.medswidget.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

data class SingleWidgetConfiguration(
    val appWidgetId: Int,
    val ownerUid: String,
    val medicineId: String,
)

private val Context.widgetConfigurationDataStore by preferencesDataStore("widget_configuration")

class WidgetConfigurationStore(
    private val context: Context,
) {
    suspend fun get(appWidgetId: Int): SingleWidgetConfiguration? {
        val values = context.widgetConfigurationDataStore.data.first()
        val owner = values[ownerKey(appWidgetId)] ?: return null
        val medicine = values[medicineKey(appWidgetId)] ?: return null
        return SingleWidgetConfiguration(appWidgetId, owner, medicine)
    }

    suspend fun set(configuration: SingleWidgetConfiguration) {
        context.widgetConfigurationDataStore.edit { values ->
            values[ownerKey(configuration.appWidgetId)] = configuration.ownerUid
            values[medicineKey(configuration.appWidgetId)] = configuration.medicineId
        }
    }

    suspend fun remove(appWidgetId: Int) {
        context.widgetConfigurationDataStore.edit { values ->
            values.remove(ownerKey(appWidgetId))
            values.remove(medicineKey(appWidgetId))
        }
    }

    suspend fun clearAll() {
        context.widgetConfigurationDataStore.edit { it.clear() }
    }

    private fun ownerKey(id: Int) = stringPreferencesKey("widget_${id}_owner")

    private fun medicineKey(id: Int) = stringPreferencesKey("widget_${id}_medicine")
}
