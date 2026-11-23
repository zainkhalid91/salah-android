package com.zainkhalid.salah

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.zainkhalid.salah.data.SalahRepository
import com.zainkhalid.salah.reminders.Notifications
import com.zainkhalid.salah.reminders.ReminderScheduler
import com.zainkhalid.salah.update.AppUpdater

class SalahApp : Application() {
    lateinit var repo: SalahRepository
        private set
    lateinit var updater: AppUpdater
        private set

    /** The started activity in front, for the updater's confirm screen; null in the background. */
    var frontActivity: Activity? = null
        private set
    private var started = 0

    override fun onCreate() {
        super.onCreate()
        repo = SalahRepository(this)
        updater = AppUpdater(this)
        Notifications.createChannels(this)
        ReminderScheduler.reschedule(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) updater.onForeground()
            }

            override fun onActivityResumed(activity: Activity) {
                frontActivity = activity
                // An install confirm that arrived while in the background.
                updater.pendingConfirm?.let {
                    updater.pendingConfirm = null
                    activity.startActivity(it)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (frontActivity === activity) frontActivity = null
            }

            override fun onActivityStopped(activity: Activity) {
                if (--started == 0) updater.onBackground()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

val Context.repo: SalahRepository get() = (applicationContext as SalahApp).repo
val Context.updater: AppUpdater get() = (applicationContext as SalahApp).updater
