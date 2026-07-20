package com.aesthetic.gym.domain.comeback

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * Detects that the user is coming back after a gap, so the app can reward the return.
 *
 * In the Nature 2021 mega-study (61,293 gym members, 54 programmes), the winning intervention
 * out of all of them was a bonus for people who had missed a session *and came back*. Rewarding
 * the return beats threatening the loss of a streak, and it doesn't push anyone to train while
 * injured or ill.
 */

enum class ComebackTier { SHORT, MEDIUM, LONG }

data class ComebackBonus(val days: Int, val tier: ComebackTier)

/** Never celebrate a gap shorter than this, however tight the user's usual cadence is. */
const val MIN_GAP_DAYS = 5

/** Above this the threshold stops growing, or someone very irregular would never qualify. */
const val MAX_GAP_DAYS = 14

/** Below this it isn't a comeback, it's still starting out. */
const val MIN_HISTORY_SESSIONS = 3

/** How many past training days are looked at to work out the usual cadence. */
const val CADENCE_WINDOW = 9

/**
 * Typical gap between sessions, in days. Zero when there isn't enough history.
 * The median (not the mean) so a single holiday doesn't distort it.
 */
private fun medianGap(dates: List<LocalDate>): Int {
    val ordered = dates.distinct().sortedDescending().take(CADENCE_WINDOW)
    if (ordered.size < 2) return 0
    val gaps = ordered
        .zipWithNext { newer, older -> ChronoUnit.DAYS.between(older, newer).toInt() }
        .filter { it > 0 }
        .sorted()
    if (gaps.isEmpty()) return 0
    return gaps[gaps.size / 2]
}

/**
 * How long a gap has to be before it counts as a comeback.
 *
 * The floor comes from the routine and the ceiling from real behaviour: with the median alone,
 * someone with gaps of 1, 1, 14, 1 days would get a median of 1 and collect a bonus every five
 * days. Taking the larger of the two stops that.
 */
fun thresholdDays(weeklyTarget: Int, priorDates: List<LocalDate>): Int {
    val fromTarget = ceil(7.0 / weeklyTarget.coerceIn(1, 7)).toInt()
    val fromHistory = medianGap(priorDates)
    return (maxOf(fromTarget, fromHistory) * 2).coerceIn(MIN_GAP_DAYS, MAX_GAP_DAYS)
}

/**
 * @param completedSetsToday nothing is celebrated for opening the app and leaving.
 * @param firstSeenAtDate first time this install ran. A previous session older than that means
 *        the data came from a restore, and the first session afterwards is not a real comeback.
 */
fun evaluate(
    today: LocalDate,
    priorSessionDates: List<LocalDate>,
    priorSessionCount: Int,
    weeklyTarget: Int,
    completedSetsToday: Int,
    firstSeenAtDate: LocalDate
): ComebackBonus? {
    if (completedSetsToday < 1) return null
    if (priorSessionCount < MIN_HISTORY_SESSIONS) return null

    val previous = priorSessionDates.distinct().maxOrNull() ?: return null
    if (previous.isBefore(firstSeenAtDate)) return null

    val days = ChronoUnit.DAYS.between(previous, today).toInt()
    if (days <= 0) return null
    if (days < thresholdDays(weeklyTarget, priorSessionDates)) return null

    val tier = when {
        days < 14 -> ComebackTier.SHORT
        days < 60 -> ComebackTier.MEDIUM
        else -> ComebackTier.LONG
    }
    return ComebackBonus(days, tier)
}
