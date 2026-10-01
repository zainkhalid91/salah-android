package com.zainkhalid.salah.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zainkhalid.salah.ui.MainActivity
import salah.core.Prayer
import salah.core.TimeFormatting

/** Larger widget: today's six times, the next one highlighted. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetData.load(context, appWidgetId)
        provideContent { TodayContent(data) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetStyles.delete(context, appWidgetIds)
    }
}

@Composable
private fun TodayContent(data: WidgetData) {
    val size = LocalSize.current
    val c = data.colors
    val compact = size.height.value < 170f
    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(c.background)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        val state = data.state
        val location = data.config.location
        if (state == null || location == null) {
            Text("Open Salah to set your location", style = TextStyle(color = ColorProvider(c.text), fontSize = 14.sp))
            return@Column
        }
        val display = data.config.display
        val today = state.today

        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    TimeFormatting.longDate(today.date, includeYear = false).uppercase(),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(c.text), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                val hijri = data.hijri
                if (data.style.hijri && hijri != null && !compact) {
                    Text(hijri, maxLines = 1, style = TextStyle(color = ColorProvider(c.secondary), fontSize = 11.sp))
                }
            }
            Text(location.name, maxLines = 1, style = TextStyle(color = ColorProvider(c.secondary), fontSize = 11.sp))
        }
        Spacer(GlanceModifier.height(if (compact) 4.dp else 8.dp))

        val nextPrayer = state.next?.takeIf { !it.isTomorrow }?.prayer
        for (p in Prayer.entries) {
            val isNext = p == nextPrayer
            val isNow = p == state.nowPrayer
            val t = today.time(p)
            val label = today.label(p, display.jumuahRelabel)
            val fg = if (isNext) c.onHighlight else if (p == Prayer.SUNRISE) c.secondary else c.text
            Row(
                GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .cornerRadius(10.dp)
                    .background(if (isNext) c.highlight else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isNow) "$label  now" else label,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(fg), fontSize = 13.sp, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    t?.let { TimeFormatting.clock(it, today.zone, display.use24HourClock) } ?: "--:--",
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(fg), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}
