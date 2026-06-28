package com.aesthetic.gym.domain.model

/**
 * Counter-Strike 2 style ranks (Silver I → The Global Elite). A continuous 0..100 strength score
 * maps to one of the 18 tiers, each with its own color used to paint the body map and badges.
 */
enum class Rank(val displayName: String, val short: String, val color: Long, val minScore: Int) {
    SILVER_I("Silver I", "S1", 0xFFB7BFCC, 0),
    SILVER_II("Silver II", "S2", 0xFFAEB7C4, 5),
    SILVER_III("Silver III", "S3", 0xFFA6AFBD, 11),
    SILVER_IV("Silver IV", "S4", 0xFF9CA6B5, 16),
    SILVER_ELITE("Silver Elite", "SE", 0xFF8B97A8, 22),
    SILVER_ELITE_MASTER("Silver Elite Master", "SEM", 0xFF7B8696, 27),
    GOLD_NOVA_I("Gold Nova I", "GN1", 0xFFE6C24A, 33),
    GOLD_NOVA_II("Gold Nova II", "GN2", 0xFFEDB935, 38),
    GOLD_NOVA_III("Gold Nova III", "GN3", 0xFFF2AE22, 44),
    GOLD_NOVA_MASTER("Gold Nova Master", "GNM", 0xFFF7A300, 50),
    MASTER_GUARDIAN_I("Master Guardian I", "MG1", 0xFF5BC8A3, 55),
    MASTER_GUARDIAN_II("Master Guardian II", "MG2", 0xFF36BE9A, 61),
    MASTER_GUARDIAN_ELITE("Master Guardian Elite", "MGE", 0xFF2BB58A, 66),
    DISTINGUISHED_MASTER_GUARDIAN("Distinguished Master Guardian", "DMG", 0xFF4A90E2, 72),
    LEGENDARY_EAGLE("Legendary Eagle", "LE", 0xFF6C8CFF, 77),
    LEGENDARY_EAGLE_MASTER("Legendary Eagle Master", "LEM", 0xFF8A6BFF, 83),
    SUPREME("Supreme Master First Class", "SMFC", 0xFFE0556B, 88),
    GLOBAL_ELITE("The Global Elite", "GE", 0xFFF2C14E, 94);

    companion object {
        fun fromScore(score: Int): Rank {
            var result = SILVER_I
            for (rank in entries) {
                if (score >= rank.minScore) result = rank
            }
            return result
        }
    }
}
