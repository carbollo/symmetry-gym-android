package com.aesthetic.gym.domain.rank

import com.aesthetic.gym.data.db.SetMuscleRow
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.domain.model.Rank
import com.aesthetic.gym.domain.model.Sex
import com.aesthetic.gym.util.epley1RM

data class MuscleRank(
    val muscle: MuscleGroup,
    val score: Int,
    val rank: Rank,
    val bestE1rmKg: Double,
    val hasData: Boolean
)

data class RankSummary(
    val perMuscle: List<MuscleRank>,
    val overallScore: Int,
    val overallRank: Rank
) {
    private val byMuscle = perMuscle.associateBy { it.muscle }
    fun of(muscle: MuscleGroup): MuscleRank =
        byMuscle[muscle] ?: MuscleRank(muscle, 0, Rank.PRINCIPIANTE, 0.0, false)
}

/**
 * Computes a 0..100 strength score and tier per muscle group, based on the best estimated 1RM
 * relative to bodyweight, compared against approximate strength standards.
 */
object RankCalculator {

    // e1RM / bodyweight thresholds for [NOVATO, INTERMEDIO, AVANZADO, ELITE, MAESTRO] (male baseline).
    private val standards: Map<MuscleGroup, DoubleArray> = mapOf(
        MuscleGroup.CHEST to doubleArrayOf(0.50, 0.75, 1.05, 1.40, 1.80),
        MuscleGroup.BACK to doubleArrayOf(0.50, 0.75, 1.05, 1.40, 1.80),
        MuscleGroup.SHOULDERS to doubleArrayOf(0.30, 0.45, 0.60, 0.80, 1.05),
        MuscleGroup.BICEPS to doubleArrayOf(0.20, 0.30, 0.40, 0.55, 0.70),
        MuscleGroup.TRICEPS to doubleArrayOf(0.25, 0.40, 0.55, 0.75, 0.95),
        MuscleGroup.FOREARMS to doubleArrayOf(0.15, 0.25, 0.35, 0.50, 0.65),
        MuscleGroup.TRAPS to doubleArrayOf(0.40, 0.65, 0.95, 1.30, 1.70),
        MuscleGroup.ABS to doubleArrayOf(0.15, 0.30, 0.45, 0.65, 0.90),
        MuscleGroup.QUADS to doubleArrayOf(0.80, 1.20, 1.60, 2.10, 2.60),
        MuscleGroup.HAMSTRINGS to doubleArrayOf(0.90, 1.30, 1.80, 2.30, 2.80),
        MuscleGroup.GLUTES to doubleArrayOf(1.00, 1.50, 2.00, 2.60, 3.20),
        MuscleGroup.CALVES to doubleArrayOf(0.80, 1.30, 1.80, 2.40, 3.00)
    )

    private val upperBody = setOf(
        MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.BICEPS,
        MuscleGroup.TRICEPS, MuscleGroup.FOREARMS, MuscleGroup.TRAPS, MuscleGroup.ABS
    )

    private fun sexFactor(muscle: MuscleGroup, sex: Sex): Double = when (sex) {
        Sex.MALE -> 1.0
        Sex.FEMALE -> if (muscle in upperBody) 0.72 else 0.80
        Sex.OTHER -> 0.85
    }

    /** Maps a ratio against thresholds to a 0..100 score (each tier spans ~16.7 points). */
    private fun ratioToScore(ratio: Double, thresholds: DoubleArray): Int {
        val band = 100.0 / 6.0
        // Below first threshold -> tier 0 (PRINCIPIANTE)
        if (ratio < thresholds[0]) {
            val frac = (ratio / thresholds[0]).coerceIn(0.0, 1.0)
            return (frac * band).toInt()
        }
        for (i in 0 until thresholds.size - 1) {
            if (ratio < thresholds[i + 1]) {
                val span = thresholds[i + 1] - thresholds[i]
                val frac = if (span <= 0) 0.0 else (ratio - thresholds[i]) / span
                return (((i + 1) + frac.coerceIn(0.0, 1.0)) * band).toInt().coerceAtMost(100)
            }
        }
        // Above the last threshold -> top tier, scale up to 100.
        val last = thresholds.last()
        val frac = ((ratio - last) / (last * 0.4)).coerceIn(0.0, 1.0)
        return (5 * band + frac * band).toInt().coerceAtMost(100)
    }

    fun compute(rows: List<SetMuscleRow>, bodyweightKg: Double, sex: Sex): RankSummary {
        val bw = bodyweightKg.coerceAtLeast(1.0)

        val bestLoaded = HashMap<MuscleGroup, Double>()
        val bestReps = HashMap<MuscleGroup, Int>()
        for (row in rows) {
            val muscle = row.primaryMuscle
            if (row.weightKg > 0.0) {
                val e1rm = epley1RM(row.weightKg, row.reps)
                if (e1rm > (bestLoaded[muscle] ?: 0.0)) bestLoaded[muscle] = e1rm
            } else {
                if (row.reps > (bestReps[muscle] ?: 0)) bestReps[muscle] = row.reps
            }
        }

        val perMuscle = MuscleGroup.ranked.map { muscle ->
            val loaded = bestLoaded[muscle] ?: 0.0
            val reps = bestReps[muscle] ?: 0
            val hasData = loaded > 0.0 || reps > 0
            val score = when {
                loaded > 0.0 -> {
                    val thresholds = standards[muscle]!!
                    val factor = sexFactor(muscle, sex)
                    val adjusted = DoubleArray(thresholds.size) { thresholds[it] * factor }
                    ratioToScore(loaded / bw, adjusted)
                }
                reps > 0 -> minOf(45, (reps * 1.5).toInt()) // bodyweight-only progression
                else -> 0
            }
            MuscleRank(muscle, score, Rank.fromScore(score), loaded, hasData)
        }

        val withData = perMuscle.filter { it.hasData }
        val overall = if (withData.isEmpty()) 0 else withData.sumOf { it.score } / withData.size
        return RankSummary(perMuscle, overall, Rank.fromScore(overall))
    }
}
