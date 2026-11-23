package com.zainkhalid.salah.ui

import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zainkhalid.salah.reminders.Notifications
import salah.core.AppLanguage
import salah.core.ThemeSetting

class MainActivity : ComponentActivity() {
    private var vm: SalahViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: SalahViewModel = viewModel()
            if (this.vm == null) {
                this.vm = vm
                if (savedInstanceState == null) handleOpen(intent)
            }
            val config by vm.config.collectAsStateWithLifecycle()
            val accent by vm.accent.collectAsStateWithLifecycle()
            val dark = when (config.display.theme) {
                ThemeSetting.SYSTEM -> isSystemInDarkTheme()
                ThemeSetting.LIGHT -> false
                ThemeSetting.DARK -> true
            }
            val lang = config.display.lang
            CompositionLocalProvider(
                LocalLang provides lang,
                LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                SalahTheme(accent, dark) { SalahRoot(vm) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    /** Notifications open a specific place: the Quran player or the update. */
    private fun handleOpen(intent: Intent?) {
        val vm = vm ?: return
        when (intent?.getStringExtra(EXTRA_OPEN)) {
            OPEN_QURAN -> vm.tab = Tab.QURAN
            OPEN_UPDATE -> vm.open(MoreDest.SETTINGS)
        }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_QURAN = "quran"
        const val OPEN_UPDATE = "update"
    }
}

@Composable
private fun SalahRoot(vm: SalahViewModel) {
    val context = LocalContext.current
    val c = palette
    val config by vm.config.collectAsStateWithLifecycle()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = vm.tab != Tab.HOME || vm.more != null) {
        if (vm.tab == Tab.MORE && vm.more != null) vm.more = null else vm.tab = Tab.HOME
    }

    Scaffold(
        containerColor = c.background,
        bottomBar = {
            Column {
                val playback by vm.quran.state.collectAsStateWithLifecycle()
                if (playback.queue != null && config.quran.showInMenuBar && vm.tab != Tab.QURAN) {
                    MiniPlayer(vm, playback) { vm.tab = Tab.QURAN }
                }
                NavigationBar(containerColor = c.display) {
                    for (tab in Tab.entries) {
                        NavigationBarItem(
                            selected = vm.tab == tab,
                            onClick = {
                                // Tapping More again goes back to its list.
                                if (tab == Tab.MORE && vm.tab == Tab.MORE) vm.more = null
                                vm.tab = tab
                                if (tab != Tab.TODAY) {
                                    vm.detailPrayer = null
                                    vm.detailExtra = null
                                }
                            },
                            icon = {
                                Icon(
                                    when (tab) {
                                        Tab.HOME -> Icons.Filled.Home
                                        Tab.TODAY -> Icons.Filled.Star
                                        Tab.QURAN -> PlayerIcons.Book
                                        Tab.CALENDAR -> Icons.Filled.DateRange
                                        Tab.MORE -> Icons.Filled.Menu
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(tr(tab.title), maxLines = 1) },
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
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        Box(Modifier.fillMaxSize()) {
            when (vm.tab) {
                Tab.HOME -> HomeScreen(vm, modifier)
                Tab.TODAY -> TodayScreen(vm, modifier)
                Tab.QURAN -> QuranScreen(vm, modifier)
                Tab.CALENDAR -> CalendarScreen(vm, modifier)
                Tab.MORE -> when (vm.more) {
                    null -> MoreScreen(vm, modifier)
                    MoreDest.QIBLA -> QiblaScreen(vm, modifier)
                    MoreDest.SCHEDULE -> ScheduleScreen(vm, modifier)
                    MoreDest.REMINDERS -> RemindersScreen(vm, modifier)
                    MoreDest.FOCUS -> FocusModeScreen(vm, modifier)
                    MoreDest.KIND_WORDS -> KindWordsScreen(vm, modifier)
                    MoreDest.DUA -> DuaScreen(vm, modifier)
                    MoreDest.SETTINGS -> SettingsScreen(vm, modifier)
                }
            }
            UpdateBanner(vm, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
    if (vm.showCustomizeHome) CustomizeHomeDialog(vm)
    if (vm.showLocationSheet) LocationSheet(vm)
    if (config.display.language == null) {
        val phone = LocalConfiguration.current.locales[0]?.language
        LanguageDialog(if (phone == "ar") AppLanguage.AR else AppLanguage.EN) { picked ->
            vm.update { it.copy(display = it.display.copy(language = picked)) }
        }
    }
}
