package com.aesthetic.gym.util

import kotlin.math.abs

/**
 * Works out which plates to load on each side of the bar for a target weight.
 * Everything is per side: a 100 kg lift on a 20 kg bar means 40 kg of plates per side.
 */
object PlateCalculator {

    /** Standard gym plates, heaviest first (kg). */
    val DEFAULT_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    val BAR_OPTIONS = listOf(20.0, 15.0, 10.0, 7.0, 0.0)

    data class Result(
        /** Plates to put on each side, heaviest first. */
        val perSide: List<Double>,
        /** Weight actually achievable with those plates (bar included). */
        val achievedKg: Double,
        /** Target the user asked for. */
        val targetKg: Double,
        /** True when the target can't be matched exactly with the available plates. */
        val approximate: Boolean,
        /** Set when the target is lighter than the bar itself. */
        val belowBar: Boolean
    ) {
        val leftoverKg: Double get() = targetKg - achievedKg
    }

    fun compute(
        targetKg: Double,
        barKg: Double = 20.0,
        plates: List<Double> = DEFAULT_PLATES
    ): Result {
        if (targetKg < barKg) {
            return Result(emptyList(), barKg, targetKg, approximate = true, belowBar = true)
        }
        var remainingPerSide = (targetKg - barKg) / 2.0
        val used = mutableListOf<Double>()
        for (plate in plates.sortedDescending()) {
            while (remainingPerSide >= plate - EPS) {
                used += plate
                remainingPerSide -= plate
            }
        }
        val achieved = barKg + used.sum() * 2.0
        return Result(
            perSide = used,
            achievedKg = achieved,
            targetKg = targetKg,
            approximate = abs(achieved - targetKg) > EPS,
            belowBar = false
        )
    }

    /** Groups the per-side plates as "2 × 20 kg" pairs, keeping the heaviest first. */
    fun grouped(perSide: List<Double>): List<Pair<Double, Int>> =
        perSide.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }

    private const val EPS = 0.001
}
