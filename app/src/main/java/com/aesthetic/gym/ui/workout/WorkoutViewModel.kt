package com.aesthetic.gym.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.aesthetic.gym.domain.model.MeasureType
import com.aesthetic.gym.domain.overload.OverloadSuggestion
import com.aesthetic.gym.domain.overload.ProgressiveOverload
import com.aesthetic.gym.util.RestBell
import com.aesthetic.gym.util.WorkoutMath
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class WorkoutSummary(
    val durationMin: Int,
    val kcal: Int,
    val volumeKg: Double,
    val sets: Int
)

class WorkoutViewModel(
    private val repo: GymRepository,
    private val bell: RestBell,
    private val sessionId: Long
) : ViewModel() {

    /** Rest countdown (seconds left, 0 = not resting). */
    var restLeft by mutableIntStateOf(0)
        private set
    var restTotal by mutableIntStateOf(0)
        private set
    private var restJob: Job? = null

    val session: StateFlow<SessionWithSets?> =
        repo.sessionWithSetsFlow(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var plannedItems by mutableStateOf<List<RoutineItemWithExercise>>(emptyList())
        private set

    var suggestions by mutableStateOf<Map<String, OverloadSuggestion>>(emptyMap())
        private set

    var summary by mutableStateOf<WorkoutSummary?>(null)
        private set

    /** All-time best set per exercise, shown as "RÉCORD" on the workout card. */
    var records by mutableStateOf<Map<String, SetLogEntity>>(emptyMap())
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

            val recs = HashMap<String, SetLogEntity>()
            for (it in items) {
                repo.bestSetForExercise(it.item.exerciseId)?.let { best -> recs[it.item.exerciseId] = best }
            }
            records = recs

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
            val ex = item.exercise
            val measure = ex?.measure ?: MeasureType.REPS

            // Restore each set from the corresponding set of the previous session (per set number).
            val previous = repo.previousSetsForExercise(item.item.exerciseId, sessionId)
            val prevByNumber = previous.associateBy { it.setNumber }

            val fallbackWeight = ex?.lastWeightKg ?: sugg?.weightKg ?: item.item.targetWeightKg ?: 20.0
            val fallbackReps = ex?.lastReps ?: if (measure == MeasureType.SECONDS) 30 else when {
                item.item.amrap -> sugg?.repsHigh ?: 10
                item.item.repsMax > 0 -> item.item.repsMax
                else -> 10
            }

            val total = item.item.targetSets.coerceIn(1, 20)
            for (n in 1..total) {
                val ref = prevByNumber[n] ?: previous.lastOrNull()
                repo.addSet(
                    SetLogEntity(
                        sessionId = sessionId,
                        exerciseId = item.item.exerciseId,
                        routineItemId = item.item.id,
                        setNumber = n,
                        reps = ref?.reps ?: fallbackReps,
                        weightKg = ref?.weightKg ?: fallbackWeight,
                        completed = false,
                        measure = measure
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
            val measure = current.firstOrNull()?.measure ?: item.exercise?.measure ?: MeasureType.REPS
            val weight = current.maxByOrNull { it.setNumber }?.weightKg
                ?: sugg?.weightKg ?: item.item.targetWeightKg ?: 20.0
            val reps = current.maxByOrNull { it.setNumber }?.reps
                ?: if (measure == MeasureType.SECONDS) 30 else when {
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
                    completed = false,
                    measure = measure
                )
            )
        }
    }

    fun setWeight(set: SetLogEntity, kg: Double) {
        viewModelScope.launch {
            val v = kg.coerceIn(0.0, 2000.0)
            repo.updateSet(set.copy(weightKg = v))
            repo.updateExerciseLastWeight(set.exerciseId, v)
        }
    }

    fun setReps(set: SetLogEntity, reps: Int) {
        viewModelScope.launch {
            val v = reps.coerceIn(0, 1000)
            repo.updateSet(set.copy(reps = v))
            repo.updateExerciseLastReps(set.exerciseId, v)
        }
    }

    fun toggleCompleted(set: SetLogEntity) {
        viewModelScope.launch {
            val nowCompleted = !set.completed
            repo.updateSet(set.copy(completed = nowCompleted))
            if (nowCompleted) {
                repo.updateExerciseLastWeight(set.exerciseId, set.weightKg)
                repo.updateExerciseLastReps(set.exerciseId, set.reps)
                startRest(restSecondsFor(set.exerciseId))
            }
        }
    }

    /** Configured rest for an exercise (defaults to 90s when the routine doesn't set one). */
    fun restSecondsFor(exerciseId: String): Int =
        plannedItems.firstOrNull { it.item.exerciseId == exerciseId }?.item?.restSeconds
            ?: DEFAULT_REST_SECONDS

    /** Persists a new rest time for this routine exercise. */
    fun setRest(item: RoutineItemWithExercise, seconds: Int) {
        val value = seconds.coerceIn(0, 600)
        viewModelScope.launch {
            repo.updateItemRest(item.item.id, value)
            plannedItems = plannedItems.map {
                if (it.item.id == item.item.id) it.copy(item = it.item.copy(restSeconds = value)) else it
            }
        }
    }

    fun startRest(seconds: Int) {
        if (seconds <= 0) return
        restJob?.cancel()
        restTotal = seconds
        restJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                restLeft = left
                delay(1000)
                left--
            }
            restLeft = 0
            bell.ring()
        }
    }

    fun skipRest() {
        restJob?.cancel()
        restLeft = 0
    }

    fun deleteSet(set: SetLogEntity) {
        viewModelScope.launch { repo.deleteSet(set.id) }
    }

    /** Switches an exercise between rep-based and time-based, updating its existing sets. */
    fun setMeasure(exerciseId: String, sets: List<SetLogEntity>, measure: MeasureType) {
        viewModelScope.launch {
            repo.updateExerciseMeasure(exerciseId, measure)
            sets.forEach { repo.updateSet(it.copy(measure = measure)) }
        }
    }

    /** Finishes the session and produces a summary (duration, calories, volume) to show the user. */
    fun finishWorkout() {
        viewModelScope.launch {
            val current = session.value
            val sets = current?.sets ?: emptyList()
            val bw = repo.getProfile()?.bodyweightKg ?: 75.0
            val now = repo.now()
            repo.finishSession(sessionId)
            val finished = (current?.session ?: repo.sessionById(sessionId))?.copy(finishedAt = now)
            val completed = sets.count { it.completed }
            summary = WorkoutSummary(
                durationMin = finished?.let { WorkoutMath.durationMinutes(it, completed).roundToInt() } ?: 0,
                kcal = finished?.let { WorkoutMath.caloriesKcal(it, sets, bw) } ?: 0,
                volumeKg = WorkoutMath.volumeKg(sets),
                sets = completed
            )
        }
    }

    companion object {
        const val DEFAULT_REST_SECONDS = 90

        fun factory(repo: GymRepository, bell: RestBell, sessionId: Long) =
            viewModelFactory { initializer { WorkoutViewModel(repo, bell, sessionId) } }
    }
}
