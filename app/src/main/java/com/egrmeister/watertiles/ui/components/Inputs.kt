package com.egrmeister.watertiles.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.egrmeister.watertiles.data.local.AppTheme
import com.egrmeister.watertiles.domain.mosaic.Tile
import com.egrmeister.watertiles.ui.theme.LocalTileColors
import com.egrmeister.watertiles.ui.theme.colorSchemeFor
import com.egrmeister.watertiles.ui.theme.tileColorsFor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileSizeSelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(100, 200)
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, ml ->
            SegmentedButton(
                selected = selected == ml,
                onClick = { onSelect(ml) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("$ml mL tiles") }
        }
    }
}

@Composable
fun ThemeSelector(selected: AppTheme, onSelect: (AppTheme) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppTheme.entries.forEach { theme ->
            val scheme = colorSchemeFor(theme)
            val tiles = tileColorsFor(theme)
            val isSel = theme == selected
            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = isSel, role = Role.RadioButton, onClick = { onSelect(theme) }),
                border = androidx.compose.foundation.BorderStroke(
                    if (isSel) 3.dp else 1.dp,
                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Column(
                    Modifier.fillMaxWidth().background(scheme.background).padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CompositionLocalProvider(LocalTileColors provides tiles) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            SwatchTile(1f)
                            SwatchTile(0.5f)
                        }
                    }
                    Text(
                        theme.label + if (isSel) " ✓" else "",
                        color = scheme.onBackground,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwatchTile(fraction: Float) {
    DropletTile(
        tile = Tile(number = 1, capacityMl = 100, filledMl = (fraction * 100).toInt(), isReducedCapacity = false),
        modifier = Modifier.size(28.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
