package com.aesthetic.gym.util

import kotlin.math.roundToLong

/**
 * Plate calculator.
 *
 * The user asks for a TOTAL WEIGHT OF PLATES — the bar, the sled or the machine itself is never
 * subtracted — and says across how many LOADING POINTS it is spread: 1 (a single spot), 2 (a
 * normal barbell, one side each), 4 (a four-post leg press), 6 (a big press). Every point gets
 * the same set of plates.
 *
 * All the arithmetic is done in whole grams (Long): no epsilon, no floating point drift, and the
 * divisions by the number of points are exact by construction.
 */
object PlateCalculator {

    /** Presets of the picker: one spot, barbell (2 sides), press (4 posts), big press (6). */
    val POINT_OPTIONS = listOf(1, 2, 4, 6)

    const val DEFAULT_POINTS = 2
    const val MAX_POINTS = 8

    /** Standard gym plates, heaviest first, in grams. */
    private val PLATES_G = longArrayOf(25_000, 20_000, 15_000, 10_000, 5_000, 2_500, 1_250)
    private const val MIN_PLATE_G = 1_250L

    /** Sanity cap of 2000 kg, so a mistyped field can't produce an absurd list. */
    private const val MAX_TOTAL_G = 2_000_000L

    /** A reachable load: the plates of ONE point and the totals they add up to. */
    data class Load(
        /** Plates of a single point, heaviest first. Empty = no plates. */
        val perPoint: List<Double>,
        /** Kilos of plates on ONE point. */
        val perPointKg: Double,
        /** Kilos of plates across ALL points (perPointKg × points). */
        val totalKg: Double
    )

    data class Result(
        /** What the user asked for, sanitised and snapped to the gram. */
        val targetKg: Double,
        /** Loading points actually used (clamped to 1..MAX_POINTS). */
        val points: Int,
        /** Heaviest reachable total that is <= the target. */
        val below: Load,
        /** Lightest reachable total that is > the target. */
        val above: Load,
        /** The one shown big: the exact match, or whichever is closest to the target. */
        val best: Load,
        /** True when the target can be hit exactly. */
        val isExact: Boolean,
        /** Smallest possible step of the TOTAL with these points: 1.25 kg × points. */
        val stepKg: Double
    ) {
        /** Negative = you fall short, positive = you go over, 0 = exact. */
        val diffKg: Double get() = best.totalKg - targetKg
    }

    fun compute(targetKg: Double, points: Int = DEFAULT_POINTS): Result {
        val p = points.coerceIn(1, MAX_POINTS)
        val safe = if (targetKg.isFinite()) targetKg else 0.0
        val targetG = (safe * 1000.0).roundToLong().coerceIn(0L, MAX_TOTAL_G)

        val stepG = MIN_PLATE_G * p
        val steps = targetG / stepG
        val rest = targetG - steps * stepG

        val below = load(steps * stepG, p)
        val above = load((steps + 1) * stepG, p)
        val stepKg = stepG / 1000.0
        val safeKg = targetG / 1000.0

        if (rest == 0L) {
            return Result(safeKg, p, below, above, below, isExact = true, stepKg = stepKg)
        }
        val best = when {
            // Below the smallest step: showing "no plates" as the answer helps nobody.
            steps == 0L -> above
            // Exact tie (right in the middle of a step): prefer falling short over going over.
            rest <= stepG - rest -> below
            else -> above
        }
        return Result(safeKg, p, below, above, best, isExact = false, stepKg = stepKg)
    }

    private fun load(totalG: Long, points: Int): Load {
        val perPointG = totalG / points // exact: totalG is always a multiple of 1250 × points
        var remaining = perPointG
        val used = ArrayList<Double>()
        for (plate in PLATES_G) {
            while (remaining >= plate) {
                used += plate / 1000.0
                remaining -= plate
            }
        }
        return Load(used, perPointG / 1000.0, totalG / 1000.0)
    }

    /** Groups the plates of one point as "2 × 20 kg", heaviest first. */
    fun grouped(perPoint: List<Double>): List<Pair<Double, Int>> =
        perPoint.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }

    /** "sitio" / "lados" / "palos" depending on the number of points, for the Spanish UI. */
    fun pointWord(points: Int): String = when (points) {
        1 -> "sitio"
        2 -> "lados"
        else -> "palos"
    }
}
