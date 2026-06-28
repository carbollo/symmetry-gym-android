package com.aesthetic.gym.pdf

import com.aesthetic.gym.domain.model.Equipment
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.util.normalizeText

/** Best-effort guess of muscle group / equipment for exercises that aren't in the catalog. */
object MuscleGuesser {

    fun guessMuscle(name: String): MuscleGroup {
        val n = normalizeText(name)
        fun has(vararg keys: String) = keys.any { n.contains(it) }
        return when {
            has("femoral", "isquio", "hamstring", "rumano", "curl de pierna", "leg curl") -> MuscleGroup.HAMSTRINGS
            has("gluteo", "hip thrust", "glute", "patada de gluteo", "abductor", "abduccion") -> MuscleGroup.GLUTES
            has("gemelo", "calf", "pantorrilla", "soleo") -> MuscleGroup.CALVES
            has("cuadricep", "sentadilla", "squat", "prensa", "leg press", "zancada", "desplante",
                "estocada", "extension de pierna", "leg extension", "bulgara", "hack") -> MuscleGroup.QUADS
            has("pecho", "press de banca", "bench", "aperturas", "fly", "cruces", "fondos", "flexion", "push up") -> MuscleGroup.CHEST
            has("espalda", "remo", "row", "dominad", "jalon", "pulldown", "pull up", "peso muerto", "deadlift", "pullover") -> MuscleGroup.BACK
            has("trapecio", "encogim", "shrug", "face pull") -> MuscleGroup.TRAPS
            has("hombro", "shoulder", "militar", "overhead", "lateral", "frontal", "deltoide", "arnold", "pajaro") -> MuscleGroup.SHOULDERS
            has("tricep", "frances", "skull", "pushdown", "kickback", "rompecraneos") -> MuscleGroup.TRICEPS
            has("bicep", "curl") -> MuscleGroup.BICEPS
            has("antebrazo", "muneca", "forearm", "wrist") -> MuscleGroup.FOREARMS
            has("abdomen", "abdominal", "crunch", "plancha", "plank", "core", "oblicuo", "elevacion de pierna") -> MuscleGroup.ABS
            else -> MuscleGroup.FULL_BODY
        }
    }

    fun guessEquipment(name: String): Equipment {
        val n = normalizeText(name)
        fun has(vararg keys: String) = keys.any { n.contains(it) }
        return when {
            has("mancuerna", "dumbbell") -> Equipment.DUMBBELL
            has("barra", "barbell") -> Equipment.BARBELL
            has("polea", "cable") -> Equipment.CABLE
            has("maquina", "machine", "prensa") -> Equipment.MACHINE
            has("dominad", "flexion", "fondos", "plancha", "pull up", "push up") -> Equipment.BODYWEIGHT
            else -> Equipment.OTHER
        }
    }
}
