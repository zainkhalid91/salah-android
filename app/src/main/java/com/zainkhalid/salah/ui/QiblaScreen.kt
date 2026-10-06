package com.zainkhalid.salah.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import salah.core.Qibla
import salah.core.SavedLocation
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Within this many degrees counts as facing the Qibla. */
private const val ALIGNED_DEGREES = 3

@Composable
fun QiblaScreen(vm: SalahViewModel, modifier: Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val c = palette
    val location = config.location?.takeIf { it.isValid }

    Column(
        modifier.fillMaxSize().background(c.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(tr("Qibla"), color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        if (location == null) {
            Panel {
                SettingRow(tr("Set a location to find the Qibla"), tr("Tap to set your location"), onClick = { vm.showLocationSheet = true })
            }
            return@Column
        }
        val qibla = remember(location) { Qibla.bearing(location.latitude, location.longitude) }
        val compass = rememberCompass(location)
        CompassPanel(compass, qibla)
        if (compass.available && compass.needsCalibration) {
            Panel {
                SettingRow(tr("Compass needs calibrating"), tr("Move your phone in a figure 8 a few times, away from metal and magnets."))
            }
        }
        Details(location, qibla, compass)
        Text(
            tr("Hold your phone flat, away from metal, speakers and magnets. Cases with magnets can push the compass off by many degrees."),
            color = c.secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

// The big dial

@Composable
private fun CompassPanel(compass: CompassState, qibla: Double) {
    val c = palette
    val haptic = LocalHapticFeedback.current
    val turn by remember(compass, qibla) { derivedStateOf { Qibla.turn(compass.heading.toDouble(), qibla).roundToInt() } }
    val aligned = compass.available && compass.hasReading && abs(turn) <= ALIGNED_DEGREES
    LaunchedEffect(aligned) { if (aligned) haptic.performHapticFeedback(HapticFeedbackType.Confirm) }

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.display).border(1.dp, c.line, RoundedCornerShape(22.dp)),
    ) {
        DottedBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(tr("QIBLA DIRECTION"), Modifier.weight(1f))
                if (aligned) Text(tr("FACING THE QIBLA"), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = tracking(0.6f))
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, 320.dp)
                Dial(compass, qibla, aligned, Modifier.size(side))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelText("${qibla.roundToInt()}°", 56f)
                Text(
                    Qibla.compassPoint(qibla, LocalLang.current),
                    color = c.secondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            val status = when {
                !compass.available -> tr("No compass on this phone. Face {0}° from north.", qibla.roundToInt())
                !compass.hasReading -> tr("Hold your phone flat")
                aligned -> tr("You are facing the Qibla")
                turn > 0 -> tr("Turn right {0}°", turn)
                else -> tr("Turn left {0}°", -turn)
            }
            Text(
                status,
                color = if (aligned) c.accent else c.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * The dial turns against the phone's heading so north stays north. The Kaaba sits on the ring
 * at the Qibla bearing, and the needle points to it. The notch at the top is where the phone faces.
 * Only the draw phase reads the heading, so sensor updates redraw without recomposing.
 */
@Composable
private fun Dial(compass: CompassState, qibla: Double, aligned: Boolean, modifier: Modifier) {
    val c = palette
    val measurer = rememberTextMeasurer()
    val lang = LocalLang.current
    // Measured once, not every frame.
    val cardinal = remember(lang, c) {
        listOf(0.0, 90.0, 180.0, 270.0).mapIndexed { i, deg ->
            measurer.measure(
                Qibla.compassPoint(deg, lang),
                TextStyle(color = if (i == 0) c.accent else c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
    val label = tr("Qibla is {0}° from north", qibla.roundToInt())
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = label }) {
        val center = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 14.dp.toPx()
        val heading = if (compass.available) compass.heading else 0f

        // Glow when facing the Qibla.
        if (aligned) drawCircle(c.accent.copy(alpha = 0.12f), radius = r + 10.dp.toPx(), center = center)
        drawCircle(c.background.copy(alpha = 0.55f), radius = r, center = center)
        drawCircle(if (aligned) c.accent else c.line, radius = r, center = center, style = Stroke(if (aligned) 3.dp.toPx() else 1.5.dp.toPx()))

        rotate(-heading, center) {
            // Ticks every 5°, longer every 30°, longest at the cardinal points.
            for (deg in 0 until 360 step 5) {
                val long = deg % 90 == 0
                val mid = deg % 30 == 0
                val len = (if (long) 14 else if (mid) 9 else 5).dp.toPx()
                val a = Math.toRadians(deg.toDouble())
                val dir = Offset(sin(a).toFloat(), -cos(a).toFloat())
                drawLine(
                    if (deg == 0) c.accent else c.secondary.copy(alpha = if (long || mid) 0.9f else 0.45f),
                    center + dir * (r - len), center + dir * (r - 2.dp.toPx()),
                    strokeWidth = (if (long) 2.5 else 1.2).dp.toPx(), cap = StrokeCap.Round,
                )
            }
            // N, E, S, W, kept upright as the dial turns.
            cardinal.forEachIndexed { i, layout ->
                val a = Math.toRadians(i * 90.0)
                val p = center + Offset(sin(a).toFloat(), -cos(a).toFloat()) * (r - 30.dp.toPx())
                rotate(heading, p) {
                    drawText(layout, topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f))
                }
            }
            // Needle to the Kaaba.
            val q = Math.toRadians(qibla)
            val dir = Offset(sin(q).toFloat(), -cos(q).toFloat())
            val tip = center + dir * (r - 72.dp.toPx())
            drawLine(c.accent, center, tip, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            arrowHead(tip, q, c.accent)
            // The Kaaba, upright as the dial turns, inside the letters so it never hides one.
            val spot = center + dir * (r - 58.dp.toPx())
            rotate(heading, spot) { kaaba(spot, 18.dp.toPx(), c.accent) }
        }

        // Where the phone points: a fixed notch at the top.
        val top = center.y - r
        val notch = Path().apply {
            moveTo(center.x, top + 4.dp.toPx())
            lineTo(center.x - 7.dp.toPx(), top - 9.dp.toPx())
            lineTo(center.x + 7.dp.toPx(), top - 9.dp.toPx())
            close()
        }
        drawPath(notch, if (aligned) c.accent else c.text)
        drawCircle(c.text, radius = 4.dp.toPx(), center = center)
    }
}

private fun DrawScope.arrowHead(tip: Offset, angle: Double, color: Color) {
    val s = 9.dp.toPx()
    val back = Offset(-sin(angle).toFloat(), cos(angle).toFloat())
    val side = Offset(cos(angle).toFloat(), sin(angle).toFloat())
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        val b = tip + back * (s * 1.4f)
        lineTo(b.x + side.x * s * 0.7f, b.y + side.y * s * 0.7f)
        lineTo(b.x - side.x * s * 0.7f, b.y - side.y * s * 0.7f)
        close()
    }
    drawPath(path, color)
}

/** A small Kaaba: a dark cube with the gold band of the kiswa. */
private fun DrawScope.kaaba(center: Offset, side: Float, accent: Color) {
    translate(center.x - side / 2, center.y - side / 2) {
        drawRoundRect(Color(0xFF1B1B1B), size = Size(side, side), cornerRadius = CornerRadius(side * 0.12f))
        drawRect(Color(0xFFD4AF37), topLeft = Offset(0f, side * 0.24f), size = Size(side, side * 0.12f))
        drawRoundRect(accent, size = Size(side, side), cornerRadius = CornerRadius(side * 0.12f), style = Stroke(1.dp.toPx()))
    }
}

// Facts under the dial

@Composable
private fun Details(location: SavedLocation, qibla: Double, compass: CompassState) {
    val c = palette
    val lang = LocalLang.current
    val distance = remember(location) { Qibla.distanceKm(location.latitude, location.longitude) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.timeline).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(location.name.uppercase(), color = c.onTimelineDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = tracking(1.2f))
        Fact(tr("Qibla"), tr("{0}° from true north", String.format(Locale.ROOT, "%.1f", qibla)) + " · " + Qibla.compassPoint(qibla, lang))
        Fact(tr("Distance to the Kaaba"), tr("{0} km", String.format(Locale.US, "%,d", distance.roundToInt())))
        if (compass.available) {
            Fact(tr("Magnetic declination"), tr("{0}°, already corrected", String.format(Locale.ROOT, "%+.1f", compass.declination)))
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    val c = palette
    Row {
        Text(label, color = c.onTimelineDim, fontSize = 13.sp, modifier = Modifier.width(150.dp))
        Text(value, color = c.onTimeline, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// Sensors

/** The phone's heading in degrees from true north, smoothed. Unwrapped, so 359° to 1° turns 2°, not 358°. */
@Stable
class CompassState(val available: Boolean, val declination: Float) {
    var heading by mutableFloatStateOf(0f)
    var hasReading by mutableStateOf(false)
    var accuracy by mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
    val needsCalibration: Boolean get() = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
}

/**
 * Rotation vector sensor when the phone has one (fused, steady), otherwise accelerometer plus
 * magnetometer. The magnetometer is also read for its accuracy, which drives the calibration
 * hint. Sensors only run while the screen is resumed.
 */
@Composable
private fun rememberCompass(location: SavedLocation): CompassState {
    val context = LocalContext.current
    val sensors = remember { context.getSystemService(SensorManager::class.java) }
    val rotation = remember { sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val accel = remember { sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val magnet = remember { sensors?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    val declination = remember(location) {
        GeomagneticField(location.latitude.toFloat(), location.longitude.toFloat(), 0f, System.currentTimeMillis()).declination
    }
    val state = remember(location) { CompassState(available = rotation != null || (accel != null && magnet != null), declination = declination) }

    LifecycleResumeEffect(state, sensors) {
        val listener = HeadingListener(context, state)
        if (sensors != null && state.available) {
            if (rotation != null) sensors.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
            else sensors.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            if (magnet != null) sensors.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)
        }
        onPauseOrDispose { sensors?.unregisterListener(listener) }
    }
    return state
}

private class HeadingListener(private val context: Context, private val state: CompassState) : SensorEventListener {
    private val matrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val angles = FloatArray(3)
    private val gravity = FloatArray(3)
    private val field = FloatArray(3)
    private var hasGravity = false
    private var hasField = false
    private var useRotation = false
    private var smoothed = Float.NaN

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                useRotation = true
                SensorManager.getRotationMatrixFromVector(matrix, e.values)
                update()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lowPass(e.values, gravity, hasGravity)
                hasGravity = true
                if (!useRotation && hasField && SensorManager.getRotationMatrix(matrix, null, gravity, field)) update()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                state.accuracy = e.accuracy
                lowPass(e.values, field, hasField)
                hasField = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) state.accuracy = accuracy
    }

    private fun update() {
        val (x, y) = when (displayRotation()) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, x, y, remapped)
        SensorManager.getOrientation(remapped, angles)
        val heading = (Math.toDegrees(angles[0].toDouble()) + state.declination).toFloat()
        if (smoothed.isNaN()) {
            smoothed = heading
            state.heading = heading
        } else {
            // Ease towards the new reading along the short way round.
            val delta = Qibla.turn(smoothed.toDouble(), heading.toDouble()).toFloat() * 0.18f
            smoothed = Qibla.normalize((smoothed + delta).toDouble()).toFloat()
            state.heading += delta
        }
        state.hasReading = true
    }

    private fun lowPass(input: FloatArray, output: FloatArray, primed: Boolean) {
        for (i in 0..2) output[i] = if (primed) output[i] + 0.2f * (input[i] - output[i]) else input[i]
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.display?.rotation ?: Surface.ROTATION_0
        else context.getSystemService(WindowManager::class.java).defaultDisplay.rotation
}
