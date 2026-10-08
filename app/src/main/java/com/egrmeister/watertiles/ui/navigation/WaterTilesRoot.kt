package com.egrmeister.watertiles.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.egrmeister.watertiles.AppContainer
import com.egrmeister.watertiles.R
import com.egrmeister.watertiles.data.local.AppSettings
import com.egrmeister.watertiles.ui.calendar.CalendarScreen
import com.egrmeister.watertiles.ui.calendar.CalendarViewModel
import com.egrmeister.watertiles.ui.entry.EntryEditorScreen
import com.egrmeister.watertiles.ui.entry.EntryEditorViewModel
import com.egrmeister.watertiles.ui.settings.PrivacyScreen
import com.egrmeister.watertiles.ui.settings.SettingsScreen
import com.egrmeister.watertiles.ui.settings.SettingsViewModel
import com.egrmeister.watertiles.ui.setup.SetupScreen
import com.egrmeister.watertiles.ui.setup.SetupViewModel
import com.egrmeister.watertiles.ui.theme.WaterTilesTheme
import com.egrmeister.watertiles.ui.today.DayScreen
import com.egrmeister.watertiles.ui.today.DayViewModel
import java.time.LocalDate

private object Routes {
    const val SETUP = "setup"
    const val TODAY = "today"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val DAY = "day/{date}"
    const val ENTRY = "entry?date={date}&id={id}"

    fun day(date: LocalDate) = "day/$date"
    fun entry(date: LocalDate, id: Long?) = "entry?date=$date&id=${id ?: -1L}"
}

private data class Tab(val route: String, val label: String, @DrawableRes val icon: Int)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", R.drawable.ic_nav_today),
    Tab(Routes.CALENDAR, "Calendar", R.drawable.ic_nav_calendar),
    Tab(Routes.SETTINGS, "Settings", R.drawable.ic_nav_settings),
)

@Composable
fun WaterTilesRoot(container: AppContainer, openTodayRequests: Int) {
    val settingsState by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val settings: AppSettings = settingsState ?: return
    WaterTilesTheme(settings.theme, settings.reducedAnimation) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            AppNavigation(container, settings, openTodayRequests)
        }
    }
}

@Composable
private fun AppNavigation(container: AppContainer, settings: AppSettings, openTodayRequests: Int) {
    val nav = rememberNavController()
    val start = remember { if (settings.setupDone) Routes.TODAY else Routes.SETUP }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showNav = route != null && route != Routes.SETUP && route != Routes.ENTRY
    val selectedTab = when {
        route == null -> null
        route == Routes.DAY || route == Routes.CALENDAR -> Routes.CALENDAR
        route == Routes.PRIVACY || route == Routes.SETTINGS -> Routes.SETTINGS
        route == Routes.TODAY -> Routes.TODAY
        else -> null
    }

    LaunchedEffect(openTodayRequests) {
        if (openTodayRequests > 0 && settings.setupDone) nav.goToTab(Routes.TODAY)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showNav && !wide) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab.route,
                                onClick = { nav.goToTab(tab.route) },
                                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (showNav && wide) {
                    NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0)) {
                        tabs.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab.route,
                                onClick = { nav.goToTab(tab.route) },
                                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
                NavHost(navController = nav, startDestination = start, modifier = Modifier.weight(1f)) {
                    composable(Routes.SETUP) {
                        val vm: SetupViewModel = viewModel(factory = viewModelFactory { initializer { SetupViewModel(container) } })
                        SetupScreen(vm, settings.theme) {
                            nav.navigate(Routes.TODAY) { popUpTo(Routes.SETUP) { inclusive = true } }
                        }
                    }
                    composable(Routes.TODAY) {
                        val vm: DayViewModel = viewModel(
                            key = "today",
                            factory = viewModelFactory { initializer { DayViewModel(container, null) } },
                        )
                        DayScreen(
                            vm = vm,
                            showBack = false,
                            onBack = {},
                            onAddEntry = { nav.navigate(Routes.entry(it, null)) },
                            onEditEntry = { date, id -> nav.navigate(Routes.entry(date, id)) },
                        )
                    }
                    composable(Routes.CALENDAR) {
                        val vm: CalendarViewModel = viewModel(factory = viewModelFactory { initializer { CalendarViewModel(container) } })
                        CalendarScreen(vm) { date ->
                            if (date == container.todayClock.today.value) nav.goToTab(Routes.TODAY) else nav.navigate(Routes.day(date))
                        }
                    }
                    composable(
                        Routes.DAY,
                        arguments = listOf(navArgument("date") { type = NavType.StringType }),
                    ) { entry ->
                        val date = LocalDate.parse(requireNotNull(entry.arguments?.getString("date")))
                        val vm: DayViewModel = viewModel(
                            key = "day-$date",
                            factory = viewModelFactory { initializer { DayViewModel(container, date) } },
                        )
                        DayScreen(
                            vm = vm,
                            showBack = true,
                            onBack = { nav.popBackStack() },
                            onAddEntry = { nav.navigate(Routes.entry(it, null)) },
                            onEditEntry = { d, id -> nav.navigate(Routes.entry(d, id)) },
                        )
                    }
                    composable(
                        Routes.ENTRY,
                        arguments = listOf(
                            navArgument("date") { type = NavType.StringType },
                            navArgument("id") {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                        ),
                    ) { entry ->
                        val date = LocalDate.parse(requireNotNull(entry.arguments?.getString("date")))
                        val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
                        val vm: EntryEditorViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer { EntryEditorViewModel(container, createSavedStateHandle(), id, date) }
                            },
                        )
                        EntryEditorScreen(vm) { nav.popBackStack() }
                    }
                    composable(Routes.SETTINGS) {
                        val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container) } })
                        SettingsScreen(
                            vm = vm,
                            onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                            onCleared = {
                                nav.navigate(Routes.SETUP) { popUpTo(nav.graph.id) { inclusive = true } }
                            },
                        )
                    }
                    composable(Routes.PRIVACY) {
                        PrivacyScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

private fun NavHostController.goToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.TODAY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
