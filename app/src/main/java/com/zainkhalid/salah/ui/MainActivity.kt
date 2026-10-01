package com.zainkhalid.salah.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zainkhalid.salah.reminders.Notifications
import salah.core.ThemeSetting

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: SalahViewModel = viewModel()
            val config by vm.config.collectAsStateWithLifecycle()
            val accent by vm.accent.collectAsStateWithLifecycle()
            val dark = when (config.display.theme) {
                ThemeSetting.SYSTEM -> isSystemInDarkTheme()
                ThemeSetting.LIGHT -> false
                ThemeSetting.DARK -> true
            }
            SalahTheme(accent, dark) { SalahRoot(vm) }
        }
    }
}

@Composable
private fun SalahRoot(vm: SalahViewModel) {
    val context = LocalContext.current
    val c = palette
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = c.background,
        bottomBar = {
            NavigationBar(containerColor = c.display) {
                for (tab in Tab.entries) {
                    NavigationBarItem(
                        selected = vm.tab == tab,
                        onClick = {
                            vm.tab = tab
                            if (tab != Tab.TODAY) vm.detailPrayer = null
                        },
                        icon = {
                            Icon(
                                when (tab) {
                                    Tab.TODAY -> Icons.Filled.Home
                                    Tab.SCHEDULE -> Icons.Filled.DateRange
                                    Tab.REMINDERS -> Icons.Filled.Notifications
                                    Tab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.onAccent,
                            indicatorColor = c.accent,
                            selectedTextColor = c.text,
                            unselectedIconColor = c.secondary,
                            unselectedTextColor = c.secondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (vm.tab) {
            Tab.TODAY -> TodayScreen(vm, modifier)
            Tab.SCHEDULE -> ScheduleScreen(vm, modifier)
            Tab.REMINDERS -> RemindersScreen(vm, modifier)
            Tab.SETTINGS -> SettingsScreen(vm, modifier)
        }
    }
    if (vm.showLocationSheet) LocationSheet(vm)
}
