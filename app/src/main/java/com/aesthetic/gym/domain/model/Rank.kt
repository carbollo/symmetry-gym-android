package com.aesthetic.gym.domain.model

/**
 * Strength tiers ("rangos"). A continuous 0..100 score maps to one of six tiers,
 * each with its own color used to paint the body map.
 */
enum class Rank(val displayName: String, val color: Long, val minScore: Int) {
    PRINCIPIANTE("Principiante", 0xFF5B616B, 0),
    NOVATO("Novato", 0xFFB87333, 17),
    INTERMEDIO("Intermedio", 0xFFAEB6BD, 34),
    AVANZADO("Avanzado", 0xFFE2B33C, 51),
    ELITE("Élite", 0xFF27D2E6, 68),
    MAESTRO("Maestro", 0xFF9B6BFA, 85);

    companion object {
        fun fromScore(score: Int): Rank {
            var result = PRINCIPIANTE
            for (rank in entries) {
                if (score >= rank.minScore) result = rank
            }
            return result
        }
    }
}
