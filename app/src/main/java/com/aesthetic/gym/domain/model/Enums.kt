package com.aesthetic.gym.domain.model

enum class Equipment(val displayName: String) {
    BARBELL("Barra"),
    DUMBBELL("Mancuerna"),
    MACHINE("Máquina"),
    CABLE("Polea"),
    BODYWEIGHT("Peso corporal"),
    OTHER("Otro")
}

enum class Sex { MALE, FEMALE, OTHER }

enum class WeightUnit(val label: String) { KG("kg"), LB("lb") }

enum class RoutineSource { MANUAL, PDF, TEXT }

enum class PhotoPose(val displayName: String) {
    FRONT("Frente"),
    SIDE("Lateral"),
    BACK("Espalda")
}

enum class GoalType(val displayName: String, val unit: String) {
    BODYWEIGHT("Peso corporal", "kg"),
    LIFT_1RM("1RM de un ejercicio", "kg"),
    STRENGTH_SCORE("Puntuación de fuerza", "pts"),
    WORKOUTS("Nº de entrenos", ""),
    CUSTOM("Personalizado", "")
}
