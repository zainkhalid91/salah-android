package com.zainkhalid.salah.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.zainkhalid.salah.R
import com.zainkhalid.salah.SalahApp
import com.zainkhalid.salah.reminders.Notifications
import com.zainkhalid.salah.reminders.postNotification
import com.zainkhalid.salah.repo
import com.zainkhalid.salah.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import salah.core.AppText
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Updates the app from GitHub Releases, no Play Store. Every push to main builds a signed APK
 * and publishes it with a latest.json beside it (see .github/workflows/android.yml):
 *   {"versionCode": 107, "versionName": "1.4.0 (build 7)", "apk": "https://…/Salah.apk", "sha256": "…", "notes": "…"}
 *
 * The app checks when it comes to the front (at most every 30 minutes) and once a day from the
 * midnight replan alarm. A newer build downloads quietly and its SHA-256 is checked. It installs
 * from the banner or Settings, or by itself a few minutes after the app goes to the background
 * when "Install automatically" is on. The first update asks once (allow this source, then
 * Update); after that Android 12+ lets the app update itself without asking.
 */
class AppUpdater(private val context: Context) {

    data class Release(val code: Long, val name: String, val apk: String, val sha256: String, val notes: String?)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val checkedAt: Long) : State
        data class Downloading(val release: Release) : State
        data class Ready(val release: Release, val file: File) : State
        data object Installing : State
        data class Failed(val message: String) : State
    }

    private val dir = File(context.cacheDir, "updates").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("salah_updates", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var lastCheck = 0L
    private var backgroundInstall: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val _dismissedCode = MutableStateFlow(prefs.getLong(KEY_DISMISSED, 0L))
    /** "Later" on the banner hides it for that build only. */
    val dismissedCode: StateFlow<Long> = _dismissedCode

    /** Android's confirm screen, when it arrived while the app was in the background. */
    var pendingConfirm: Intent? = null

    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_CHECK, value) }

    var autoInstall: Boolean
        get() = prefs.getBoolean(KEY_AUTO_INSTALL, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_INSTALL, value) }

    val installedCode: Long
        get() = context.packageManager.getPackageInfo(context.packageName, 0).let {
            if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        }

    init {
        // A build downloaded earlier (and still newer) is ready without another download.
        scope.launch { lock.withLock { restoreDownloaded() } }
    }

    /** At most every [every]; network errors just wait for the next check. */
    fun checkSoon(every: Duration = 30.minutes, notify: Boolean = false) {
        if (!autoCheck) return
        val now = System.currentTimeMillis()
        if (now - lastCheck < every.inWholeMilliseconds) return
        check(notify)
    }

    /** "Check now" in Settings: always checks and reports errors. */
    fun check(notify: Boolean = false) {
        lastCheck = System.currentTimeMillis()
        scope.launch {
            lock.withLock {
                val before = _state.value
                if (before !is State.Ready) _state.value = State.Checking
                runCatching { fetch() }
                    .onSuccess { if (notify) notifyIfReady() }
                    .onFailure {
                        Log.i(TAG, "Update check failed", it)
                        _state.value = if (before is State.Ready) before else State.Failed(it.message ?: "Couldn't check for updates.")
                    }
            }
        }
    }

    fun onForeground() {
        backgroundInstall?.cancel()
        checkSoon()
    }

    fun onBackground() {
        backgroundInstall?.cancel()
        if (!autoInstall) return
        backgroundInstall = scope.launch {
            delay(5.minutes)
            install()
        }
    }

    fun dismiss(code: Long) {
        prefs.edit { putLong(KEY_DISMISSED, code) }
        _dismissedCode.value = code
    }

    fun install() {
        val ready = _state.value as? State.Ready ?: return
        _state.value = State.Installing
        scope.launch {
            runCatching { commit(ready.file) }.onFailure {
                Log.w(TAG, "Install failed", it)
                _state.value = ready
            }
        }
    }

    /** Called by [UpdateInstallReceiver]: back to Ready if the install didn't happen. */
    internal fun onInstallFinished(ok: Boolean) {
        if (!ok) scope.launch { lock.withLock { restoreDownloaded() } }
    }

    private fun fetch() {
        val json = JSONObject(get("$LATEST_URL?t=${System.currentTimeMillis()}").decodeToString())
        val release = Release(
            code = json.getLong("versionCode"),
            name = json.getString("versionName"),
            apk = json.getString("apk"),
            sha256 = json.getString("sha256").lowercase(),
            notes = json.optString("notes").ifBlank { null },
        )
        if (release.code <= installedCode) {
            dir.listFiles()?.forEach { it.delete() }
            _state.value = State.UpToDate(System.currentTimeMillis())
            return
        }
        val current = _state.value
        if (current is State.Ready && current.release.code >= release.code) return

        val file = File(dir, "salah-${release.code}.apk")
        if (!(file.exists() && sha256(file) == release.sha256)) {
            _state.value = State.Downloading(release)
            val part = File(dir, file.name + ".part")
            // GitHub redirects release assets to its CDN; HttpURLConnection follows https → https.
            (URL(release.apk).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 60_000 }.use { c ->
                check(c.responseCode == 200) { "HTTP ${c.responseCode} downloading the update" }
                c.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            }
            check(sha256(part) == release.sha256) { "Downloaded update doesn't match its checksum" }
            part.renameTo(file)
        }
        dir.listFiles()?.filter { it != file }?.forEach { it.delete() }
        File(dir, "release.json").writeText(json.toString())
        _state.value = State.Ready(release, file)
        Log.i(TAG, "Update ${release.name} ready")
    }

    private fun restoreDownloaded() {
        val meta = File(dir, "release.json").takeIf { it.exists() } ?: return
        val json = runCatching { JSONObject(meta.readText()) }.getOrNull() ?: return
        val release = Release(json.getLong("versionCode"), json.getString("versionName"), json.getString("apk"), json.getString("sha256").lowercase(), json.optString("notes").ifBlank { null })
        val file = File(dir, "salah-${release.code}.apk")
        _state.value = if (release.code > installedCode && file.exists() && sha256(file) == release.sha256) State.Ready(release, file) else {
            dir.listFiles()?.forEach { it.delete() }
            State.Idle
        }
    }

    /** From the daily check: a notification, unless the app is open (it shows the banner then). */
    private fun notifyIfReady() {
        val ready = _state.value as? State.Ready ?: return
        if ((context.applicationContext as SalahApp).frontActivity != null) return
        if (prefs.getLong(KEY_NOTIFIED, 0L) == ready.release.code) return
        prefs.edit { putLong(KEY_NOTIFIED, ready.release.code) }
        val lang = context.repo.config.value.display.lang
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_UPDATE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = ready.release.notes ?: AppText.t(lang, "Tap to install.")
        val n = NotificationCompat.Builder(context, Notifications.UPDATES_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_salah)
            .setColor(ContextCompat.getColor(context, R.color.crimson))
            .setContentTitle(AppText.t(lang, "Salah {0} is ready", ready.release.name))
            .setContentText(body.substringBefore("\n"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        postNotification(context, NOTIFICATION_ID, n)
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("salah.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val done = PendingIntent.getBroadcast(
                context, id, Intent(context, UpdateInstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
            )
            session.commit(done.intentSender)
        }
    }

    private fun get(url: String): ByteArray =
        (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 15_000 }.use { c ->
            check(c.responseCode == 200) { if (c.responseCode == 404) "No release published yet." else "HTTP ${c.responseCode} checking for updates" }
            c.inputStream.use { it.readBytes() }
        }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T = try { block(this) } finally { disconnect() }

    companion object {
        const val TAG = "SalahUpdate"
        /** Always the newest release's latest.json; GitHub redirects /latest/download/ to it. */
        const val LATEST_URL = "https://github.com/zainkhalid91/salah-android/releases/latest/download/latest.json"
        const val RELEASES_URL = "https://github.com/zainkhalid91/salah-android/releases"
        private const val KEY_AUTO_CHECK = "autoCheck"
        private const val KEY_AUTO_INSTALL = "autoInstall"
        private const val KEY_DISMISSED = "dismissed"
        private const val KEY_NOTIFIED = "notified"
        private const val NOTIFICATION_ID = 0x5A1C
    }
}

/** The midnight replan alarm's once-a-day check. */
object UpdateChecker {
    fun dailyCheck(context: Context) {
        (context.applicationContext as SalahApp).updater.checkSoon(every = 20.hours, notify = true)
    }
}

/** PackageInstaller's answer: show Android's confirm screen when it needs one. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SalahApp
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val front = app.frontActivity
                if (front != null) front.startActivity(confirm) else app.updater.pendingConfirm = confirm
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i(AppUpdater.TAG, "Updated")
            else -> {
                Log.w(AppUpdater.TAG, "Install ended with $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                app.updater.onInstallFinished(ok = false)
            }
        }
    }
}
