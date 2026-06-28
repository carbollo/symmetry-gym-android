package com.aesthetic.gym.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.data.db.SetWithDate
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.util.epley1RM
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class ExerciseStats(
    val exercise: ExerciseEntity? = null,
    val best1RM: Double = 0.0,
    val bestSetWeight: Double = 0.0,
    val bestSetReps: Int = 0,
    val sessions: Int = 0,
    val totalSets: Int = 0,
    val totalVolume: Double = 0.0,
    val series: List<Float> = emptyList(),
    val lastDate: Long? = null,
    val hasData: Boolean = false
)

class ExerciseDetailViewModel(repo: GymRepository, exerciseId: String) : ViewModel() {

    val stats: StateFlow<ExerciseStats> = combine(
        flow { emit(repo.getExercise(exerciseId)) },
        repo.setsWithDateFlow(exerciseId)
    ) { exercise, rows ->
        build(exercise, rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseStats())

    private fun build(exercise: ExerciseEntity?, rows: List<SetWithDate>): ExerciseStats {
        if (rows.isEmpty()) return ExerciseStats(exercise = exercise)
        val best = rows.maxWith(compareBy({ it.weightKg }, { it.reps }))
        val byDate = rows.groupBy { it.date }.toSortedMap()
        return ExerciseStats(
            exercise = exercise,
            best1RM = rows.maxOf { epley1RM(it.weightKg, it.reps) },
            bestSetWeight = best.weightKg,
            bestSetReps = best.reps,
            sessions = byDate.size,
            totalSets = rows.size,
            totalVolume = rows.sumOf { it.weightKg * it.reps },
            series = byDate.values.map { list -> list.maxOf { epley1RM(it.weightKg, it.reps) }.toFloat() },
            lastDate = byDate.keys.maxOrNull(),
            hasData = true
        )
    }

    companion object {
        fun factory(repo: GymRepository, exerciseId: String) =
            viewModelFactory { initializer { ExerciseDetailViewModel(repo, exerciseId) } }
    }
}
