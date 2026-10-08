package com.egrmeister.watertiles.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val sections = listOf(
    "What is stored" to "Your water entries (amount, date, time and time zone), your goal history and your display " +
        "and reminder preferences. Nothing else — no age, weight, health data, location or account.",
    "Where it is stored" to "Only in WaterTiles' private storage on this device. The app has no internet permission, " +
        "no servers, no analytics, no advertising and no cloud sync.",
    "Backups" to "WaterTiles opts out of Android cloud backup and device-to-device transfer, so your data is not " +
        "copied off this device. Uninstalling the app or using “Clear all local data” deletes it permanently.",
    "Notifications" to "Reminders are optional, off by default and generated on the device. Notification permission " +
        "is requested only when you turn reminders on. Delivery times are approximate.",
    "Health" to "WaterTiles records the amounts you enter against a goal you choose. It does not calculate " +
        "hydration needs or provide medical advice.",
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("‹ Back to Settings") }
            Text("Privacy", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            sections.forEach { (title, body) ->
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
