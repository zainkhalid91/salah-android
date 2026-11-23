package com.zainkhalid.salah.focus

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zainkhalid.salah.repo
import com.zainkhalid.salah.ui.DottedBackground
import com.zainkhalid.salah.ui.Eyebrow
import com.zainkhalid.salah.ui.LocalLang
import com.zainkhalid.salah.ui.PixelText
import com.zainkhalid.salah.ui.SalahTheme
import com.zainkhalid.salah.ui.isDark
import com.zainkhalid.salah.ui.palette
import com.zainkhalid.salah.ui.tr
import com.zainkhalid.salah.ui.tracking
import kotlinx.coroutines.delay
import salah.core.Prayer
import salah.core.PrayerSchedule
import salah.core.TimeFormatting
import salah.core.focusmode.CardNote
import salah.core.kindwords.KindWordsPlanner
import salah.core.localDate
import java.time.Duration
import java.time.Instant

/**
 * Focus Mode's calm full-screen card. Opens over the lock screen at prayer time and closes on
 * "I'm done" or after the auto-close minutes, undoing what Focus Mode turned on.
 */
class FocusCardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        enableEdgeToEdge()
        val prayer = Prayer.fromRaw(intent.getStringExtra(EXTRA_PRAYER).orEmpty()) ?: return finish()
        val time = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis()))
        val note = CardNote.fromRaw(intent.getStringExtra(FocusModeRunner.EXTRA_NOTE))

        setContent {
            val config by repo.config.collectAsStateWithLifecycle()
            val accent by repo.accent.collectAsStateWithLifecycle()
            val lang = config.display.lang
            CompositionLocalProvider(
                LocalLang provides lang,
                LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                SalahTheme(accent, isDark(config.display.theme)) {
                    val closeAt = time.plus(Duration.ofMinutes(config.focusMode.card.autoCloseMinutes.toLong()))
                    LaunchedEffect(closeAt) {
                        delay(Duration.between(Instant.now(), closeAt).toMillis().coerceAtLeast(0))
                        close()
                    }
                    BackHandler { close() }
                    val c = palette
                    val zone = config.location?.zone ?: java.time.ZoneId.systemDefault()
                    val line = config.location?.takeIf { it.isValid }?.let {
                        val day = PrayerSchedule.forDate(localDate(time, zone), it, config.calculation)
                        KindWordsPlanner.beforePrayerLine(prayer, day, config, force = true)
                    }
                    Box(Modifier.fillMaxSize().background(c.background)) {
                        DottedBackground()
                        Column(
                            Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (note != null) {
                                Text(
                                    tr(if (note == CardNote.CALL_ENDED) "Call ended" else "Do Not Disturb ended"),
                                    color = c.onAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                                Spacer(Modifier.height(18.dp))
                            }
                            Eyebrow(tr("IT'S TIME FOR"), color = c.secondary)
                            Text(
                                FocusModeRunner.prayerLabel(this@FocusCardActivity, prayer, time),
                                color = c.text,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            PixelText(
                                TimeFormatting.clock(time, zone, config.display.use24HourClock, lang = lang),
                                54f,
                                color = c.accent,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            if (line != null) {
                                Text(
                                    line,
                                    color = c.secondary,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 28.dp),
                                )
                            }
                            Spacer(Modifier.height(40.dp))
                            Text(
                                tr("I'm done"),
                                color = c.onAccent,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                letterSpacing = tracking(0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(c.accent)
                                    .clickable { close() }
                                    .padding(vertical = 16.dp),
                            )
                            Text(
                                tr("Closes by itself at {0}", TimeFormatting.clock(closeAt, zone, config.display.use24HourClock, lang = lang)),
                                color = c.secondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun close() {
        FocusModeRunner.done(this)
        finish()
    }

    companion object {
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_TIME = "time"

        fun intent(context: Context, prayer: Prayer, time: Instant, note: CardNote?) =
            Intent(context, FocusCardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_PRAYER, prayer.raw)
                .putExtra(EXTRA_TIME, time.toEpochMilli())
                .putExtra(FocusModeRunner.EXTRA_NOTE, note?.raw)
    }
}
