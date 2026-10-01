package com.zainkhalid.salah.ui

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zainkhalid.salah.data.AccentColor

/** Small spaced-out label, e.g. "NEXT PRAYER". */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = palette.secondary) {
    Text(text, modifier = modifier, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
}

@Composable
fun PixelText(text: String, size: Float, modifier: Modifier = Modifier, color: Color = palette.text) {
    Text(text, modifier = modifier, color = color, maxLines = 1, softWrap = false, style = pixelStyle(size))
}

/** Rounded panel used for every settings group. */
@Composable
fun Panel(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = palette
    Column(modifier.fillMaxWidth()) {
        if (title != null) Eyebrow(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.display)
                .border(1.dp, c.line, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            content = content,
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = palette
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = c.secondary, fontSize = 12.5.sp)
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, onClick = if (enabled) ({ onChange(!checked) }) else null) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** "−  value  +" control. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", onMinus)
        Text(value, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp))
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val c = palette
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(c.text.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = c.text, fontSize = 18.sp)
    }
}

/** Pill row of options; the selected one is filled with the accent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = palette
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) c.accent else c.text.copy(alpha = 0.07f))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label, color = if (on) c.onAccent else c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Value with a drop-down list of choices. */
@Composable
fun <T> DropdownRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val c = palette
    Box {
        SettingRow(title, options.firstOrNull { it.first == selected }?.second, onClick = { open = true }) {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = c.secondary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((value, label) in options) {
                DropdownMenuItem(
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                    trailingIcon = if (value == selected) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                )
            }
        }
    }
}

/**
 * Colour dots. A null option means "follow the app" and is drawn as a split
 * dot of the app's current colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatches(
    options: List<AccentColor?>,
    selected: AccentColor?,
    appAccent: AccentColor,
    onSelect: (AccentColor?) -> Unit,
) {
    val context = LocalContext.current
    val c = palette
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (option in options) {
            val (timeline, accent) = (option ?: appAccent).resolve(context, c.isDark)
            val on = option == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(if (on) 3.dp else 1.dp, if (on) c.text else c.line, CircleShape)
                        .padding(if (on) 5.dp else 2.dp)
                        .clip(CircleShape)
                        .background(timeline)
                        .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(accent))
                }
                Text(
                    option?.title ?: "App",
                    color = if (on) c.text else c.secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
