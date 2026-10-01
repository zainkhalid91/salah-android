package com.zainkhalid.salah

import android.app.Application
import android.content.Context
import com.zainkhalid.salah.data.SalahRepository
import com.zainkhalid.salah.reminders.Notifications
import com.zainkhalid.salah.reminders.ReminderScheduler

class SalahApp : Application() {
    lateinit var repo: SalahRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = SalahRepository(this)
        Notifications.createChannels(this)
        ReminderScheduler.reschedule(this)
    }
}

val Context.repo: SalahRepository get() = (applicationContext as SalahApp).repo
