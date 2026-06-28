package com.aesthetic.gym.pdf

import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.data.db.RoutineDayEntity
import com.aesthetic.gym.data.db.RoutineEntity
import com.aesthetic.gym.data.db.RoutineItemEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.RoutineSource
import com.aesthetic.gym.util.normalizeText

data class ImportResult(
    val routineId: Long,
    val routineName: String,
    val dayCount: Int,
    val exerciseCount: Int,
    val matchedCount: Int,
    val createdExercises: List<String>
)

/** Turns parsed routine text into persisted entities, auto-resolving exercises. */
class RoutineImporter(private val repo: GymRepository) {

    suspend fun import(rawText: String, fallbackName: String, source: RoutineSource): ImportResult {
        val parsed = RoutineParser.parse(rawText, fallbackName)
        require(parsed.days.isNotEmpty() && parsed.exerciseCount > 0) {
            "No se detectaron ejercicios en el texto. Revisa el formato."
        }

        val existing = repo.getAllExercises()
        val existingIds = existing.map { it.id }.toMutableSet()
        val matcher = ExerciseMatcher.build(existing)

        var matched = 0
        val created = mutableListOf<String>()

        val routineId = repo.insertRoutine(
            RoutineEntity(name = parsed.name, source = source, createdAt = repo.now())
        )

        parsed.days.forEachIndexed { dayIndex, day ->
            val dayId = repo.insertDay(
                RoutineDayEntity(routineId = routineId, name = day.name, orderIndex = dayIndex)
            )
            day.items.forEachIndexed { itemIndex, item ->
                val preMatch = matcher.match(item.name)
                if (preMatch != null) matched++
                val exerciseId = preMatch ?: createCustomExercise(item.name, existingIds, created, matcher)
                repo.insertItem(
                    RoutineItemEntity(
                        dayId = dayId,
                        exerciseId = exerciseId,
                        orderIndex = itemIndex,
                        targetSets = item.sets,
                        repsMin = if (item.amrap) 0 else item.repsMin,
                        repsMax = if (item.amrap) 0 else item.repsMax,
                        targetWeightKg = item.weightKg,
                        restSeconds = item.restSeconds,
                        amrap = item.amrap,
                        rawText = item.rawText
                    )
                )
            }
        }

        return ImportResult(
            routineId = routineId,
            routineName = parsed.name,
            dayCount = parsed.days.size,
            exerciseCount = parsed.exerciseCount,
            matchedCount = matched,
            createdExercises = created
        )
    }

    /** Creates a custom exercise (used only when no catalog/DB match was found). */
    private suspend fun createCustomExercise(
        name: String,
        existingIds: MutableSet<String>,
        created: MutableList<String>,
        matcher: ExerciseMatcher
    ): String {
        // Best-effort muscle/equipment guess for an exercise we didn't recognise.
        var id = "custom-" + normalizeText(name).replace(' ', '-').take(40).trim('-')
        if (id == "custom-") id = "custom-${System.nanoTime()}"
        var unique = id
        var suffix = 1
        while (unique in existingIds) {
            unique = "$id-$suffix"; suffix++
        }
        val exercise = ExerciseEntity(
            id = unique,
            name = name,
            primaryMuscle = MuscleGuesser.guessMuscle(name),
            equipment = MuscleGuesser.guessEquipment(name),
            isCompound = false,
            instructions = "",
            isCustom = true
        )
        repo.upsertExercise(exercise)
        existingIds.add(unique)
        matcher.register(unique, name)
        created.add(name)
        return unique
    }
}
