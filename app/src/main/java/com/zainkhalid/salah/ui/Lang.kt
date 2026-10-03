package com.zainkhalid.salah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import salah.core.AppLanguage
import salah.core.AppText
import java.util.Locale

val LocalLang = staticCompositionLocalOf { AppLanguage.EN }

/** The English text in the app's language. */
@Composable
@ReadOnlyComposable
fun tr(en: String, vararg args: Any): String = AppText.t(LocalLang.current, en, *args)

/** The app language with the phone's region, for dates and the first day of the week. */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalLang.current.locale(LocalConfiguration.current.locales[0] ?: Locale.getDefault())

/** Asked once, on first launch. Both names are shown in their own language. */
@Composable
fun LanguageDialog(initial: AppLanguage, onPick: (AppLanguage) -> Unit) {
    val c = palette
    var choice by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { },
        containerColor = c.background,
        title = { Text("Choose your language\nاختر لغتك", color = c.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (lang in AppLanguage.entries) {
                    val on = lang == choice
                    Text(
                        lang.nativeName,
                        color = if (on) c.onAccent else c.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (on) c.accent else c.display, RoundedCornerShape(14.dp))
                            .border(1.dp, c.line, RoundedCornerShape(14.dp))
                            .clickable { choice = lang }
                            .padding(vertical = 14.dp),
                    )
                }
                Text(
                    AppText.t(choice, "You can change it later in Settings."),
                    color = c.secondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(choice) }) { Text(AppText.t(choice, "Continue"), color = c.accent) }
        },
    )
}
