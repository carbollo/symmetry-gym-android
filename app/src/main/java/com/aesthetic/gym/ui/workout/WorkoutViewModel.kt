package com.aesthetic.gym.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.RoutineItemWithExercise
import com.aesthetic.gym.data.db.SessionWithSets
import com.aesthetic.gym.data.db.SetLogEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.overload.OverloadSuggestion
import com.aesthetic.gym.domain.overload.ProgressiveOverload
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(
    private val repo: GymRepository,
    private val sessionId: Long
) : ViewModel() {

    val session: StateFlow<SessionWithSets?> =
        repo.sessionWithSetsFlow(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var plannedItems by mutableStateOf<List<RoutineItemWithExercise>>(emptyList())
        private set

    var suggestions by mutableStateOf<Map<String, OverloadSuggestion>>(emptyMap())
        private set

    init {
        viewModelScope.launch {
            val s = repo.sessionById(sessionId) ?: return@launch
            val routine = s.routineId?.let { repo.routineWithDays(it) }
            val day = routine?.days?.firstOrNull { it.day.id == s.dayId }
            val items = day?.sortedItems ?: emptyList()
            plannedItems = items

            val map = HashMap<String, OverloadSuggestion>()
            for (it in items) {
                val last = repo.lastSetsForExercise(it.item.exerciseId, sessionId)
                map[it.item.exerciseId] = ProgressiveOverload.suggest(it.item, it.exercise, last)
            }
            suggestions = map

            // First time this session opens: pre-load every planned set (pending confirmation).
            if (repo.setCountForSession(sessionId) == 0) {
                autoPopulate(items, map)
            }
        }
    }

    private suspend fun autoPopulate(
        items: List<RoutineItemWithExercise>,
        suggestionsMap: Map<String, OverloadSuggestion>
    ) {
        for (item in items) {
            val sugg = suggestionsMap[item.item.exerciseId]
            val weight = sugg?.weightKg ?: item.item.targetWeightKg ?: 20.0
            val reps = when {
                item.item.amrap -> sugg?.repsHigh ?: 10
                item.item.repsMax > 0 -> item.item.repsMax
                else -> 10
            }
            val total = item.item.targetSets.coerceIn(1, 20)
            for (n in 1..total) {
                repo.addSet(
                    SetLogEntity(
                        sessionId = sessionId,
                        exerciseId = item.item.exerciseId,
                        routineItemId = item.item.id,
                        setNumber = n,
                        reps = reps,
                        weightKg = weight,
                        completed = false
                    )
                )
            }
        }
    }

    fun setsFor(exerciseId: String): List<SetLogEntity> =
        session.value?.sets?.filter { it.exerciseId == exerciseId }?.sortedBy { it.setNumber } ?: emptyList()

    fun addSet(item: RoutineItemWithExercise) {
        viewModelScope.launch {
            val current = setsFor(item.item.exerciseId)
            val setNumber = (current.maxOfOrNull { it.setNumber } ?: 0) + 1
            val sugg = suggestions[item.item.exerciseId]
            val weight = current.maxByOrNull { it.setNumber }?.weightKg
                ?: sugg?.weightKg ?: item.item.targetWeightKg ?: 20.0
            val reps = when {
                item.item.amrap -> sugg?.repsHigh ?: 10
                item.item.repsMax > 0 -> item.item.repsMax
                else -> 10
            }
            repo.addSet(
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = item.item.exerciseId,
                    routineItemId = item.item.id,
                    setNumber = setNumber,
                    reps = reps,
                    weightKg = weight,
                    completed = false
                )
            )
        }
    }

    fun setWeight(set: SetLogEntity, kg: Double) {
        viewModelScope.launch {
            repo.updateSet(set.copy(weightKg = kg.coerceIn(0.0, 2000.0)))
        }
    }

    fun setReps(set: SetLogEntity, reps: Int) {
        viewModelScope.launch {
            repo.updateSet(set.copy(reps = reps.coerceIn(0, 1000)))
        }
    }

    fun toggleCompleted(set: SetLogEntity) {
        viewModelScope.launch { repo.updateSet(set.copy(completed = !set.completed)) }
    }

    fun deleteSet(set: SetLogEntity) {
        viewModelScope.launch { repo.deleteSet(set.id) }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.finishSession(sessionId)
            onDone()
        }
    }

    companion object {
        fun factory(repo: GymRepository, sessionId: Long) =
            viewModelFactory { initializer { WorkoutViewModel(repo, sessionId) } }
    }
}
