package com.zainkhalid.salah.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.zainkhalid.salah.data.AccentColor
import com.zainkhalid.salah.repo
import com.zainkhalid.salah.ui.ColorSwatches
import com.zainkhalid.salah.ui.Eyebrow
import com.zainkhalid.salah.ui.LocalLang
import com.zainkhalid.salah.ui.Segmented
import com.zainkhalid.salah.ui.SalahTheme
import com.zainkhalid.salah.ui.SwitchRow
import com.zainkhalid.salah.ui.isDark
import com.zainkhalid.salah.ui.palette
import com.zainkhalid.salah.ui.pixelStyle
import com.zainkhalid.salah.ui.tr
import kotlinx.coroutines.launch

/** Opens when a widget is added (or long-pressed to reconfigure) to pick its colours. */
class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Backing out without saving keeps the widget off the home screen.
        setResult(RESULT_CANCELED, resultIntent())

        val start = WidgetStyles.load(this, appWidgetId)
        val config = repo.config.value
        setContent {
            val lang = config.display.lang
            CompositionLocalProvider(
                LocalLang provides lang,
                LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                SalahTheme(repo.accent.value, isDark(config.display.theme)) {
                    WidgetConfigScreen(start) { style -> save(style) }
                }
            }
        }
    }

    private fun save(style: WidgetStyle) {
        WidgetStyles.save(this, appWidgetId, style)
        lifecycleScope.launch {
            WidgetRefresher.refreshNow(applicationContext)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun WidgetConfigScreen(start: WidgetStyle, onSave: (WidgetStyle) -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(start) }
    val c = palette
    val appAccent = context.repo.accent.value
    val appDark = context.isDark(context.repo.config.value.display.theme)
    val colors = WidgetStyles.colors(context, style, appAccent, appDark)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(tr("Widget style"), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = c.text)

        // Preview with the chosen colours.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.background)
                .padding(16.dp),
        ) {
            Text(tr("NEXT PRAYER"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.secondary)
            Text(tr("Asr").uppercase(), style = pixelStyle(34f), color = colors.text)
            Text("15:42", style = pixelStyle(40f), color = colors.text)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.highlight)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(tr("Maghrib"), color = colors.onHighlight, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("18:21", color = colors.onHighlight, fontWeight = FontWeight.Bold)
            }
        }

        Eyebrow(tr("Colour").uppercase())
        ColorSwatches(
            options = listOf<AccentColor?>(null) + AccentColor.available,
            selected = style.color,
            appAccent = appAccent,
            onSelect = { style = style.copy(color = it) },
        )

        Eyebrow(tr("Background").uppercase())
        Segmented(
            options = WidgetBackground.entries.map { it to tr(it.title) },
            selected = style.background,
            onSelect = { style = style.copy(background = it) },
        )

        SwitchRow(tr("Live countdown"), style.countdown) { style = style.copy(countdown = it) }
        SwitchRow(tr("Hijri date"), style.hijri) { style = style.copy(hijri = it) }

        Spacer(Modifier.height(4.dp))
        Button(onClick = { onSave(style) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Save widget")) }
    }
}
