package com.aesthetic.gym.util

import com.aesthetic.gym.data.db.SetLogEntity
import com.aesthetic.gym.data.db.WorkoutSessionEntity
import com.aesthetic.gym.domain.model.MeasureType
import kotlin.math.roundToInt

/** Volume moved and calories burned estimates for a workout session. */
object WorkoutMath {

    private const val STRENGTH_MET = 5.0 // moderate weight training

    /** Total weight moved (kg) across completed rep-based working sets: Σ peso × reps. */
    fun volumeKg(sets: List<SetLogEntity>): Double =
        sets.filter { it.completed && !it.isWarmup && it.measure == MeasureType.REPS }
            .sumOf { it.weightKg * it.reps }

    /** Session duration in minutes, real if available, otherwise estimated from set count. */
    fun durationMinutes(session: WorkoutSessionEntity, completedSets: Int): Double {
        val real = session.finishedAt?.let { (it - session.startedAt) / 60000.0 } ?: 0.0
        val base = if (real in 5.0..240.0) real else completedSets * 3.0
        return base.coerceIn(10.0, 180.0)
    }

    /** Estimated calories burned using the MET formula: kcal/min = MET × 3.5 × kg / 200. */
    fun caloriesKcal(
        session: WorkoutSessionEntity,
        sets: List<SetLogEntity>,
        bodyweightKg: Double
    ): Int {
        val completed = sets.count { it.completed }
        if (completed == 0) return 0
        val minutes = durationMinutes(session, completed)
        val kcalPerMin = STRENGTH_MET * 3.5 * bodyweightKg.coerceAtLeast(30.0) / 200.0
        return (kcalPerMin * minutes).roundToInt()
    }
}
