package io.github.ffelixq.medswidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.ui.MainActivity
import io.github.ffelixq.medswidget.ui.theme.MedsWidgetTheme
import kotlinx.coroutines.launch

class SingleWidgetConfigurationActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    @Suppress("LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId =
            intent
                ?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val expectedProvider = ComponentName(this, SingleMedicineWidgetReceiver::class.java)
        if (AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider != expectedProvider) {
            finish()
            return
        }

        val graph = MedsApplication.graph(this)
        setContent {
            MedsWidgetTheme {
                val snapshot by graph.snapshotStore.flow.collectAsStateWithLifecycle(
                    initialValue = WidgetSnapshot(isLoading = true),
                )
                var selectedId by remember { mutableStateOf<String?>(null) }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Choose one medicine", style = MaterialTheme.typography.headlineSmall)
                    when {
                        snapshot.isLoading -> {
                            Text("Loading medicines…")
                        }

                        !snapshot.signedIn -> {
                            Text("Sign in and add a medicine before configuring this widget.")
                            Button(
                                onClick = {
                                    startActivity(
                                        Intent(
                                            this@SingleWidgetConfigurationActivity,
                                            MainActivity::class.java,
                                        ),
                                    )
                                },
                            ) {
                                Text("Open Meds Widget")
                            }
                        }

                        snapshot.medicines.isEmpty() -> {
                            Text("There are no active medicines to select.")
                            Button(
                                onClick = {
                                    startActivity(
                                        Intent(
                                            this@SingleWidgetConfigurationActivity,
                                            MainActivity::class.java,
                                        ),
                                    )
                                },
                            ) {
                                Text("Add a medicine")
                            }
                        }

                        else -> {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(snapshot.medicines, key = WidgetMedicine::id) { medicine ->
                                    WidgetMedicineSelectionRow(
                                        medicine = medicine,
                                        selected = selectedId == medicine.id,
                                        onSelected = { selectedId = medicine.id },
                                    )
                                }
                            }
                            Button(
                                enabled = selectedId != null,
                                onClick = {
                                    val medicineId = selectedId ?: return@Button
                                    val uid = snapshot.ownerUid ?: return@Button
                                    lifecycleScope.launch {
                                        graph.configurationStore.set(
                                            SingleWidgetConfiguration(appWidgetId, uid, medicineId),
                                        )
                                        graph.widgetUpdater.updateAll()
                                        setResult(
                                            RESULT_OK,
                                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                                        )
                                        finish()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Save widget")
                            }
                        }
                    }
                    TextButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
internal fun WidgetMedicineSelectionRow(
    medicine: WidgetMedicine,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onSelected,
                ).testTag("widget_medicine_${medicine.id}")
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(medicine.name, modifier = Modifier.padding(start = 8.dp))
    }
}
