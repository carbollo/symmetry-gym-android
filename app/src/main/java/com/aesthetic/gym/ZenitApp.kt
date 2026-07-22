package com.aesthetic.gym

import android.app.Application
import com.aesthetic.gym.di.AppContainer
import com.aesthetic.gym.reminder.ReminderScheduler
import com.aesthetic.gym.premium.DeviceIdentity
import com.aesthetic.gym.premium.Premium
import com.aesthetic.gym.social.AccountManager
import com.aesthetic.gym.social.SocialNotifications
import com.aesthetic.gym.ui.ads.Ads
import com.aesthetic.gym.ui.ads.RewardedAds
import com.aesthetic.gym.util.Notifications
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
        // Una vez arrancada la SDK, dejamos un intersticial recompensado precargado para que el
        // botón de recompensa esté listo desde el primer momento (preload marshala al hilo main).
        appScope.launch {
            Ads.initialize(this@ZenitApp)
            Ads.ready.first { it }
            RewardedAds.preload(this@ZenitApp)
        }
        // Premium (server-authoritative): crea la identidad de dispositivo en el primer arranque,
        // carga el premium cacheado (offline) y consulta el estado actual del servidor.
        appScope.launch {
            DeviceIdentity.ensureCreated()
            Premium.load(this@ZenitApp)
            Premium.refresh(this@ZenitApp)
        }
        // Sesión social (opcional, modelo híbrido): carga la cacheada y la revalida si hay red.
        AccountManager.init(this)
        appScope.launch { AccountManager.load() }
        // Aviso local periódico de novedades sociales (solicitudes/rutinas). El worker no hace nada
        // si no hay sesión; sin FCM, pero te enteras sin abrir la app.
        SocialNotifications.schedule(this)
        // Safety net: re-arm the schedule in case WorkManager's queue was cleared (force-stop, wipe).
        appScope.launch {
            container.repository.getProfile()?.let { ReminderScheduler.sync(this@ZenitApp, it) }
        }
    }
}
