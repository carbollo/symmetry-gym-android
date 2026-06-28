package com.aesthetic.gym.domain.model

/** Muscle groups tracked across the app: used for the body map, ranks and routine import. */
enum class MuscleGroup(val displayName: String, val region: BodyRegion) {
    CHEST("Pecho", BodyRegion.FRONT),
    SHOULDERS("Hombros", BodyRegion.FRONT),
    BICEPS("Bíceps", BodyRegion.FRONT),
    FOREARMS("Antebrazos", BodyRegion.FRONT),
    ABS("Abdomen", BodyRegion.FRONT),
    QUADS("Cuádriceps", BodyRegion.FRONT),
    TRAPS("Trapecio", BodyRegion.BACK),
    BACK("Espalda", BodyRegion.BACK),
    TRICEPS("Tríceps", BodyRegion.BACK),
    GLUTES("Glúteos", BodyRegion.BACK),
    HAMSTRINGS("Femoral", BodyRegion.BACK),
    CALVES("Gemelos", BodyRegion.BACK),
    FULL_BODY("Cuerpo completo", BodyRegion.NONE);

    /** Groups shown on the interactive body map (FULL_BODY is excluded). */
    companion object {
        val ranked: List<MuscleGroup> = entries.filter { it != FULL_BODY }
    }
}

enum class BodyRegion { FRONT, BACK, NONE }
