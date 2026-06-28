package com.aesthetic.gym.data.db

import androidx.room.Embedded
import androidx.room.Relation
import com.aesthetic.gym.domain.model.MuscleGroup

/** A routine item joined with its catalog exercise. */
data class RoutineItemWithExercise(
    @Embedded val item: RoutineItemEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity?
)

/** A routine day with its ordered list of items. */
data class DayWithItems(
    @Embedded val day: RoutineDayEntity,
    @Relation(
        entity = RoutineItemEntity::class,
        parentColumn = "id",
        entityColumn = "dayId"
    )
    val items: List<RoutineItemWithExercise>
) {
    val sortedItems: List<RoutineItemWithExercise> get() = items.sortedBy { it.item.orderIndex }
}

/** A full routine with its days and items. */
data class RoutineWithDays(
    @Embedded val routine: RoutineEntity,
    @Relation(
        entity = RoutineDayEntity::class,
        parentColumn = "id",
        entityColumn = "routineId"
    )
    val days: List<DayWithItems>
) {
    val sortedDays: List<DayWithItems> get() = days.sortedBy { it.day.orderIndex }
}

/** A workout session with all its logged sets. */
data class SessionWithSets(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<SetLogEntity>
)

/** Projection used by the rank calculator: a completed set joined with its muscle group. */
data class SetMuscleRow(
    val exerciseId: String,
    val weightKg: Double,
    val reps: Int,
    val primaryMuscle: MuscleGroup,
    val isCompound: Boolean
)
