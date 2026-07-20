package com.aesthetic.gym.domain

import com.aesthetic.gym.domain.comeback.ComebackTier
import com.aesthetic.gym.domain.comeback.evaluate
import com.aesthetic.gym.domain.comeback.thresholdDays
import com.aesthetic.gym.domain.streak.computeWeeklyStreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** La racha va por semanas para no penalizar los dias de descanso. */
class WeeklyStreakTest {

    private val monday = LocalDate.of(2026, 7, 20) // es lunes

    /** Convierte una fecha al epoch que guardaria la app (mediodia local, para evitar bordes). */
    private fun at(date: LocalDate): Long =
        date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Días DENTRO de la semana en curso: hacia delante desde el lunes, sin cruzar de semana. */
    private fun week(vararg offsets: Long): List<Long> =
        offsets.map { at(monday.plusDays(it)) }

    @Test fun `sin sesiones no muestra un cero`() {
        val s = computeWeeklyStreak(emptyList(), 4, monday)
        assertFalse(s.hasHistory)
        assertEquals(0, s.weeks)
        assertEquals("—", s.valueLabel)
        assertEquals("Empieza tu primera semana", s.hintLabel)
    }

    @Test fun `el lunes en blanco no rompe la racha`() {
        // 3 semanas anteriores con 4 dias cada una, y hoy lunes sin entrenar todavia
        val previas = mutableListOf<Long>()
        for (semana in 1..3) {
            for (dia in 0..3) previas += at(monday.minusWeeks(semana.toLong()).plusDays(dia.toLong()))
        }
        val s = computeWeeklyStreak(previas, 4, monday)
        assertEquals(3, s.weeks)
        assertEquals(0, s.doneThisWeek)
        assertFalse(s.currentWeekComplete)
        assertEquals("Esta semana: 0 de 4", s.hintLabel)
    }

    @Test fun `una semana sin cumplir rompe la racha`() {
        val previas = mutableListOf<Long>()
        for (dia in 0..3) previas += at(monday.minusWeeks(1).plusDays(dia.toLong())) // completa
        previas += at(monday.minusWeeks(2))                                          // solo 1 dia
        val s = computeWeeklyStreak(previas, 4, monday)
        assertEquals(1, s.weeks)
    }

    @Test fun `dos sesiones el mismo dia cuentan una vez`() {
        val s = computeWeeklyStreak(listOf(at(monday), at(monday)), 3, monday)
        assertEquals(1, s.doneThisWeek)
    }

    @Test fun `semana en curso cumplida entra en la racha`() {
        val s = computeWeeklyStreak(week(0, 1, 2), 3, monday.plusDays(2))
        assertTrue(s.currentWeekComplete)
        assertEquals(1, s.weeks)
        assertEquals("Semana cumplida · 3 de 3", s.hintLabel)
    }

    @Test fun `nunca dice mas sesiones que el objetivo`() {
        val s = computeWeeklyStreak(week(0, 1, 2, 3, 4), 3, monday.plusDays(4))
        assertEquals(5, s.doneThisWeek)
        assertEquals("Semana cumplida · 3 de 3", s.hintLabel)
        assertEquals(1f, s.fraction, 0.001f)
    }
}

/** El bonus premia volver, no la perfeccion. */
class ComebackDetectorTest {

    private val hoy = LocalDate.of(2026, 7, 20)
    private val instalada = LocalDate.of(2025, 1, 1)

    private fun fechas(vararg atras: Long) = atras.map { hoy.minusDays(it) }

    @Test fun `hace falta historial de verdad`() {
        // Solo 2 sesiones previas: esto es empezar, no volver
        assertNull(evaluate(hoy, fechas(30, 60), 2, 4, 1, instalada))
    }

    @Test fun `un hueco normal no es un regreso`() {
        // Rutina de 4 dias: umbral 5. Hueco de 4 dias es cadencia normal.
        assertNull(evaluate(hoy, fechas(4, 6, 8, 11), 4, 4, 1, instalada))
    }

    @Test fun `un hueco real si dispara el bonus`() {
        val b = evaluate(hoy, fechas(9, 11, 13, 16), 4, 4, 1, instalada)
        assertEquals(9, b!!.days)
        assertEquals(ComebackTier.SHORT, b.tier)
    }

    @Test fun `los tramos se reparten bien`() {
        assertEquals(ComebackTier.MEDIUM, evaluate(hoy, fechas(30, 33, 36, 39), 4, 4, 1, instalada)!!.tier)
        assertEquals(ComebackTier.LONG, evaluate(hoy, fechas(200, 203, 206, 209), 4, 4, 1, instalada)!!.tier)
    }

    @Test fun `sin series confirmadas no hay premio`() {
        assertNull(evaluate(hoy, fechas(30, 33, 36), 3, 4, 0, instalada))
    }

    @Test fun `una restauracion de datos no cuenta como regreso`() {
        // La sesion anterior es anterior a la instalacion: los datos vienen de un backup
        val instaladaAyer = hoy.minusDays(1)
        assertNull(evaluate(hoy, fechas(30, 33, 36), 5, 4, 1, instaladaAyer))
    }

    @Test fun `el umbral no se hunde con un historial irregular`() {
        // Huecos 1,1,14,1 -> mediana 1. Sin el suelo, cobraria bonus cada 2 dias.
        val irregular = fechas(1, 2, 3, 17, 18)
        assertEquals(MIN_UMBRAL, thresholdDays(4, irregular))
        assertNull(evaluate(hoy, irregular, 5, 4, 1, instalada))
    }

    @Test fun `quien entrena una vez por semana necesita un hueco mayor`() {
        val semanal = fechas(7, 14, 21, 28)
        assertEquals(14, thresholdDays(1, semanal))
        assertNull(evaluate(hoy, semanal, 4, 1, 1, instalada))
    }

    private companion object { const val MIN_UMBRAL = 5 }
}
