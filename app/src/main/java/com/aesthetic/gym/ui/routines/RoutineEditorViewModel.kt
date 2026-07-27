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
import com.aesthetic.gym.data.repo.EditedDay
import com.aesthetic.gym.data.repo.EditedItem
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.domain.model.RoutineSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Un ejercicio mientras se edita.
 *
 * [uid] es la identidad ESTABLE en la interfaz: los campos numéricos recuerdan su texto por uid,
 * así que al quitar un ejercicio los de abajo no heredan los números del que estaba en su sitio.
 * [id] es la fila real en la base de datos (0 = todavía no existe).
 */
data class DraftItem(
    val uid: Long,
    val id: Long = 0L,
    val exerciseId: String,
    val name: String,
    val muscle: MuscleGroup,
    val sets: Int = 3,
    val repsMin: Int = 8,
    val repsMax: Int = 12,
    val weightKg: Double? = null
)

data class DraftDay(
    val uid: Long,
    val id: Long = 0L,
    val name: String,
    val items: List<DraftItem>
)

/**
 * Editor de rutinas, usado para las dos cosas: crear una nueva ([routineId] nulo) y editar una
 * guardada. Al editar se cargan los días y ejercicios existentes conservando sus ids, de forma
 * que guardar actualiza las filas en lugar de borrarlas y recrearlas (ver `applyRoutineEdits`).
 */
class RoutineEditorViewModel(
    private val repo: GymRepository,
    private val routineId: Long?
) : ViewModel() {

    val isEditing: Boolean = routineId != null

    /** Solo al editar: hasta que no está cargada la rutina no se puede mostrar el formulario. */
    var loading by mutableStateOf(routineId != null)
        private set

    /** La rutina que se iba a editar ya no existe (la borraron en otra pantalla). */
    var missing by mutableStateOf(false)
        private set

    /** Guardado en curso: evita que una segunda pulsación duplique el trabajo y la navegación. */
    var saving by mutableStateOf(false)
        private set

    /** El último intento de guardar falló: se avisa y el borrador sigue en pantalla. */
    var saveFailed by mutableStateOf(false)
        private set

    var name by mutableStateOf("")
        private set

    var days by mutableStateOf(listOf(DraftDay(uid = 1L, name = "Día 1", items = emptyList())))
        private set

    private var nextUid = 2L

    /** Se está editando la rutina activa: cambiar sus días mueve el objetivo semanal. */
    private var isActiveRoutine = false
    private var loadedDayCount = 0

    /**
     * El objetivo de la racha semanal sale del número de días de la rutina activa
     * ([weeklyTargetFor]), así que añadir o quitar días lo recalcula. Se avisa antes de guardar
     * en vez de que la racha de Inicio cambie sin explicación.
     */
    val weeklyTargetChanges: Boolean
        get() = isEditing && isActiveRoutine && days.count { it.items.isNotEmpty() } != loadedDayCount

    val exercises: StateFlow<List<ExerciseEntity>> =
        repo.exercisesHot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val canSave: Boolean get() = !loading && !saving && days.any { it.items.isNotEmpty() }

    init {
        if (routineId != null) {
            viewModelScope.launch {
                val routine = repo.routineWithDays(routineId)
                if (routine == null) {
                    missing = true
                } else {
                    name = routine.routine.name
                    isActiveRoutine = routine.routine.isActive
                    loadedDayCount = routine.days.count { it.items.isNotEmpty() }
                    days = routine.sortedDays.map { day ->
                        DraftDay(
                            uid = nextUid++,
                            id = day.day.id,
                            name = day.day.name,
                            items = day.sortedItems.map { row ->
                                DraftItem(
                                    uid = nextUid++,
                                    id = row.item.id,
                                    exerciseId = row.item.exerciseId,
                                    name = row.exercise?.name ?: row.item.rawText,
                                    muscle = row.exercise?.primaryMuscle ?: MuscleGroup.FULL_BODY,
                                    sets = row.item.targetSets,
                                    repsMin = row.item.repsMin,
                                    repsMax = row.item.repsMax,
                                    weightKg = row.item.targetWeightKg
                                )
                            }
                        )
                    }.ifEmpty { listOf(DraftDay(uid = nextUid++, name = "Día 1", items = emptyList())) }
                }
                loading = false
            }
        }
    }

    fun updateName(value: String) { name = value }

    fun addDay() {
        days = days + DraftDay(uid = nextUid++, name = "Día ${days.size + 1}", items = emptyList())
    }

    fun renameDay(index: Int, value: String) {
        days = days.mapIndexed { i, d -> if (i == index) d.copy(name = value) else d }
    }

    fun removeDay(index: Int) {
        days = days.filterIndexed { i, _ -> i != index }
    }

    fun addItem(dayIndex: Int, exercise: ExerciseEntity) {
        val item = DraftItem(
            uid = nextUid++,
            exerciseId = exercise.id,
            name = exercise.name,
            muscle = exercise.primaryMuscle
        )
        days = days.mapIndexed { i, d -> if (i == dayIndex) d.copy(items = d.items + item) else d }
    }

    /** Cambia el ejercicio de una fila conservando sus series y repeticiones. */
    fun replaceItem(dayIndex: Int, itemIndex: Int, exercise: ExerciseEntity) {
        updateItem(dayIndex, itemIndex) {
            it.copy(
                exerciseId = exercise.id,
                name = exercise.name,
                muscle = exercise.primaryMuscle
            )
        }
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

    /** Sube o baja un ejercicio dentro de su día ([delta] = -1 o +1). */
    fun moveItem(dayIndex: Int, itemIndex: Int, delta: Int) {
        val target = itemIndex + delta
        days = days.mapIndexed { i, d ->
            if (i != dayIndex || itemIndex !in d.items.indices || target !in d.items.indices) d
            else d.copy(items = d.items.toMutableList().apply { add(target, removeAt(itemIndex)) })
        }
    }

    /** Un día quedará fuera de la rutina al guardar si se ha quedado sin ejercicios. */
    fun dayWillBeDropped(index: Int): Boolean =
        days.getOrNull(index)?.items?.isEmpty() == true && days.any { it.items.isNotEmpty() }

    /** Ejercicios repetidos dentro del mismo día: el entrenamiento los trataría como uno solo. */
    fun duplicatesIn(index: Int): Boolean {
        val items = days.getOrNull(index)?.items ?: return false
        return items.map { it.exerciseId }.distinct().size != items.size
    }

    /** Creates a brand-new custom exercise from typed text (muscle guessed) and returns it. */
    fun createCustomExercise(rawName: String, onCreated: (ExerciseEntity) -> Unit) {
        viewModelScope.launch {
            repo.createCustomExercise(rawName)?.let(onCreated)
        }
    }

    /**
     * Guarda: crea la rutina nueva o aplica los cambios sobre la existente. Si algo falla, el
     * borrador se queda intacto en pantalla para reintentar en lugar de perder el trabajo.
     */
    fun save(onDone: (Long) -> Unit) {
        if (!canSave) return
        saving = true
        saveFailed = false
        viewModelScope.launch {
            val saved = runCatching { doSave() }
            saving = false
            saved.fold(
                onSuccess = { onDone(it) },
                onFailure = { saveFailed = true }
            )
        }
    }

    /**
     * Si el usuario deja las repeticiones al revés (mín 12, máx 8), el rango se ordena en vez de
     * guardarse invertido: el entrenamiento siembra las series con el máximo y saldrían números
     * absurdos. Es lo mismo que hace el importador de PDF.
     */
    private fun DraftItem.normalized(): DraftItem =
        if (repsMin <= repsMax) this else copy(repsMin = repsMax, repsMax = repsMin)

    /** Devuelve el id de la rutina guardada (la existente al editar, o la recién creada). */
    private suspend fun doSave(): Long {
        if (routineId != null) {
            repo.applyRoutineEdits(
                routineId = routineId,
                name = name,
                days = days.map { day ->
                    EditedDay(
                        id = day.id,
                        name = day.name,
                        items = day.items.map { it.normalized() }.map { item ->
                            EditedItem(
                                id = item.id,
                                exerciseId = item.exerciseId,
                                name = item.name,
                                sets = item.sets,
                                repsMin = item.repsMin,
                                repsMax = item.repsMax,
                                weightKg = item.weightKg
                            )
                        }
                    )
                }
            )
            return routineId
        }

        val newId = repo.insertRoutine(
            RoutineEntity(
                name = name.trim().ifBlank { "Mi rutina" },
                source = RoutineSource.MANUAL,
                createdAt = repo.now()
            )
        )
        days.filter { it.items.isNotEmpty() }.forEachIndexed { dayIndex, day ->
            val dayId = repo.insertDay(
                RoutineDayEntity(
                    routineId = newId,
                    name = day.name.trim().ifBlank { "Día ${dayIndex + 1}" },
                    orderIndex = dayIndex
                )
            )
            day.items.map { it.normalized() }.forEachIndexed { itemIndex, item ->
                repo.insertItem(
                    RoutineItemEntity(
                        dayId = dayId,
                        exerciseId = item.exerciseId,
                        orderIndex = itemIndex,
                        targetSets = item.sets,
                        repsMin = item.repsMin,
                        repsMax = item.repsMax,
                        targetWeightKg = item.weightKg,
                        rawText = item.name
                    )
                )
            }
        }
        return newId
    }

    companion object {
        fun factory(repo: GymRepository, routineId: Long? = null) = viewModelFactory {
            initializer { RoutineEditorViewModel(repo, routineId) }
        }
    }
}
