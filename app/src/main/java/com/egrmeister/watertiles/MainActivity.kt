package com.egrmeister.watertiles

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.egrmeister.watertiles.reminders.ReminderScheduler
import com.egrmeister.watertiles.ui.navigation.WaterTilesRoot

class MainActivity : ComponentActivity() {
    /** Incremented whenever a reminder tap asks to show Today. */
    private val openTodayRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as WaterTilesApp).container
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            WaterTilesRoot(container = container, openTodayRequests = openTodayRequests.intValue)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ReminderScheduler.EXTRA_OPEN_TODAY, false) == true) {
            openTodayRequests.intValue += 1
            intent.removeExtra(ReminderScheduler.EXTRA_OPEN_TODAY)
        }
    }
}
