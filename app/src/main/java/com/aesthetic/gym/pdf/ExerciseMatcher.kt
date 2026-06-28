package com.aesthetic.gym.pdf

import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.data.seed.ExerciseCatalog
import com.aesthetic.gym.util.normalizeText

/**
 * Fuzzy-matches a free-text exercise name (from an imported routine) against the catalog
 * and any custom exercises already in the database.
 */
class ExerciseMatcher private constructor(
    private val entries: MutableList<Entry>
) {
    private data class Entry(val id: String, val aliasesNorm: List<String>)

    /** Registers a freshly created exercise so repeated lines in the same import reuse it. */
    fun register(id: String, name: String) {
        entries.add(Entry(id, listOf(normalizeText(name))))
    }

    fun match(rawName: String, threshold: Double = 0.55): String? {
        val query = normalizeText(rawName)
        if (query.isBlank()) return null
        val qTokens = query.split(' ').filter { it.isNotBlank() }.toSet()

        var bestId: String? = null
        var bestScore = 0.0
        for (entry in entries) {
            for (alias in entry.aliasesNorm) {
                val score = aliasScore(query, qTokens, alias)
                if (score > bestScore) {
                    bestScore = score
                    bestId = entry.id
                }
            }
        }
        return if (bestScore >= threshold) bestId else null
    }

    private fun aliasScore(query: String, qTokens: Set<String>, alias: String): Double {
        if (query == alias) return 1.0
        val aTokens = alias.split(' ').filter { it.isNotBlank() }.toSet()
        if (aTokens.isEmpty()) return 0.0
        val inter = qTokens.intersect(aTokens).size
        if (inter == 0) return 0.0
        if (alias.contains(query) || query.contains(alias)) return 0.9
        val union = qTokens.union(aTokens).size
        return inter.toDouble() / union.toDouble()
    }

    companion object {
        /** Builds a matcher from the catalog (with aliases) plus any custom DB exercises. */
        fun build(extra: List<ExerciseEntity> = emptyList()): ExerciseMatcher {
            val catalogIds = ExerciseCatalog.all.map { it.entity.id }.toSet()
            val entries = ExerciseCatalog.all.map { cat ->
                Entry(cat.entity.id, cat.aliases.map { normalizeText(it) }.distinct())
            }.toMutableList()
            extra.filter { it.id !in catalogIds }.forEach { ex ->
                entries.add(Entry(ex.id, listOf(normalizeText(ex.name))))
            }
            return ExerciseMatcher(entries)
        }
    }
}
