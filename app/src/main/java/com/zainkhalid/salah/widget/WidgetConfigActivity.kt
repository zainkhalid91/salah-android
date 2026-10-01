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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.zainkhalid.salah.data.AccentColor
import com.zainkhalid.salah.repo
import com.zainkhalid.salah.ui.ColorSwatches
import com.zainkhalid.salah.ui.Eyebrow
import com.zainkhalid.salah.ui.Segmented
import com.zainkhalid.salah.ui.SalahTheme
import com.zainkhalid.salah.ui.isDark
import com.zainkhalid.salah.ui.palette
import com.zainkhalid.salah.ui.pixelStyle
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
            SalahTheme(repo.accent.value, isDark(config.display.theme)) {
                WidgetConfigScreen(start) { style -> save(style) }
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
        Text("Widget style", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = c.text)

        // Preview with the chosen colours.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.background)
                .padding(16.dp),
        ) {
            Text("NEXT PRAYER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.secondary)
            Text("ASR", style = pixelStyle(34f), color = colors.text)
            Text("15:42", style = pixelStyle(40f), color = colors.text)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.highlight)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Maghrib", color = colors.onHighlight, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("18:21", color = colors.onHighlight, fontWeight = FontWeight.Bold)
            }
        }

        Eyebrow("COLOUR")
        ColorSwatches(
            options = listOf<AccentColor?>(null) + AccentColor.available,
            selected = style.color,
            appAccent = appAccent,
            onSelect = { style = style.copy(color = it) },
        )

        Eyebrow("BACKGROUND")
        Segmented(
            options = WidgetBackground.entries.map { it to it.title },
            selected = style.background,
            onSelect = { style = style.copy(background = it) },
        )

        ToggleRow("Live countdown", style.countdown) { style = style.copy(countdown = it) }
        ToggleRow("Hijri date", style.hijri) { style = style.copy(hijri = it) }

        Spacer(Modifier.height(4.dp))
        Button(onClick = { onSave(style) }, modifier = Modifier.fillMaxWidth()) { Text("Save widget") }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
