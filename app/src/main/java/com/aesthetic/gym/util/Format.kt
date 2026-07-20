package com.aesthetic.gym.util

import com.aesthetic.gym.domain.model.WeightUnit
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val LB_PER_KG = 2.2046226218

/** Lowercases, strips accents and collapses whitespace. Used by the routine parser/matcher. */
fun normalizeText(input: String): String {
    val nfd = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    val noAccents = nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    return noAccents.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}

fun kgToUnit(kg: Double, unit: WeightUnit): Double =
    if (unit == WeightUnit.LB) kg * LB_PER_KG else kg

fun unitToKg(value: Double, unit: WeightUnit): Double =
    if (unit == WeightUnit.LB) value / LB_PER_KG else value

private fun trimZeros(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString()
    else rounded.toString().trimEnd('0').trimEnd('.')
}

/** Formats a kg value in the user's unit, e.g. "60 kg" or "132.3 lb". */
fun formatWeight(kg: Double, unit: WeightUnit): String =
    "${trimZeros(kgToUnit(kg, unit))} ${unit.label}"

fun formatWeightValue(kg: Double, unit: WeightUnit): String = trimZeros(kgToUnit(kg, unit))

/** Formats a raw kg amount with no unit suffix, trimming trailing zeros: "62.5", "60". */
fun formatKg(kg: Double): String = trimZeros(kg)

/** Epley estimated one-rep max. */
fun epley1RM(weightKg: Double, reps: Int): Double =
    if (reps <= 1) weightKg else weightKg * (1.0 + reps / 30.0)

private val dayFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))
private val shortFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("es"))

fun epochToLocalDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatDate(epochMillis: Long): String = epochToLocalDate(epochMillis).format(dayFormatter)

fun formatShortDate(epochMillis: Long): String = epochToLocalDate(epochMillis).format(shortFormatter)

fun relativeDay(epochMillis: Long): String {
    val date = epochToLocalDate(epochMillis)
    val today = LocalDate.now()
    return when (val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)) {
        0L -> "Hoy"
        1L -> "Ayer"
        in 2..6 -> "hace $days días"
        else -> formatShortDate(epochMillis)
    }
}
