package com.aesthetic.gym.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aesthetic.gym.MainActivity
import com.aesthetic.gym.R
import com.aesthetic.gym.ZenitApp
import com.aesthetic.gym.util.Notifications
import java.time.LocalDate
import java.time.ZoneId

/**
 * Posts at most one reminder on a chosen day and reschedules itself. Everything about tone and
 * frequency is enforced here and in [ReminderCopy]; it never nags and never mentions absences.
 */
class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The container is created in ZenitApp.onCreate, before any Worker runs.
        val repo = (applicationContext as ZenitApp).container.repository
        val profile = repo.getProfile() ?: return Result.success()
        if (!profile.reminderEnabled) return Result.success() // turned off between scheduling and now
        val days = ReminderScheduler.parseDays(profile.reminderDays)
        if (days.isEmpty()) return Result.success()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        // 1) Reconcile the streak against real training since the last reminder shown.
        var streak = profile.reminderIgnoredStreak
        if (profile.reminderLastShownAt > 0L &&
            repo.trainedBetween(profile.reminderLastShownAt, now) > 0
        ) {
            streak = 0
        }

        // 2) Decreasing rule.
        val plan = decideReminderPlan(streak)
        if (plan == ReminderPlan.DISABLE) {
            if (Notifications.canPostReminders(applicationContext)) {
                show(ReminderCopy.PAUSED_TITLE, ReminderCopy.PAUSED_BODY)
            }
            repo.saveProfile(
                profile.copy(reminderEnabled = false, reminderIgnoredStreak = 0, reminderLastShownAt = 0L)
            )
            ReminderScheduler.cancel(applicationContext)
            return Result.success()
        }
        val reduced = plan == ReminderPlan.WEEKLY

        // 3) Post at most one: chosen day, not trained today, and within the weekly cap if reduced.
        val isTrainingDay = today.dayOfWeek in days                 // safety net against Doze drift
        val alreadyTrained = repo.trainedBetween(dayStart, dayEnd) > 0
        val weeklyThrottle = reduced && profile.reminderLastShownAt > 0L &&
            (now - profile.reminderLastShownAt) < SEVEN_DAYS_MS

        var lastShownAt = profile.reminderLastShownAt
        if (isTrainingDay && !alreadyTrained && !weeklyThrottle &&
            Notifications.canPostReminders(applicationContext)
        ) {
            val dayName = nextRoutineDayName(repo.activeRoutine(), repo.lastFinishedDayId())
            val body = if (dayName != null) ReminderCopy.bodyWithDay(dayName) else ReminderCopy.GENERIC_BODY
            show(ReminderCopy.REMINDER_TITLE, body)
            streak += 1              // optimistic: counts as ignored until proven otherwise
            lastShownAt = now
        }

        repo.saveProfile(profile.copy(reminderIgnoredStreak = streak, reminderLastShownAt = lastShownAt))
        // 4) Chain the next occurrence (whether or not it trained).
        ReminderScheduler.sync(applicationContext, profile)
        return Result.success()
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun show(title: String, body: String) {
        val n = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // existing vector (tinted silhouette)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent())               // only opens the app; never auto-starts a workout
            .setAutoCancel(true)                             // dismissible with a swipe
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, n) // fixed id: replaces, never stacks
        } catch (_: SecurityException) {
            // Permission revoked mid-race: never crash a background worker.
        }
    }

    companion object {
        const val NOTIF_ID = 2001
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
