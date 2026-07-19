package com.aesthetic.gym.ui.routines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.data.db.RoutineDayEntity
import com.aesthetic.gym.data.db.RoutineEntity
import com.aesthetic.gym.data.db.RoutineItemEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.domain.model.RoutineSource
import com.aesthetic.gym.pdf.MuscleGuesser
import com.aesthetic.gym.util.normalizeText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DraftItem(
    val exerciseId: String,
    val name: String,
    val muscle: MuscleGroup,
    val sets: Int = 3,
    val repsMin: Int = 8,
    val repsMax: Int = 12,
    val weightKg: Double? = null
)

data class DraftDay(val localId: Long, val name: String, val items: List<DraftItem>)

class CreateRoutineViewModel(private val repo: GymRepository) : ViewModel() {

    var name by mutableStateOf("")
        private set

    var days by mutableStateOf(listOf(DraftDay(1, "Día 1", emptyList())))
        private set

    private var nextDayId = 2L

    val exercises: StateFlow<List<ExerciseEntity>> =
        repo.exercisesHot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val canSave: Boolean get() = days.any { it.items.isNotEmpty() }

    fun updateName(value: String) { name = value }

    fun addDay() {
        days = days + DraftDay(nextDayId++, "Día ${days.size + 1}", emptyList())
    }

    fun renameDay(index: Int, value: String) {
        days = days.mapIndexed { i, d -> if (i == index) d.copy(name = value) else d }
    }

    fun removeDay(index: Int) {
        days = days.filterIndexed { i, _ -> i != index }
    }

    fun addItem(dayIndex: Int, exercise: ExerciseEntity) {
        val item = DraftItem(exercise.id, exercise.name, exercise.primaryMuscle)
        days = days.mapIndexed { i, d -> if (i == dayIndex) d.copy(items = d.items + item) else d }
    }

    fun updateItem(dayIndex: Int, itemIndex: Int, transform: (DraftItem) -> DraftItem) {
        days = days.mapIndexed { i, d ->
            if (i != dayIndex) d
            else d.copy(items = d.items.mapIndexed { j, it -> if (j == itemIndex) transform(it) else it })
        }
    }

    fun removeItem(dayIndex: Int, itemIndex: Int) {
        days = days.mapIndexed { i, d ->
            if (i != dayIndex) d else d.copy(items = d.items.filterIndexed { j, _ -> j != itemIndex })
        }
    }

    /** Creates a brand-new custom exercise from typed text (muscle guessed) and returns it. */
    fun createCustomExercise(rawName: String, onCreated: (ExerciseEntity) -> Unit) {
        val trimmed = rawName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val base = "custom-" + normalizeText(trimmed).replace(' ', '-').take(40).trim('-')
            val existing = repo.getAllExercises().map { it.id }.toSet()
            var id = base.ifBlank { "custom-${System.nanoTime()}" }
            var n = 1
            while (id in existing) { id = "$base-$n"; n++ }
            val exercise = ExerciseEntity(
                id = id,
                name = trimmed,
                primaryMuscle = MuscleGuesser.guessMuscle(trimmed),
                equipment = MuscleGuesser.guessEquipment(trimmed),
                isCustom = true
            )
            repo.upsertExercise(exercise)
            onCreated(exercise)
        }
    }

    fun save(onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val routineId = repo.insertRoutine(
                RoutineEntity(
                    name = name.ifBlank { "Mi rutina" },
                    source = RoutineSource.MANUAL,
                    createdAt = repo.now()
                )
            )
            days.filter { it.items.isNotEmpty() }.forEachIndexed { dayIndex, day ->
                val dayId = repo.insertDay(
                    RoutineDayEntity(routineId = routineId, name = day.name, orderIndex = dayIndex)
                )
                day.items.forEachIndexed { itemIndex, item ->
                    repo.insertItem(
                        RoutineItemEntity(
                            dayId = dayId,
                            exerciseId = item.exerciseId,
                            orderIndex = itemIndex,
                            targetSets = item.sets,
                            repsMin = item.repsMin,
                            repsMax = item.repsMax,
                            targetWeightKg = item.weightKg
                        )
                    )
                }
            }
            onDone(routineId)
        }
    }

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory { initializer { CreateRoutineViewModel(repo) } }
    }
}
