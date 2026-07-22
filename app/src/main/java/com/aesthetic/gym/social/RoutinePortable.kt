package com.aesthetic.gym.social

import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.data.db.RoutineDayEntity
import com.aesthetic.gym.data.db.RoutineEntity
import com.aesthetic.gym.data.db.RoutineItemEntity
import com.aesthetic.gym.data.db.RoutineWithDays
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.Equipment
import com.aesthetic.gym.domain.model.MeasureType
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.domain.model.RoutineSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * Formato portable de una rutina para compartir entre usuarios.
 *
 * Lleva EMBEBIDOS todos los ejercicios que referencia (con nombre, músculo, etc.), así que si quien
 * la importa no tiene ese ejercicio en su catálogo, se recrea como ejercicio personalizado en vez de
 * fallar. Al importar se generan SIEMPRE filas nuevas (PKs frescas): nunca se pisan rutinas ni
 * ejercicios existentes del que importa.
 *
 * Se serializa con `org.json` (como el resto de la app), no con kotlinx.serialization.
 */
object RoutinePortable {

    const val FORMAT = "zenit.routine"
    const val VERSION = 1

    /** Construye el JSON portable a partir de una rutina completa de la base de datos local. */
    fun build(routine: RoutineWithDays): JSONObject {
        // Diccionario de ejercicios referenciados (por id) -> embebido con sus datos.
        val exercises = LinkedHashMap<String, JSONObject>()
        routine.sortedDays.forEach { day ->
            day.sortedItems.forEach { ri ->
                val id = ri.item.exerciseId
                if (id.isNotBlank() && !exercises.containsKey(id)) {
                    exercises[id] = exerciseJson(id, ri.exercise, ri.item.rawText)
                }
            }
        }

        val daysArr = JSONArray()
        routine.sortedDays.forEach { day ->
            val itemsArr = JSONArray()
            day.sortedItems.forEach { ri ->
                val item = ri.item
                val obj = JSONObject()
                    .put("exerciseId", item.exerciseId)
                    .put("orderIndex", item.orderIndex)
                    .put("targetSets", item.targetSets)
                    .put("repsMin", item.repsMin)
                    .put("repsMax", item.repsMax)
                    .put("amrap", item.amrap)
                item.targetWeightKg?.let { obj.put("targetWeightKg", it) }
                item.restSeconds?.let { obj.put("restSeconds", it) }
                item.notes?.takeIf { it.isNotBlank() }?.let { obj.put("notes", it) }
                itemsArr.put(obj)
            }
            daysArr.put(
                JSONObject()
                    .put("name", day.day.name)
                    .put("orderIndex", day.day.orderIndex)
                    .put("items", itemsArr)
            )
        }

        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("name", routine.routine.name)
            .apply { routine.routine.notes?.takeIf { it.isNotBlank() }?.let { put("notes", it) } }
            .put("exercises", JSONArray(exercises.values.toList()))
            .put("days", daysArr)
    }

    private fun exerciseJson(id: String, ex: ExerciseEntity?, fallbackName: String): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", ex?.name?.takeIf { it.isNotBlank() } ?: fallbackName.ifBlank { "Ejercicio" })
            .put("primaryMuscle", (ex?.primaryMuscle ?: MuscleGroup.FULL_BODY).name)
            .put("secondaryMuscles", JSONArray((ex?.secondaryMuscles ?: emptyList()).map { it.name }))
            .put("equipment", (ex?.equipment ?: Equipment.OTHER).name)
            .put("isCompound", ex?.isCompound ?: false)
            .put("measure", (ex?.measure ?: MeasureType.REPS).name)
            .put("loadPoints", ex?.loadPoints ?: 2)

    /**
     * Importa una rutina portable en la base de datos local, con filas nuevas.
     * Devuelve el id de la rutina creada, o null si el JSON no es una rutina válida.
     */
    suspend fun import(json: JSONObject, repo: GymRepository): Long? {
        if (json.optString("format") != FORMAT) return null

        val name = json.optString("name").ifBlank { "Rutina compartida" }
        val notes = json.optString("notes").ifBlank { null }

        // 1) Ejercicios: id del origen -> id local. Reutiliza si ya existe; si no, crea personalizado.
        val existingIds = repo.getAllExercises().map { it.id }.toHashSet()
        val idMap = HashMap<String, String>()
        val exArr = json.optJSONArray("exercises") ?: JSONArray()
        for (i in 0 until exArr.length()) {
            val e = exArr.optJSONObject(i) ?: continue
            val oldId = e.optString("id")
            if (oldId.isBlank()) continue
            if (oldId in existingIds) {
                idMap[oldId] = oldId // ya lo tiene (catálogo o importado antes)
                continue
            }
            // Nuevo ejercicio personalizado. oldId no existe localmente, así que es seguro reusarlo.
            repo.upsertExercise(
                ExerciseEntity(
                    id = oldId,
                    name = e.optString("name").ifBlank { "Ejercicio" },
                    primaryMuscle = parseMuscle(e.optString("primaryMuscle")),
                    secondaryMuscles = parseMuscleList(e.optJSONArray("secondaryMuscles")),
                    equipment = parseEquipment(e.optString("equipment")),
                    isCompound = e.optBoolean("isCompound", false),
                    isCustom = true,
                    measure = parseMeasure(e.optString("measure")),
                    loadPoints = e.optInt("loadPoints", 2)
                )
            )
            existingIds.add(oldId)
            idMap[oldId] = oldId
        }

        // 2) Rutina (fila nueva, no activa).
        val routineId = repo.insertRoutine(
            RoutineEntity(name = name, source = RoutineSource.MANUAL, notes = notes, createdAt = repo.now())
        )

        // 3) Días e items.
        val daysArr = json.optJSONArray("days") ?: JSONArray()
        for (di in 0 until daysArr.length()) {
            val d = daysArr.optJSONObject(di) ?: continue
            val dayId = repo.insertDay(
                RoutineDayEntity(
                    routineId = routineId,
                    name = d.optString("name").ifBlank { "Día ${di + 1}" },
                    orderIndex = d.optInt("orderIndex", di)
                )
            )
            val itemsArr = d.optJSONArray("items") ?: JSONArray()
            for (ii in 0 until itemsArr.length()) {
                val it = itemsArr.optJSONObject(ii) ?: continue
                val oldEx = it.optString("exerciseId")
                val localEx = idMap[oldEx] ?: if (oldEx in existingIds) oldEx else continue
                repo.insertItem(
                    RoutineItemEntity(
                        dayId = dayId,
                        exerciseId = localEx,
                        orderIndex = it.optInt("orderIndex", ii),
                        targetSets = it.optInt("targetSets", 3),
                        repsMin = it.optInt("repsMin", 8),
                        repsMax = it.optInt("repsMax", 12),
                        targetWeightKg = if (it.has("targetWeightKg")) it.optDouble("targetWeightKg") else null,
                        restSeconds = if (it.has("restSeconds")) it.optInt("restSeconds") else null,
                        amrap = it.optBoolean("amrap", false),
                        notes = it.optString("notes").ifBlank { null }
                    )
                )
            }
        }
        return routineId
    }

    private fun parseMuscle(name: String): MuscleGroup =
        runCatching { MuscleGroup.valueOf(name) }.getOrDefault(MuscleGroup.FULL_BODY)

    private fun parseMuscleList(arr: JSONArray?): List<MuscleGroup> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { MuscleGroup.valueOf(arr.optString(i)) }.getOrNull()
        }
    }

    private fun parseEquipment(name: String): Equipment =
        runCatching { Equipment.valueOf(name) }.getOrDefault(Equipment.OTHER)

    private fun parseMeasure(name: String): MeasureType =
        runCatching { MeasureType.valueOf(name) }.getOrDefault(MeasureType.REPS)
}
