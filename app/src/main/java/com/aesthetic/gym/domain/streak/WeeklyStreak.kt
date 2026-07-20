package com.aesthetic.gym.domain.streak

import com.aesthetic.gym.data.db.RoutineWithDays
import com.aesthetic.gym.util.epochToLocalDate
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Weekly streak: how many weeks in a row the user has hit their training target.
 *
 * A daily streak punishes rest days, which are part of training: with a 4-day split it would
 * read 0 every Wednesday and every weekend. Counting whole weeks lets someone train Mon/Tue/
 * Thu/Fri and still see an unbroken run.
 */

const val DEFAULT_WEEKLY_TARGET = 3

/** Single source of truth for "how many days a week does this user train". */
fun weeklyTargetFor(routine: RoutineWithDays?): Int {
    val days = routine?.days?.size ?: 0
    if (days <= 0) return DEFAULT_WEEKLY_TARGET
    // More than 7 days means a multi-week block. The PDF parser copies the heading verbatim,
    // so there is no way to tell how many weeks it spans: assume a high frequency and cap it.
    val perWeek = if (days > 7) 5 else days
    // From 5 upwards leave a day of slack. Never ask for 7 out of 7.
    val withRest = if (perWeek >= 5) perWeek - 1 else perWeek
    return withRest.coerceIn(1, 6)
}

data class WeeklyStreak(
    /** Consecutive completed weeks, not counting the current one unless it is already met. */
    val weeks: Int = 0,
    /** Distinct training days so far in the current week. */
    val doneThisWeek: Int = 0,
    val target: Int = DEFAULT_WEEKLY_TARGET,
    val currentWeekComplete: Boolean = false,
    /** False when the user has never logged a session: shows an invitation instead of a zero. */
    val hasHistory: Boolean = false
) {
    val fraction: Float
        get() = if (target <= 0) 0f else (doneThisWeek.toFloat() / target).coerceIn(0f, 1f)

    val valueLabel: String
        get() = if (!hasHistory) "—" else "$weeks sem"

    val hintLabel: String
        get() = when {
            !hasHistory -> "Empieza tu primera semana"
            // min() so an extra session never reads "6 de 5".
            currentWeekComplete -> "Semana cumplida · ${minOf(doneThisWeek, target)} de $target"
            else -> "Esta semana: $doneThisWeek de $target"
        }
}

/**
 * @param startedAt start timestamps of sessions that actually have a confirmed set.
 * @param today injectable so the tests don't depend on the day they run.
 */
fun computeWeeklyStreak(
    startedAt: List<Long>,
    weeklyTarget: Int,
    today: LocalDate = LocalDate.now()
): WeeklyStreak {
    val target = weeklyTarget.coerceIn(1, 7)
    if (startedAt.isEmpty()) return WeeklyStreak(target = target)

    // Two sessions on the same day count as one training day.
    val perWeek = startedAt
        .map { epochToLocalDate(it) }
        .distinct()
        .groupingBy { it.with(DayOfWeek.MONDAY) }
        .eachCount()

    val thisWeek = today.with(DayOfWeek.MONDAY)
    val done = perWeek[thisWeek] ?: 0
    val complete = done >= target

    // The week in progress cannot break the streak: if it isn't met yet, start counting back
    // from last week. Otherwise every Monday morning would reset it to zero.
    var cursor = if (complete) thisWeek else thisWeek.minusWeeks(1)
    var weeks = 0
    while ((perWeek[cursor] ?: 0) >= target) {
        weeks++
        cursor = cursor.minusWeeks(1)
    }

    return WeeklyStreak(
        weeks = weeks,
        doneThisWeek = done,
        target = target,
        currentWeekComplete = complete,
        hasHistory = true
    )
}
