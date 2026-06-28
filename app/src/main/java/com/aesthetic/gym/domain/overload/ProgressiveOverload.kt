package com.aesthetic.gym.domain.overload

import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.data.db.RoutineItemEntity
import com.aesthetic.gym.data.db.SetLogEntity
import com.aesthetic.gym.domain.model.MuscleGroup
import kotlin.math.roundToInt

data class OverloadSuggestion(
    val weightKg: Double?,
    val repsLow: Int,
    val repsHigh: Int,
    val note: String,
    val reachedTop: Boolean
)

/**
 * Automatic progressive overload using a double-progression model:
 * keep the weight until every working set reaches the top of the rep range, then add a load step.
 */
object ProgressiveOverload {

    private val heavyMuscles = setOf(
        MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.BACK
    )

    fun loadStepKg(exercise: ExerciseEntity?): Double {
        val compound = exercise?.isCompound == true
        val heavy = exercise != null && exercise.primaryMuscle in heavyMuscles
        return if (compound && heavy) 5.0 else 2.5
    }

    private fun roundToHalf(value: Double): Double = (value * 2).roundToInt() / 2.0

    fun suggest(
        item: RoutineItemEntity,
        exercise: ExerciseEntity?,
        lastSets: List<SetLogEntity>
    ): OverloadSuggestion {
        val repsLow = item.repsMin
        val repsHigh = item.repsMax.coerceAtLeast(item.repsMin)
        val step = loadStepKg(exercise)

        if (lastSets.isEmpty()) {
            val target = item.targetWeightKg
            val note = if (target != null)
                "Peso objetivo de la rutina"
            else
                "Sin historial: elige tu peso inicial"
            return OverloadSuggestion(target, repsLow, repsHigh, note, reachedTop = false)
        }

        val topWeight = lastSets.maxOf { it.weightKg }
        val setsAtTop = lastSets.filter { it.weightKg == topWeight }
        val minRepsAtTop = setsAtTop.minOf { it.reps }

        return if (minRepsAtTop >= repsHigh) {
            val next = roundToHalf(topWeight + step)
            OverloadSuggestion(
                weightKg = next,
                repsLow = repsLow,
                repsHigh = repsHigh,
                note = "¡Completaste el rango! Sube a ${trim(next)} kg (+${trim(step)})",
                reachedTop = true
            )
        } else {
            OverloadSuggestion(
                weightKg = topWeight,
                repsLow = repsLow,
                repsHigh = repsHigh,
                note = "Mantén ${trim(topWeight)} kg y busca llegar a $repsHigh reps",
                reachedTop = false
            )
        }
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
