package com.zainkhalid.salah.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.zainkhalid.salah.R
import com.zainkhalid.salah.data.AccentColor
import salah.core.ThemeSetting

/** Same palette as the Windows and macOS apps; timeline and accent come from the chosen colour. */
@Immutable
data class SalahColors(
    val isDark: Boolean,
    val background: Color,
    val display: Color,
    val timeline: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val secondary: Color,
    val highlight: Color,
    val onTimeline: Color,
    val onTimelineDim: Color,
    val line: Color,
    val dot: Color,
) {
    companion object {
        fun of(context: Context, accent: AccentColor, dark: Boolean): SalahColors {
            val (timeline, acc) = accent.resolve(context, dark)
            return if (dark) {
                SalahColors(
                    isDark = true,
                    background = Color(0xFF121212),
                    display = Color(0xFF1E2020),
                    timeline = timeline,
                    accent = acc,
                    onAccent = lerp(Color.Black, acc, 0.12f),
                    text = Color(0xFFEDEDEA),
                    secondary = Color(0xFF9A9A96),
                    highlight = Color(0xFF2A2C2C),
                    onTimeline = lerp(Color.White, acc, 0.06f),
                    onTimelineDim = lerp(Color.White, acc, 0.32f),
                    line = Color.White.copy(alpha = 0.10f),
                    dot = Color.White.copy(alpha = 0.035f),
                )
            } else {
                SalahColors(
                    isDark = false,
                    background = Color(0xFFF3F4F1),
                    display = Color(0xFFE4E7E6),
                    timeline = timeline,
                    accent = acc,
                    onAccent = Color.White,
                    text = Color(0xFF171717),
                    secondary = Color(0xFF5E5E5C),
                    highlight = Color(0xFFF0F0EC),
                    onTimeline = lerp(Color.White, timeline, 0.05f),
                    onTimelineDim = lerp(Color.White, timeline, 0.28f),
                    line = Color.Black.copy(alpha = 0.12f),
                    dot = Color.Black.copy(alpha = 0.05f),
                )
            }
        }
    }
}

val LocalSalahColors = staticCompositionLocalOf<SalahColors> { error("No SalahColors") }

val palette: SalahColors
    @Composable get() = LocalSalahColors.current

/** True when the app should be dark for this theme setting. */
fun Context.isDark(theme: ThemeSetting): Boolean = when (theme) {
    ThemeSetting.LIGHT -> false
    ThemeSetting.DARK -> true
    ThemeSetting.SYSTEM ->
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

/** Doto pixel font (SIL OFL 1.1), Black weight only; lighter cuts lose their dots. */
val PixelFamily = FontFamily(Font(R.font.doto_black, FontWeight.Black))

fun pixelStyle(size: Float) = TextStyle(
    fontFamily = PixelFamily,
    fontWeight = FontWeight.Black,
    fontSize = size.sp,
    lineHeight = (size * 1.08f).sp,
    fontFeatureSettings = "tnum",
)

@Composable
fun SalahTheme(accent: AccentColor, dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = remember(accent, dark) { SalahColors.of(context, accent, dark) }
    val scheme = remember(colors) {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accent,
            primaryContainer = colors.highlight,
            onPrimaryContainer = colors.text,
            secondaryContainer = colors.accent.copy(alpha = 0.18f),
            onSecondaryContainer = colors.text,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.background,
            onSurface = colors.text,
            surfaceVariant = colors.display,
            onSurfaceVariant = colors.secondary,
            surfaceContainer = colors.display,
            surfaceContainerHigh = colors.highlight,
            surfaceContainerHighest = colors.highlight,
            surfaceContainerLow = colors.display,
            surfaceContainerLowest = colors.background,
            outline = colors.line,
            outlineVariant = colors.line,
        )
    }
    CompositionLocalProvider(LocalSalahColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Fine dot texture for the display: one 4x4 tile repeated by a shader. */
@Composable
fun DottedBackground(modifier: Modifier = Modifier.fillMaxSize()) {
    val colors = palette
    val brush = remember(colors.dot) {
        val tile = ImageBitmap(8, 8)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(tile), Size(8f, 8f)) {
            drawCircle(colors.dot, radius = 1.2f, center = Offset(4.2f, 4.2f))
        }
        ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    }
    Canvas(modifier) { drawRect(brush) }
}
