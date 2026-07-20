package com.aesthetic.gym

import android.app.Application
import com.aesthetic.gym.di.AppContainer
import com.aesthetic.gym.reminder.ReminderScheduler
import com.aesthetic.gym.ui.ads.Ads
import com.aesthetic.gym.util.Notifications
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ZenitApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        Notifications.ensureChannel(this) // cheap + idempotent; channel ready before the first notify
        container = AppContainer(this)
        appScope.launch { container.repository.ensureSeeded() }
        // Off the main thread on purpose: initialize() does disk I/O and would stutter the launch.
        appScope.launch { Ads.initialize(this@ZenitApp) }
        // Safety net: re-arm the schedule in case WorkManager's queue was cleared (force-stop, wipe).
        appScope.launch {
            container.repository.getProfile()?.let { ReminderScheduler.sync(this@ZenitApp, it) }
        }
    }
}
