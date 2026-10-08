package com.egrmeister.watertiles.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.watertiles.AppContainer
import com.egrmeister.watertiles.core.Format
import com.egrmeister.watertiles.data.local.AppTheme
import com.egrmeister.watertiles.domain.entries.AmountParser
import com.egrmeister.watertiles.domain.entries.AmountResult
import com.egrmeister.watertiles.domain.mosaic.MosaicCalculator
import com.egrmeister.watertiles.ui.components.ThemeSelector
import com.egrmeister.watertiles.ui.components.TileSizeSelector
import kotlinx.coroutines.launch

class SetupViewModel(private val c: AppContainer) : ViewModel() {
    /** Preview the chosen theme live while setting up. */
    fun previewTheme(theme: AppTheme) = viewModelScope.launch { c.settings.setTheme(theme) }

    fun finish(goalMl: Int, tileSizeMl: Int, theme: AppTheme, onDone: () -> Unit) = viewModelScope.launch {
        c.repository.setGoal(goalMl, c.time.today())
        c.settings.completeSetup(tileSizeMl, theme)
        onDone()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: SetupViewModel, currentTheme: AppTheme, onDone: () -> Unit) {
    var goalText by rememberSaveable { mutableStateOf("") }
    var goalError by rememberSaveable { mutableStateOf<String?>(null) }
    var tileSize by rememberSaveable { mutableIntStateOf(100) }
    var busy by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("WaterTiles", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
            Text(
                "Choose a daily goal, a tile size and a look. You can change all of these later in Settings.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Text("Daily goal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            OutlinedTextField(
                value = goalText,
                onValueChange = { v ->
                    goalText = v.filter { it.isDigit() }.take(5)
                    goalError = null
                },
                label = { Text("Goal (mL)") },
                singleLine = true,
                isError = goalError != null,
                supportingText = { Text(goalError ?: "Between 100 and 10,000 mL.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Examples (editable, not personalized recommendations):", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1_500, 2_000, 2_500).forEach { ex ->
                    AssistChip(
                        onClick = {
                            goalText = ex.toString()
                            goalError = null
                        },
                        label = { Text(Format.ml(ex)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Text(
                "WaterTiles doesn't calculate hydration needs or give health advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Tile size", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            TileSizeSelector(tileSize, { tileSize = it })

            Text("Theme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            ThemeSelector(currentTheme, { vm.previewTheme(it) })

            Text(
                "Reminders are off. You can enable optional local reminders in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    when (val r = AmountParser.parse(goalText, MosaicCalculator.MIN_GOAL_ML, MosaicCalculator.MAX_GOAL_ML)) {
                        is AmountResult.Invalid -> goalError = r.message
                        is AmountResult.Valid -> {
                            busy = true
                            vm.finish(r.ml, tileSize, currentTheme, onDone)
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Start tracking") }
        }
    }
}
