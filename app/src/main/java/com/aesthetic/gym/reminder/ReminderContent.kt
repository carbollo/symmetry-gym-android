package com.aesthetic.gym.reminder

import com.aesthetic.gym.data.db.RoutineWithDays

/** Next day of the routine (cyclic) after the last trained one; null if no routine/days. */
fun nextRoutineDayName(routine: RoutineWithDays?, lastFinishedDayId: Long?): String? {
    val days = routine?.sortedDays?.takeIf { it.isNotEmpty() } ?: return null
    val idx = days.indexOfFirst { it.day.id == lastFinishedDayId }
    val next = if (idx < 0) days.first() else days[(idx + 1) % days.size]
    return next.day.name
}

enum class ReminderPlan { NORMAL, WEEKLY, DISABLE }

/**
 * Decreasing rule (HeartSteps: the effect fades in ~1 month).
 * ignoredStreak = reminders shown in a row with no workout after them.
 *  0..2 -> daily | 3..5 -> once a week, quiet | >=6 -> auto-disable with one final notice.
 */
fun decideReminderPlan(ignoredStreak: Int): ReminderPlan = when {
    ignoredStreak >= 6 -> ReminderPlan.DISABLE
    ignoredStreak >= 3 -> ReminderPlan.WEEKLY
    else -> ReminderPlan.NORMAL
}
