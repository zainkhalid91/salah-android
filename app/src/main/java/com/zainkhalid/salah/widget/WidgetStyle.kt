package com.zainkhalid.salah.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import com.zainkhalid.salah.data.AccentColor
import com.zainkhalid.salah.ui.SalahColors

enum class WidgetBackground(val title: String) {
    APP("App theme"),
    LIGHT("Light"),
    DARK("Dark"),
    ACCENT("Colour"),
    GLASS("Glass"),
}

/**
 * Per widget look. [color] null means "use the app's colour", so the widget
 * changes along with the app.
 */
data class WidgetStyle(
    val color: AccentColor? = null,
    val background: WidgetBackground = WidgetBackground.APP,
    val countdown: Boolean = true,
    val hijri: Boolean = true,
)

data class WidgetColors(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val highlight: Color,
    val onHighlight: Color,
)

object WidgetStyles {
    private const val PREFS = "salah_widgets"

    fun load(context: Context, id: Int): WidgetStyle {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetStyle(
            color = p.getString("color_$id", null)?.let { AccentColor.fromKey(it) },
            background = WidgetBackground.entries.firstOrNull { it.name == p.getString("bg_$id", null) } ?: WidgetBackground.APP,
            countdown = p.getBoolean("countdown_$id", true),
            hijri = p.getBoolean("hijri_$id", true),
        )
    }

    fun save(context: Context, id: Int, style: WidgetStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (style.color == null) remove("color_$id") else putString("color_$id", style.color.key)
            putString("bg_$id", style.background.name)
            putBoolean("countdown_$id", style.countdown)
            putBoolean("hijri_$id", style.hijri)
        }
    }

    fun delete(context: Context, ids: IntArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            for (id in ids) {
                remove("color_$id")
                remove("bg_$id")
                remove("countdown_$id")
                remove("hijri_$id")
            }
        }
    }

    fun colors(context: Context, style: WidgetStyle, appAccent: AccentColor, appDark: Boolean): WidgetColors {
        val accent = style.color ?: appAccent
        fun plain(dark: Boolean): WidgetColors {
            val c = SalahColors.of(context, accent, dark)
            return WidgetColors(c.display, c.text, c.secondary, c.timeline, c.onTimeline)
        }
        return when (style.background) {
            WidgetBackground.APP -> plain(appDark)
            WidgetBackground.LIGHT -> plain(false)
            WidgetBackground.DARK -> plain(true)
            WidgetBackground.ACCENT -> {
                val c = SalahColors.of(context, accent, appDark)
                WidgetColors(c.timeline, c.onTimeline, c.onTimelineDim, Color.White.copy(alpha = 0.2f), Color.White)
            }
            WidgetBackground.GLASS -> {
                val c = SalahColors.of(context, accent, true)
                WidgetColors(Color.Black.copy(alpha = 0.45f), Color.White, Color.White.copy(alpha = 0.7f), c.accent, c.onAccent)
            }
        }
    }
}
