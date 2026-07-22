package com.aesthetic.gym.social

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aesthetic.gym.MainActivity
import com.aesthetic.gym.R
import com.aesthetic.gym.data.db.AppDatabase
import com.aesthetic.gym.domain.planet.PlanetEngine
import com.aesthetic.gym.domain.planet.PlanetPrefs
import com.aesthetic.gym.util.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Aviso local (sin FCM) de novedades sociales: cada pocas horas, si hay sesión, comprueba cuántas
 * solicitudes de amistad y rutinas compartidas tienes pendientes y, si hay MÁS que la última vez que
 * avisamos, pone una notificación. No es push instantáneo (eso requeriría Firebase), pero te enteras
 * aunque no abras la app. La app también refresca en primer plano al abrir Amigos/Rutinas.
 */
object SocialNotifications {

    private const val WORK_NAME = "zenit_social_check"
    private const val PREFS = "zenit_account" // mismas prefs de la sesión: se limpian al cerrar sesión
    private const val KEY_LAST_FR = "notif_last_friend_requests"
    private const val KEY_LAST_SR = "notif_last_shared_routines"

    /** Programa la comprobación periódica (idempotente). */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SocialCheckWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Últimos recuentos por categoría con los que se avisó (o que se dieron por vistos). */
    fun lastCounts(context: Context): Pair<Int, Int> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getInt(KEY_LAST_FR, 0) to p.getInt(KEY_LAST_SR, 0)
    }

    fun setLastCounts(context: Context, friendRequests: Int, sharedRoutines: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LAST_FR, friendRequests)
            .putInt(KEY_LAST_SR, sharedRoutines)
            .apply()
    }
}

class SocialCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // tokenForBackground: en arranque frío del proceso, load() puede no haber corrido aún.
        val token = AccountManager.tokenForBackground() ?: return Result.success() // sin sesión, nada que hacer

        // De paso, mantiene fresco el planeta en la galaxia global (best-effort: la galaxia
        // solo lista mundos que crecieron hace <30 días, así que basta con este ciclo de 3 h).
        runCatching {
            val db = AppDatabase.get(applicationContext)
            val planet = withContext(Dispatchers.IO) {
                val sessions = db.workoutDao().finishedSessionsWithSetsFlow().first()
                val rows = db.workoutDao().setMuscleRowsFlow().first()
                PlanetEngine.compute(
                    sessions, rows,
                    PlanetPrefs.baseSeed(applicationContext),
                    PlanetPrefs.highWater(applicationContext)
                )
            }
            PlanetPrefs.persist(applicationContext, planet.baseSeed, planet.totalXp)
            if (planet.hasHistory) {
                withContext(Dispatchers.IO) {
                    SocialApi.publishPlanet(token, planet.totalXp, planet.species, planet.seed)
                }
            }
        }

        val summary = withContext(Dispatchers.IO) { SocialApi.notificationsSummary(token) }
            ?: return Result.success() // fallo de red: reintentará en el siguiente ciclo

        val (lastFr, lastSr) = SocialNotifications.lastCounts(applicationContext)
        // Novedad = alguna categoría SUBIÓ respecto a la última vez (por categoría, no por suma:
        // así no se enmascara una rutina nueva porque bajen las solicitudes, ni al revés).
        val novelty = summary.friendRequests > lastFr || summary.sharedRoutines > lastSr

        when {
            novelty && Notifications.canPostReminders(applicationContext) -> {
                show(summary)
                // Marcamos como vistas SOLO tras avisar de verdad.
                SocialNotifications.setLastCounts(applicationContext, summary.friendRequests, summary.sharedRoutines)
            }
            // Sin novedad: seguimos las bajadas (aceptó algo) para que una subida futura vuelva a avisar.
            !novelty -> SocialNotifications.setLastCounts(applicationContext, summary.friendRequests, summary.sharedRoutines)
            // Novedad pero no se pudo avisar (permiso/notifs off): NO tocamos el marcador; se preserva
            // el pendiente y se reintenta en el siguiente ciclo (y en cuanto se conceda el permiso).
        }
        return Result.success()
    }

    private fun show(s: NotificationSummary) {
        val parts = mutableListOf<String>()
        if (s.friendRequests > 0) {
            parts.add("${s.friendRequests} solicitud${if (s.friendRequests == 1) "" else "es"} de amistad")
        }
        if (s.sharedRoutines > 0) {
            parts.add("${s.sharedRoutines} rutina${if (s.sharedRoutines == 1) "" else "s"} compartida${if (s.sharedRoutines == 1) "" else "s"}")
        }
        if (parts.isEmpty()) return
        val body = "Tienes " + parts.joinToString(" y ") + " esperándote."

        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Novedades en Zenit")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // Permiso revocado en carrera: no crashear un worker de fondo.
        }
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            applicationContext, 1,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        const val NOTIF_ID = 2002
    }
}
