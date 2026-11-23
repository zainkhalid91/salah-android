package com.zainkhalid.salah.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zainkhalid.salah.data.AccentColor
import com.zainkhalid.salah.location.DeviceLocation
import com.zainkhalid.salah.quran.QuranPlayer
import com.zainkhalid.salah.repo
import com.zainkhalid.salah.update.AppUpdater
import com.zainkhalid.salah.updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import salah.core.ExtraTime
import salah.core.LocationSearch
import salah.core.Prayer
import salah.core.SalahConfig
import salah.core.SavedLocation
import java.time.LocalDate

enum class Tab(val title: String) { HOME("Home"), TODAY("Today"), QURAN("Quran"), CALENDAR("Calendar"), MORE("More") }

/** Screens under the More tab. */
enum class MoreDest(val title: String, val subtitle: String) {
    QIBLA("Qibla", "Compass to the Kaaba"),
    SCHEDULE("Schedule", "Week and month prayer times"),
    REMINDERS("Reminders", "Notifications before and at each prayer"),
    FOCUS("Focus Mode", "A calm card and quiet phone at prayer time"),
    KIND_WORDS("Kind Words", "Gentle reminders through the day"),
    DUA("Du'a", "Supplications for every moment"),
    SETTINGS("Settings", "Location, calculation, look and updates"),
}

class SalahViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.repo

    val config: StateFlow<SalahConfig> = repo.config
    val accent: StateFlow<AccentColor> = repo.accent

    var tab by mutableStateOf(Tab.HOME)
    /** The open screen under More, or null for the More list. */
    var more by mutableStateOf<MoreDest?>(null)
    var showCustomizeHome by mutableStateOf(false)
    var showQueue by mutableStateOf(false)

    val quran: QuranPlayer get() = QuranPlayer.get(getApplication())
    val updater: AppUpdater = app.updater

    fun open(dest: MoreDest) {
        tab = Tab.MORE
        more = dest
    }
    var previewDate by mutableStateOf<LocalDate?>(null)
    var detailPrayer by mutableStateOf<Prayer?>(null)
    var detailExtra by mutableStateOf<ExtraTime?>(null)
    var showLocationSheet by mutableStateOf(false)

    var locating by mutableStateOf(false)
        private set
    var locationMessage by mutableStateOf<String?>(null)
        private set
    var searching by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<SavedLocation>>(emptyList())
        private set
    var searchMessage by mutableStateOf<String?>(null)
        private set

    /** Back to the live display. */
    fun clearDetail() {
        detailPrayer = null
        detailExtra = null
        previewDate = null
    }

    fun update(body: (SalahConfig) -> SalahConfig) = repo.update(body)

    fun setAccent(accent: AccentColor) = repo.setAccent(accent)

    fun setLocation(location: SavedLocation) {
        update { it.copy(location = location) }
        showLocationSheet = false
        results = emptyList()
    }

    fun locate() {
        if (locating) return
        locating = true
        locationMessage = null
        viewModelScope.launch {
            val found = runCatching { DeviceLocation.current(getApplication()) }.getOrNull()
            if (found != null) {
                setLocation(found)
            } else {
                locationMessage = "Couldn't find your location. Check that location is on, or search for your city."
            }
            locating = false
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        searching = true
        searchMessage = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { LocationSearch.search(query) } }
            results = r.getOrDefault(emptyList())
            searchMessage = r.exceptionOrNull()?.message
            searching = false
        }
    }

    fun regionOf(location: SavedLocation): String? = LocationSearch.region(location)
}
