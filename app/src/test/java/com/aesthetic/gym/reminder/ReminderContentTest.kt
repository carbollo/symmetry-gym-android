package com.aesthetic.gym.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.ZoneId

/** La regla decreciente: diario, luego semanal, luego se apaga (HeartSteps: el efecto se agota). */
class ReminderPlanTest {

    @Test fun `pocos ignorados sigue diario`() {
        assertEquals(ReminderPlan.NORMAL, decideReminderPlan(0))
        assertEquals(ReminderPlan.NORMAL, decideReminderPlan(2))
    }

    @Test fun `tres a cinco pasa a semanal`() {
        assertEquals(ReminderPlan.WEEKLY, decideReminderPlan(3))
        assertEquals(ReminderPlan.WEEKLY, decideReminderPlan(5))
    }

    @Test fun `seis o mas se autodesactiva`() {
        assertEquals(ReminderPlan.DISABLE, decideReminderPlan(6))
        assertEquals(ReminderPlan.DISABLE, decideReminderPlan(20))
    }
}

/** El próximo aviso cae en el primer (día elegido, hora) estrictamente futuro. */
class NextTriggerTest {

    private val zone = ZoneId.of("Europe/Madrid")
    // Lunes 20 jul 2026, 20:00.
    private val monEvening = ZonedDateTime.of(2026, 7, 20, 20, 0, 0, 0, zone)

    @Test fun `si hoy ya paso la hora, salta al proximo dia elegido`() {
        // Lunes 20:00, aviso a las 19:00 los lunes -> el próximo lunes (7 días).
        val ms = ReminderScheduler.nextTriggerMillis(setOf(DayOfWeek.MONDAY), 19, 0, monEvening)!!
        val days = ms / (24 * 60 * 60 * 1000.0)
        assertTrue("debería ser ~7 días", days in 6.9..7.1)
    }

    @Test fun `hoy mas tarde el mismo dia`() {
        // Lunes 20:00, aviso a las 21:00 los lunes -> hoy, dentro de 1 hora.
        val ms = ReminderScheduler.nextTriggerMillis(setOf(DayOfWeek.MONDAY), 21, 0, monEvening)!!
        assertEquals(60 * 60 * 1000L, ms)
    }

    @Test fun `elige el proximo dia de la semana`() {
        // Lunes 20:00, aviso miércoles 07:00 -> pasado mañana por la mañana.
        val ms = ReminderScheduler.nextTriggerMillis(setOf(DayOfWeek.WEDNESDAY), 7, 0, monEvening)!!
        val days = ms / (24 * 60 * 60 * 1000.0)
        assertTrue("~1.46 días", days in 1.4..1.6)
    }

    @Test fun `sin dias no programa`() {
        assertNull(ReminderScheduler.nextTriggerMillis(emptySet(), 19, 0, monEvening))
    }

    @Test fun `parse y format van y vuelven`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertEquals("MON,WED,FRI", ReminderScheduler.formatDays(days))
        assertEquals(days, ReminderScheduler.parseDays("MON,WED,FRI"))
        assertEquals(emptySet<DayOfWeek>(), ReminderScheduler.parseDays(""))
        assertEquals(emptySet<DayOfWeek>(), ReminderScheduler.parseDays("basura,xyz"))
    }
}
