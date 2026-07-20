package com.aesthetic.gym.util

import kotlin.math.roundToLong

/**
 * Plate calculator.
 *
 * The user asks for a TOTAL WEIGHT OF PLATES — the bar, the sled or the machine itself is never
 * subtracted — and says across how many LOADING POINTS it is spread: 1 (a single spot), 2 (a
 * normal barbell, one side each), 4 (a four-post leg press), 6 (a big press). Every point gets
 * the same set of plates. The plates available are the ones the user owns, which they pick.
 *
 * Everything is computed in whole 1.25 kg units with dynamic programming, so it works for any
 * plate selection: a greedy pass would be wrong the moment the user drops, say, the 25 kg discs
 * (with only 10 and 25 available, 15 kg simply cannot be loaded, and greedy can't tell).
 * The DP also guarantees the FEWEST plates, not just any valid combination.
 */
object PlateCalculator {

    /** Presets of the picker: one spot, barbell (2 sides), press (4 posts), big press (6). */
    val POINT_OPTIONS = listOf(1, 2, 4, 6)

    const val DEFAULT_POINTS = 2
    const val MAX_POINTS = 8

    /** Every plate a gym might have, in grams, lightest first. */
    val ALL_PLATES_G = listOf(1_250, 2_500, 5_000, 10_000, 15_000, 20_000, 25_000, 30_000)

    /** Ticked by default: the usual rack. 30 kg discs are rarer, so they start off. */
    val DEFAULT_PLATES_G = listOf(1_250, 2_500, 5_000, 10_000, 15_000, 20_000, 25_000)

    /** Everything is a whole number of these. Every plate above is a multiple of 1.25 kg. */
    private const val UNIT_G = 1_250

    /** Sanity cap of 2000 kg, so a mistyped field can't produce an absurd list. */
    private const val MAX_TOTAL_G = 2_000_000L

    private const val INF = Int.MAX_VALUE / 2

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
        /** Heaviest reachable total that is <= the target, null if none. */
        val below: Load?,
        /** Lightest reachable total that is > the target, null if past the cap. */
        val above: Load?,
        /** The one shown big: the exact match, or whichever is closest. Null with no plates. */
        val best: Load?,
        /** True when the target can be hit exactly. */
        val isExact: Boolean,
        /** Next reachable load above [best] — what the "+" button jumps to. */
        val nextUp: Load?,
        /** Previous reachable load below [best] — what the "−" button jumps to. */
        val nextDown: Load?
    ) {
        /** Negative = you fall short, positive = you go over, 0 = exact. */
        val diffKg: Double get() = (best?.totalKg ?: 0.0) - targetKg

        /** Smallest total increase possible from here, or null at the cap. */
        val stepUpKg: Double? get() =
            if (nextUp != null && best != null) nextUp.totalKg - best.totalKg else null

        /** Smallest total decrease possible from here, or null at zero. */
        val stepDownKg: Double? get() =
            if (nextDown != null && best != null) best.totalKg - nextDown.totalKg else null
    }

    /** Reads the stored "1250,2500,..." list, falling back to the default rack. */
    fun parsePlates(csv: String?): List<Int> {
        val parsed = csv?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in ALL_PLATES_G }
            ?.distinct()
            ?.sorted()
        return if (parsed.isNullOrEmpty()) DEFAULT_PLATES_G else parsed
    }

    fun formatPlates(gramsSet: Collection<Int>): String =
        gramsSet.filter { it in ALL_PLATES_G }.distinct().sorted().joinToString(",")

    fun compute(
        targetKg: Double,
        points: Int = DEFAULT_POINTS,
        platesG: List<Int> = DEFAULT_PLATES_G
    ): Result {
        val p = points.coerceIn(1, MAX_POINTS)
        val safe = if (targetKg.isFinite()) targetKg else 0.0
        val targetG = (safe * 1000.0).roundToLong().coerceIn(0L, MAX_TOTAL_G)
        val targetKgSafe = targetG / 1000.0

        // Heaviest first so that, among solutions with the same number of plates,
        // the one using the biggest disc wins.
        val plateUnits = platesG.map { it / UNIT_G }.filter { it > 0 }.distinct().sortedDescending()
        if (plateUnits.isEmpty()) {
            return Result(targetKgSafe, p, null, null, null, isExact = false, nextUp = null, nextDown = null)
        }

        val maxUnits = (MAX_TOTAL_G / (UNIT_G.toLong() * p)).toInt()
        val cost = IntArray(maxUnits + 1) { INF }
        val from = IntArray(maxUnits + 1) { -1 }
        cost[0] = 0
        for (u in 1..maxUnits) {
            for (plate in plateUnits) {
                if (plate > u) continue
                val candidate = cost[u - plate]
                if (candidate < INF && candidate + 1 < cost[u]) {
                    cost[u] = candidate + 1
                    from[u] = plate
                }
            }
        }

        val reachable = ArrayList<Int>()
        for (u in 0..maxUnits) if (cost[u] < INF) reachable += u

        fun totalGramsOf(units: Int): Long = units.toLong() * UNIT_G * p
        fun loadAt(index: Int): Load? {
            if (index < 0 || index >= reachable.size) return null
            val units = reachable[index]
            val plates = ArrayList<Double>()
            var rest = units
            while (rest > 0) {
                val plate = from[rest]
                if (plate <= 0) break
                plates += plate.toDouble() * UNIT_G / 1000.0
                rest -= plate
            }
            plates.sortDescending()
            return Load(plates, units.toDouble() * UNIT_G / 1000.0, totalGramsOf(units) / 1000.0)
        }

        var belowIndex = -1
        for (i in reachable.indices) {
            if (totalGramsOf(reachable[i]) <= targetG) belowIndex = i else break
        }
        val aboveIndex = if (belowIndex + 1 < reachable.size) belowIndex + 1 else -1

        val exact = belowIndex >= 0 && totalGramsOf(reachable[belowIndex]) == targetG
        val bestIndex = when {
            exact -> belowIndex
            belowIndex < 0 -> aboveIndex
            aboveIndex < 0 -> belowIndex
            // "No plates at all" is never a useful answer when a weight was asked for.
            reachable[belowIndex] == 0 -> aboveIndex
            else -> {
                val short = targetG - totalGramsOf(reachable[belowIndex])
                val over = totalGramsOf(reachable[aboveIndex]) - targetG
                // Tie goes to falling short: better light than impossible to lift.
                if (short <= over) belowIndex else aboveIndex
            }
        }

        return Result(
            targetKg = targetKgSafe,
            points = p,
            below = loadAt(belowIndex),
            above = loadAt(aboveIndex),
            best = loadAt(bestIndex),
            isExact = exact,
            nextUp = loadAt(bestIndex + 1),
            nextDown = if (bestIndex > 0) loadAt(bestIndex - 1) else null
        )
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
