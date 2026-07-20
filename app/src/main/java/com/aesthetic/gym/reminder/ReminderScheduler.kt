package com.aesthetic.gym.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aesthetic.gym.data.db.ProfileEntity
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Schedules the next reminder as a single self-rescheduling one-shot work. */
object ReminderScheduler {
    const val WORK_NAME = "zenit_reminder"

    private val CODE_TO_DAY = linkedMapOf(
        "MON" to DayOfWeek.MONDAY, "TUE" to DayOfWeek.TUESDAY, "WED" to DayOfWeek.WEDNESDAY,
        "THU" to DayOfWeek.THURSDAY, "FRI" to DayOfWeek.FRIDAY, "SAT" to DayOfWeek.SATURDAY,
        "SUN" to DayOfWeek.SUNDAY
    )
    private val DAY_TO_CODE = CODE_TO_DAY.entries.associate { (k, v) -> v to k }

    fun dayToCode(d: DayOfWeek): String = DAY_TO_CODE.getValue(d)

    fun parseDays(csv: String): Set<DayOfWeek> =
        csv.split(',').mapNotNull { CODE_TO_DAY[it.trim().uppercase()] }.toSet()

    fun formatDays(days: Set<DayOfWeek>): String =
        days.sorted().joinToString(",") { dayToCode(it) }

    /** Single entry point: schedule the next reminder, or cancel if off / no days. */
    fun sync(context: Context, profile: ProfileEntity) {
        val wm = WorkManager.getInstance(context)
        val days = parseDays(profile.reminderDays)
        if (!profile.reminderEnabled || days.isEmpty()) {
            wm.cancelUniqueWork(WORK_NAME); return
        }
        val delayMs = nextTriggerMillis(days, profile.reminderHour, profile.reminderMinute)
            ?: run { wm.cancelUniqueWork(WORK_NAME); return }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request) // never stacks
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Millis until the next (chosen day, hour:min) strictly in the future. Null if no days. */
    fun nextTriggerMillis(
        days: Set<DayOfWeek>,
        hour: Int,
        minute: Int,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Long? {
        if (days.isEmpty()) return null
        for (offset in 0..7) { // 0..7 covers "today already passed" -> same weekday +7
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek !in days) continue
            val candidate = date.atTime(hour, minute).atZone(now.zone)
            if (candidate.isAfter(now)) return Duration.between(now, candidate).toMillis()
        }
        return null
    }
}
