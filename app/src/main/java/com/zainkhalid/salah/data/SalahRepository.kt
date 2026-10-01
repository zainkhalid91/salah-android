package com.zainkhalid.salah.data

import android.content.Context
import androidx.core.content.edit
import com.zainkhalid.salah.reminders.ReminderScheduler
import com.zainkhalid.salah.widget.WidgetRefresher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import salah.core.ConfigStore
import salah.core.SalahConfig
import java.io.File

/**
 * Holds the config (same JSON format as the macOS and Windows apps) plus the
 * Android-only look settings. Every change replans reminders and redraws widgets.
 */
class SalahRepository(private val context: Context) {
    private val file = File(context.filesDir, "config.json")
    private val prefs = context.getSharedPreferences("salah_android", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<SalahConfig> = _config.asStateFlow()

    private val _accent = MutableStateFlow(AccentColor.fromKey(prefs.getString(KEY_ACCENT, null)))
    val accent: StateFlow<AccentColor> = _accent.asStateFlow()

    fun update(body: (SalahConfig) -> SalahConfig) {
        val next = body(_config.value)
        if (next == _config.value) return
        _config.value = next
        write(next)
        changed()
    }

    fun setAccent(accent: AccentColor) {
        prefs.edit { putString(KEY_ACCENT, accent.key) }
        _accent.value = accent
        WidgetRefresher.refresh(context)
    }

    private fun load(): SalahConfig {
        if (!file.exists()) return SalahConfig.DEFAULT
        return runCatching { ConfigStore.decode(file.readText(), file.path) }.getOrDefault(SalahConfig.DEFAULT)
    }

    private fun write(config: SalahConfig) {
        // Write next to the file, then rename, so a crash never leaves half a file.
        val tmp = File(file.parentFile, "config.json.tmp")
        tmp.writeText(ConfigStore.encode(config))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun changed() {
        ReminderScheduler.reschedule(context)
        WidgetRefresher.refresh(context)
    }

    private companion object {
        const val KEY_ACCENT = "accent"
    }
}
