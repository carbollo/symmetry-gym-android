package com.aesthetic.gym.pdf

import com.aesthetic.gym.util.normalizeText

data class ParsedItem(
    val rawText: String,
    val name: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val weightKg: Double?,
    val amrap: Boolean,
    val restSeconds: Int?
)

data class ParsedDay(val name: String, val items: List<ParsedItem>)

data class ParsedRoutine(val name: String, val days: List<ParsedDay>) {
    val exerciseCount: Int get() = days.sumOf { it.items.size }
}

/**
 * Parses a free-text gym routine (typically extracted from a PDF) into a structured routine.
 * Recognised lines:
 *  - "Rutina: <nombre>"            -> routine name
 *  - "Día 1: <nombre>" / "<sección>:" -> a training day / section
 *  - "<ejercicio>: <series>x<reps>[-<reps>] [@ <peso>kg]" -> an exercise
 */
object RoutineParser {

    private const val LB_PER_KG = 2.2046226218

    private val routineHeader =
        Regex("^(rutina|routine|programa|plan|workout)\\s*[:\\-]\\s*(.+)", RegexOption.IGNORE_CASE)
    private val dayPrefix = Regex("^(dia|day)\\b")
    private val setsReps =
        Regex("(\\d+)\\s*[xX×]\\s*(amrap|fallo|max|f|\\d+)(?:\\s*[-–aA]\\s*(\\d+))?", RegexOption.IGNORE_CASE)
    private val bullet = Regex("^\\s*([\\-*•·–—►▶]+|\\d+[.)])\\s*")
    private val weightAt =
        Regex("@\\s*(\\d+(?:[.,]\\d+)?)\\s*(kg|kgs|kilos|lb|lbs)?", RegexOption.IGNORE_CASE)
    private val weightUnit =
        Regex("(\\d+(?:[.,]\\d+)?)\\s*(kg|kgs|kilos|lb|lbs)", RegexOption.IGNORE_CASE)
    private val restRegex =
        Regex("descanso\\s*(\\d+)\\s*(s|seg|segundos|min|m|minutos)?", RegexOption.IGNORE_CASE)

    private class MutableDay(val name: String) {
        val items = mutableListOf<ParsedItem>()
    }

    fun parse(rawText: String, fallbackName: String): ParsedRoutine {
        var routineName = fallbackName
        val days = mutableListOf<MutableDay>()
        var current: MutableDay? = null

        val lines = rawText.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            val rh = routineHeader.find(line)
            if (rh != null) {
                val n = stripTrailingColon(rh.groupValues[2].trim())
                if (n.isNotBlank()) routineName = n
                continue
            }

            if (isDayHeader(line)) {
                val day = MutableDay(cleanDayName(line))
                days.add(day)
                current = day
                continue
            }

            val item = parseExerciseLine(line)
            if (item != null) {
                val day = current ?: MutableDay("Día 1").also { days.add(it); current = it }
                day.items.add(item)
            }
        }

        val result = days.filter { it.items.isNotEmpty() }
            .map { ParsedDay(it.name, it.items.toList()) }
        return ParsedRoutine(routineName, result)
    }

    private fun isDayHeader(line: String): Boolean {
        val norm = normalizeText(line)
        if (dayPrefix.containsMatchIn(norm)) return true
        return line.endsWith(":") && line.length <= 40 && setsReps.find(line) == null
    }

    private fun cleanDayName(line: String): String =
        stripTrailingColon(line.replace(bullet, "").trim())

    private fun stripTrailingColon(s: String): String = s.trimEnd(':', ' ', '-', '–').trim()

    fun parseExerciseLine(line: String): ParsedItem? {
        val noBullet = line.replace(bullet, "").trim()
        val m = setsReps.find(noBullet) ?: return null
        val sets = m.groupValues[1].toIntOrNull()?.coerceIn(1, 30) ?: return null

        val repsToken = m.groupValues[2].lowercase()
        val asInt = repsToken.toIntOrNull()
        val amrap = asInt == null
        var repsMin = 0
        var repsMax = 0
        if (!amrap) {
            val r1 = asInt!!
            val r2 = m.groupValues[3].toIntOrNull()
            repsMin = r1
            repsMax = r2 ?: r1
            if (repsMax < repsMin) {
                val t = repsMin; repsMin = repsMax; repsMax = t
            }
            repsMin = repsMin.coerceIn(1, 100)
            repsMax = repsMax.coerceIn(1, 100)
        }

        val name = noBullet.substring(0, m.range.first).trim().trimEnd(':', '-', '|', '·', '–').trim()
        if (name.isBlank() || name.length < 2) return null

        val remainder = noBullet.substring((m.range.last + 1).coerceAtMost(noBullet.length))
        val weightKg = parseWeight(remainder) ?: parseWeight(noBullet)
        val rest = restRegex.find(noBullet)?.let {
            val v = it.groupValues[1].toIntOrNull() ?: return@let null
            val unit = it.groupValues[2].lowercase()
            if (unit.startsWith("m")) v * 60 else v
        }

        return ParsedItem(line, name, sets, repsMin, repsMax, weightKg, amrap, rest)
    }

    private fun parseWeight(text: String): Double? {
        weightAt.find(text)?.let { return toKg(it.groupValues[1], it.groupValues[2]) }
        weightUnit.find(text)?.let { return toKg(it.groupValues[1], it.groupValues[2]) }
        return null
    }

    private fun toKg(numStr: String, unit: String): Double? {
        val v = numStr.replace(',', '.').toDoubleOrNull() ?: return null
        if (v <= 0 || v > 1000) return null
        return if (unit.lowercase().startsWith("lb")) v / LB_PER_KG else v
    }
}
