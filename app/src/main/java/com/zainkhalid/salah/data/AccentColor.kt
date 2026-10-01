package com.zainkhalid.salah.data

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Colour themes. Crimson is the original Salah red; the rest keep the same
 * contrast, a deep timeline colour plus a brighter accent for each mode.
 * Wallpaper follows the system's Material You colours on Android 12+.
 */
enum class AccentColor(
    val key: String,
    val title: String,
    private val lightTimeline: Long,
    private val lightAccent: Long,
    private val darkTimeline: Long,
    private val darkAccent: Long,
) {
    CRIMSON("crimson", "Crimson", 0xFFA10F24, 0xFFC21D35, 0xFF7E0C1C, 0xFFF06377),
    EMERALD("emerald", "Emerald", 0xFF0E6B49, 0xFF128A5E, 0xFF0B4A33, 0xFF4FD6A0),
    TEAL("teal", "Teal", 0xFF0B6E73, 0xFF0E8A90, 0xFF084B4F, 0xFF4FD8DE),
    SAPPHIRE("sapphire", "Sapphire", 0xFF1846A3, 0xFF2459C9, 0xFF12336F, 0xFF7AA7FF),
    INDIGO("indigo", "Indigo", 0xFF2E2F8F, 0xFF3E40B8, 0xFF22236A, 0xFF9A9CFF),
    AMETHYST("amethyst", "Amethyst", 0xFF5B2A9E, 0xFF7239C4, 0xFF3F1D70, 0xFFB48CFF),
    ROSE("rose", "Rose", 0xFFA3175E, 0xFFC41F72, 0xFF6E0F3F, 0xFFFF7AB8),
    AMBER("amber", "Amber", 0xFF9A4F08, 0xFFB8620C, 0xFF6B3906, 0xFFFFB45C),
    OLIVE("olive", "Olive", 0xFF5C5A12, 0xFF77741A, 0xFF403E0C, 0xFFD8CF5C),
    GRAPHITE("graphite", "Graphite", 0xFF2B2F36, 0xFF3D434C, 0xFF24272C, 0xFFB8C2CC),
    WALLPAPER("wallpaper", "Wallpaper", 0xFFA10F24, 0xFFC21D35, 0xFF7E0C1C, 0xFFF06377);

    /** Timeline and accent for a mode. */
    fun resolve(context: Context, dark: Boolean): Pair<Color, Color> {
        if (this == WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (dark) {
                val s = dynamicDarkColorScheme(context)
                s.primaryContainer to s.primary
            } else {
                val s = dynamicLightColorScheme(context)
                lerp(s.primary, Color.Black, 0.18f) to s.primary
            }
        }
        return if (dark) Color(darkTimeline) to Color(darkAccent) else Color(lightTimeline) to Color(lightAccent)
    }

    companion object {
        /** Colours that can be picked on this device. */
        val available: List<AccentColor>
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) entries else entries - WALLPAPER

        fun fromKey(key: String?): AccentColor = entries.firstOrNull { it.key == key } ?: CRIMSON
    }
}
