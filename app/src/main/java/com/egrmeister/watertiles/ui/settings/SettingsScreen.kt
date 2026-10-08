package com.egrmeister.watertiles.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.watertiles.core.Format
import com.egrmeister.watertiles.domain.entries.AmountParser
import com.egrmeister.watertiles.domain.entries.AmountResult
import com.egrmeister.watertiles.domain.goals.GoalApply
import com.egrmeister.watertiles.domain.mosaic.MosaicCalculator
import com.egrmeister.watertiles.domain.reminders.ReminderPolicy
import com.egrmeister.watertiles.domain.reminders.ReminderSettings
import com.egrmeister.watertiles.reminders.ReminderScheduler
import com.egrmeister.watertiles.ui.components.ThemeSelector
import com.egrmeister.watertiles.ui.components.TileSizeSelector
import com.egrmeister.watertiles.ui.components.TimePickerDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onOpenPrivacy: () -> Unit, onCleared: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    var goalDialog by rememberSaveable { mutableStateOf(false) }
    var portionsDialog by rememberSaveable { mutableStateOf(false) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    var startPicker by rememberSaveable { mutableStateOf(false) }
    var endPicker by rememberSaveable { mutableStateOf(false) }
    var windowError by rememberSaveable { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTick++
    }

    if (s.loading) return
    val r = s.settings.reminders
    val canPost = remember(resumeTick, r.enabled) { ReminderScheduler.canPost(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })

            Section("Daily goal")
            Text(
                s.todayGoal?.let { "Today's goal: ${Format.ml(it)}" } ?: "No goal saved for today.",
                style = MaterialTheme.typography.bodyLarge,
            )
            s.pendingGoal?.let {
                Text(
                    "${Format.ml(it.goalMl)} applies from ${Format.shortDate(it.effectiveDate)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { goalDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Change goal") }

            Section("Tile size")
            TileSizeSelector(s.settings.tileSizeMl, vm::setTileSize)
            Hint("Changes how tiles are drawn, including past days. Recorded amounts and goals stay the same.")

            Section("Quick portions")
            Text(s.settings.quickPortions.joinToString(" · ") { Format.ml(it) }, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { portionsDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit quick portions") }

            Section("Theme")
            ThemeSelector(s.settings.theme, vm::setTheme)

            Section("Reminders")
            SwitchRow(
                label = "Reminders",
                checked = r.enabled,
                onChange = { on ->
                    vm.setReminders(r.copy(enabled = on))
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !ReminderScheduler.hasPostPermission(context)
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            if (r.enabled) {
                if (!canPost) {
                    Text(
                        "Notifications are off for WaterTiles, so reminders can't appear. Tracking works normally.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }) { Text("Open notification settings") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { startPicker = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("Start ${Format.time(r.start)}")
                    }
                    OutlinedButton(onClick = { endPicker = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("End ${Format.time(r.end)}")
                    }
                }
                windowError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Interval", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderSettings.INTERVALS.forEach { minutes ->
                        FilterChip(
                            selected = r.intervalMinutes == minutes,
                            onClick = { vm.setReminders(r.copy(intervalMinutes = minutes)) },
                            label = { Text("$minutes min") },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Hint(
                    "Reminders are local and approximate: Android may delay them to save battery. " +
                        "They are skipped once your goal for the day is reached or when you recorded water recently. " +
                        "If the app is force-stopped, reminders pause until you open WaterTiles again.",
                )
            }

            Section("Accessibility")
            SwitchRow("Reduce animation", s.settings.reducedAnimation, vm::setReducedAnimation)

            Section("Privacy")
            Hint("Everything stays on this device. WaterTiles has no account, no internet access and no analytics.")
            OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Privacy information") }

            Section("Data")
            OutlinedButton(onClick = { clearDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Clear all local data", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (goalDialog) {
        GoalDialog(
            initial = s.todayGoal,
            onDismiss = { goalDialog = false },
            onApply = { goal, apply ->
                vm.setGoal(goal, apply)
                goalDialog = false
            },
        )
    }
    if (portionsDialog) {
        PortionsDialog(
            initial = s.settings.quickPortions,
            onDismiss = { portionsDialog = false },
            onSave = {
                vm.setQuickPortions(it)
                portionsDialog = false
            },
        )
    }
    if (startPicker) {
        TimePickerDialog("Start time", r.start, onDismiss = { startPicker = false }) { t ->
            startPicker = false
            val next = r.copy(start = t)
            if (ReminderPolicy.isWindowValid(next)) {
                windowError = null
                vm.setReminders(next)
            } else {
                windowError = "End time must be later than start time on the same day."
            }
        }
    }
    if (endPicker) {
        TimePickerDialog("End time", r.end, onDismiss = { endPicker = false }) { t ->
            endPicker = false
            val next = r.copy(end = t)
            if (ReminderPolicy.isWindowValid(next)) {
                windowError = null
                vm.setReminders(next)
            } else {
                windowError = "End time must be later than start time on the same day."
            }
        }
    }
    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Clear all local data?") },
            text = { Text("This permanently deletes every entry, goal and setting on this device and cancels reminders.") },
            confirmButton = {
                TextButton(onClick = {
                    clearDialog = false
                    vm.clearAll(onCleared)
                }) { Text("Clear data", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun GoalDialog(initial: Int?, onDismiss: () -> Unit, onApply: (Int, GoalApply) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial?.toString() ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun submit(apply: GoalApply) {
        when (val r = AmountParser.parse(text, MosaicCalculator.MIN_GOAL_ML, MosaicCalculator.MAX_GOAL_ML)) {
            is AmountResult.Valid -> onApply(r.ml, apply)
            is AmountResult.Invalid -> error = r.message
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily goal") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(5); error = null },
                    label = { Text("Goal (mL)") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "100–10,000 mL. Earlier days keep their saved goal.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { submit(GoalApply.TOMORROW) }) { Text("Apply tomorrow") }
                TextButton(onClick = { submit(GoalApply.TODAY) }) { Text("Apply today") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PortionsDialog(initial: List<Int>, onDismiss: () -> Unit, onSave: (List<Int>) -> Unit) {
    val texts = remember { initial.map { mutableStateOf(it.toString()) } }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick portions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                texts.forEachIndexed { i, st ->
                    OutlinedTextField(
                        value = st.value,
                        onValueChange = { v -> st.value = v.filter { it.isDigit() }.take(4); error = null },
                        label = { Text("Portion ${i + 1} (mL)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Text(error ?: "Each portion: 1–5,000 mL.", color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = texts.map { AmountParser.parse(it.value) }
                val bad = parsed.filterIsInstance<AmountResult.Invalid>().firstOrNull()
                if (bad != null) {
                    error = bad.message
                } else {
                    onSave(parsed.map { (it as AmountResult.Valid).ml })
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
