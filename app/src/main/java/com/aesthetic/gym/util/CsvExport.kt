package com.aesthetic.gym.util

import com.aesthetic.gym.data.db.ExportRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds the CSV backup of the whole training history. */
object CsvExport {

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale("es"))
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale("es"))

    private const val HEADER =
        "fecha;entreno;ejercicio;musculo;serie;tipo;medida;peso_kg;reps;rir;completada"

    fun suggestedFileName(nowMillis: Long): String =
        "symmetry-${format(nowMillis, fileStamp)}.csv"

    fun build(rows: List<ExportRow>): String {
        val sb = StringBuilder(HEADER.length + rows.size * 64)
        sb.append('﻿') // BOM so Excel opens the accents correctly
        sb.append(HEADER).append('\n')
        for (r in rows) {
            sb.append(format(r.date, stamp)).append(';')
                .append(escape(r.sessionName)).append(';')
                .append(escape(r.exercise)).append(';')
                .append(escape(r.muscle)).append(';')
                .append(r.setNumber).append(';')
                .append(if (r.isWarmup) "calentamiento" else "normal").append(';')
                .append(if (r.measure == "SECONDS") "segundos" else "reps").append(';')
                .append(formatKg(r.weightKg)).append(';')
                .append(r.reps).append(';')
                .append(r.rpe?.let { formatKg(10.0 - it) } ?: "").append(';')
                .append(if (r.completed) "si" else "no").append('\n')
        }
        return sb.toString()
    }

    private fun format(millis: Long, formatter: DateTimeFormatter): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formatter)

    /** Semicolon-separated, so any semicolon or newline inside a name has to be neutralised. */
    private fun escape(value: String): String =
        value.replace(';', ',').replace('\n', ' ').replace('\r', ' ').trim()
}
