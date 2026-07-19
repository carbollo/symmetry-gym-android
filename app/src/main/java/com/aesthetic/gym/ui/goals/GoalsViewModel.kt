package com.aesthetic.gym.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.GoalEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.GoalType
import com.aesthetic.gym.domain.model.Sex
import com.aesthetic.gym.domain.rank.RankCalculator
import com.aesthetic.gym.util.epley1RM
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalProgress(
    val goal: GoalEntity,
    val current: Double,
    val fraction: Float,
    val done: Boolean
)

class GoalsViewModel(private val repo: GymRepository) : ViewModel() {

    val goals: StateFlow<List<GoalProgress>> = combine(
        repo.goalsHot,
        repo.profileHot,
        repo.finishedCountFlow(),
        repo.setMuscleRowsHot
    ) { goals, profile, workouts, rows ->
        val bestByExercise = rows.filter { it.weightKg > 0 }
            .groupBy { it.exerciseId }
            .mapValues { (_, l) -> l.maxOf { epley1RM(it.weightKg, it.reps) } }
        val summary = RankCalculator.compute(rows, profile?.bodyweightKg ?: 75.0, profile?.sex ?: Sex.MALE)

        goals.map { g ->
            val current = when (g.type) {
                GoalType.BODYWEIGHT -> profile?.bodyweightKg ?: 0.0
                GoalType.WORKOUTS -> workouts.toDouble()
                GoalType.STRENGTH_SCORE -> summary.overallScore.toDouble()
                GoalType.LIFT_1RM -> g.exerciseId?.let { bestByExercise[it] } ?: 0.0
                GoalType.CUSTOM -> g.manualCurrent
            }
            val fraction = if (g.targetValue > 0) (current / g.targetValue).coerceIn(0.0, 1.0).toFloat() else 0f
            GoalProgress(g, current, fraction, g.targetValue > 0 && current >= g.targetValue)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addGoal(title: String, type: GoalType, target: Double, exerciseId: String? = null) {
        viewModelScope.launch {
            repo.insertGoal(
                GoalEntity(
                    title = title.ifBlank { type.displayName },
                    type = type,
                    targetValue = target,
                    exerciseId = exerciseId,
                    createdAt = repo.now()
                )
            )
        }
    }

    fun updateManual(goal: GoalEntity, value: Double) {
        viewModelScope.launch { repo.updateGoal(goal.copy(manualCurrent = value.coerceAtLeast(0.0))) }
    }

    fun delete(id: Long) = viewModelScope.launch { repo.deleteGoal(id) }

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory { initializer { GoalsViewModel(repo) } }
    }
}
