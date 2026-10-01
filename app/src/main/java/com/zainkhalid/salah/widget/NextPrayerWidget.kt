package com.zainkhalid.salah.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zainkhalid.salah.R
import com.zainkhalid.salah.ui.MainActivity
import salah.core.TimeFormatting
import java.time.Instant

/** Small widget: the next prayer in dot matrix type with a live countdown. */
class NextPrayerWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetData.load(context, appWidgetId)
        provideContent { NextPrayerContent(data) }
    }
}

class NextPrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetStyles.delete(context, appWidgetIds)
    }
}

@Composable
private fun NextPrayerContent(data: WidgetData) {
    val context = LocalContext.current
    val size = LocalSize.current
    val c = data.colors
    val density = context.resources.displayMetrics.density
    val inner = (size.width.value - 28f).coerceAtLeast(40f) * density
    val tall = size.height.value >= 130f

    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(c.background)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
    ) {
        val state = data.state
        val location = data.config.location
        if (state == null || location == null) {
            Text("SALAH", style = TextStyle(color = ColorProvider(c.secondary), fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            Text("Open the app to set your location", style = TextStyle(color = ColorProvider(c.text), fontSize = 14.sp))
            return@Column
        }
        val display = data.config.display
        val nowPrayer = state.nowPrayer
        val next = state.next
        val status: String
        val name: String
        val time: Instant?
        when {
            nowPrayer != null -> {
                status = "NOW"
                name = state.today.label(nowPrayer, display.jumuahRelabel)
                time = state.today.time(nowPrayer)
            }
            next != null -> {
                status = if (next.isTomorrow) "TOMORROW" else "NEXT PRAYER"
                name = next.label(display.jumuahRelabel)
                time = next.time
            }
            else -> {
                status = "NEXT PRAYER"
                name = "--"
                time = null
            }
        }

        Text(status, style = TextStyle(color = ColorProvider(if (nowPrayer != null) c.highlight else c.secondary), fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(4.dp))
        val nameBitmap = PixelBitmap.render(context, name.uppercase(), (if (tall) 34f else 26f) * density, inner, c.text.toArgb())
        Image(ImageProvider(nameBitmap), contentDescription = name)
        if (time != null) {
            val (t, period) = TimeFormatting.parts(time, location.zone, display.use24HourClock, padHour = true)
            Row(verticalAlignment = Alignment.Bottom) {
                val timeBitmap = PixelBitmap.render(context, t, (if (tall) 40f else 28f) * density, inner * 0.8f, c.text.toArgb())
                Image(ImageProvider(timeBitmap), contentDescription = t)
                if (period.isNotEmpty()) {
                    Text(" $period", style = TextStyle(color = ColorProvider(c.secondary), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        if (data.style.countdown && time != null && size.height.value >= 110f) {
            Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (nowPrayer != null) "STARTED " else "IN ",
                    style = TextStyle(color = ColorProvider(c.secondary), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                AndroidRemoteViews(countdown(context, time, c.text.toArgb(), counting = nowPrayer == null))
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        if (tall) {
            Row(GlanceModifier.fillMaxWidth()) {
                Text(
                    location.name,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(c.secondary), fontSize = 11.sp),
                )
            }
        }
    }
}

/** A Chronometer ticks every second on its own, so the widget doesn't need redraws to count down. */
private fun countdown(context: Context, target: Instant, color: Int, counting: Boolean): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_countdown)
    val base = SystemClock.elapsedRealtime() + (target.toEpochMilli() - System.currentTimeMillis())
    views.setChronometer(R.id.countdown, base, null, true)
    views.setChronometerCountDown(R.id.countdown, counting)
    views.setTextColor(R.id.countdown, color)
    return views
}
