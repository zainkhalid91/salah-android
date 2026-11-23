package com.zainkhalid.salah.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zainkhalid.salah.update.AppUpdater

/** A downloaded update, ready to install: Install or Later (hidden for that build). */
@Composable
fun UpdateBanner(vm: SalahViewModel, modifier: Modifier = Modifier) {
    val state by vm.updater.state.collectAsStateWithLifecycle()
    val dismissed by vm.updater.dismissedCode.collectAsStateWithLifecycle()
    val ready = state as? AppUpdater.State.Ready ?: return
    if (ready.release.code == dismissed) return
    val c = palette
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.timeline)
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
    ) {
        Text(tr("Salah {0} is ready", ready.release.name), color = c.onTimeline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        ready.release.notes?.let { Text(it.lines().first(), color = c.onTimelineDim, fontSize = 12.sp, maxLines = 1) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("", Modifier.weight(1f))
            TextButton(onClick = { vm.updater.dismiss(ready.release.code) }) { Text(tr("Later"), color = c.onTimelineDim) }
            TextButton(onClick = { vm.updater.install() }) { Text(tr("Install"), color = c.onTimeline, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Settings → Updates: version, automatic checks and installs, Check now. */
@Composable
fun UpdatesPanel(vm: SalahViewModel) {
    val context = LocalContext.current
    val c = palette
    val state by vm.updater.state.collectAsStateWithLifecycle()
    var autoCheck by remember { mutableStateOf(vm.updater.autoCheck) }
    var autoInstall by remember { mutableStateOf(vm.updater.autoInstall) }
    Panel(title = tr("UPDATES")) {
        val status = when (val s = state) {
            AppUpdater.State.Idle -> tr("Version {0}", appVersionName(context))
            AppUpdater.State.Checking -> tr("Checking…")
            is AppUpdater.State.UpToDate -> tr("Version {0} is the newest", appVersionName(context))
            is AppUpdater.State.Downloading -> tr("Downloading {0}…", s.release.name)
            is AppUpdater.State.Ready -> tr("{0} is ready to install", s.release.name)
            AppUpdater.State.Installing -> tr("Installing…")
            is AppUpdater.State.Failed -> tr(s.message)
        }
        SettingRow(tr("Salah for Android"), status)
        (state as? AppUpdater.State.Ready)?.release?.notes?.let {
            Text(it, color = c.secondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row {
            TextButton(onClick = { vm.updater.check() }) { Text(tr("Check now"), color = c.accent) }
            if (state is AppUpdater.State.Ready) {
                TextButton(onClick = { vm.updater.install() }) { Text(tr("Install"), color = c.accent, fontWeight = FontWeight.SemiBold) }
            }
            TextButton(onClick = { openUrl(context, AppUpdater.RELEASES_URL) }) { Text(tr("All releases"), color = c.secondary) }
        }
        SwitchRow(tr("Check for updates automatically"), autoCheck, tr("When you open the app and once a day")) {
            autoCheck = it
            vm.updater.autoCheck = it
        }
        SwitchRow(tr("Install automatically"), autoInstall, tr("A few minutes after you leave the app"), enabled = autoCheck) {
            autoInstall = it
            vm.updater.autoInstall = it
        }
    }
}

fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
