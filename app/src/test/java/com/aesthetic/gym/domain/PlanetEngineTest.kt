package com.aesthetic.gym.domain

import com.aesthetic.gym.data.db.SessionWithSets
import com.aesthetic.gym.data.db.SetLogEntity
import com.aesthetic.gym.data.db.SetMuscleRow
import com.aesthetic.gym.data.db.WorkoutSessionEntity
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.domain.planet.PlanetEngine
import com.aesthetic.gym.domain.planet.PlanetStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El planeta se deriva solo del historial: mismo historial = mismo planeta, siempre. */
class PlanetEngineTest {

    private val dayMs = 86_400_000L

    /** Una sesión el día [day] (UTC) con [workSets] series válidas y [warmups] de calentamiento. */
    private fun session(id: Long, day: Long, workSets: Int, warmups: Int = 0): SessionWithSets {
        val startedAt = day * dayMs + 10 * 3_600_000L // a las 10:00 UTC de ese día
        val sets = buildList {
            repeat(workSets) { n ->
                add(SetLogEntity(id = id * 100 + n, sessionId = id, exerciseId = "e",
                    setNumber = n + 1, reps = 10, weightKg = 50.0))
            }
            repeat(warmups) { n ->
                add(SetLogEntity(id = id * 100 + 50 + n, sessionId = id, exerciseId = "e",
                    setNumber = n + 1, reps = 10, weightKg = 20.0, isWarmup = true))
            }
        }
        return SessionWithSets(
            WorkoutSessionEntity(id = id, name = "s$id", startedAt = startedAt, finishedAt = startedAt + 1),
            sets
        )
    }

    /** [days] días de entreno consecutivos con 12 series cada uno: 22 pts/día. */
    private fun trainingDays(days: Int, setsPerDay: Int = 12): List<SessionWithSets> =
        (1..days).map { session(it.toLong(), it.toLong(), setsPerDay) }

    private fun muscleRows(muscle: MuscleGroup, sets: Int): List<SetMuscleRow> =
        List(sets) { SetMuscleRow("e", 50.0, 10, muscle, false) }

    @Test fun `sin historial - genesis en el planeta cero`() {
        val s = PlanetEngine.compute(emptyList(), emptyList())
        assertFalse(s.hasHistory)
        assertEquals(0, s.planetIndex)
        assertEquals(PlanetStage.GENESIS, s.stage)
        assertEquals(0, s.xpInPlanet)
        assertEquals(0, s.species)
    }

    @Test fun `cualquier primer entreno despierta el planeta`() {
        // Día con solo 3 series = 13 pts >= umbral de Pulso Interno (10): victoria temprana.
        val s = PlanetEngine.compute(listOf(session(1, 1, workSets = 3)), emptyList())
        assertEquals(PlanetStage.PULSO, s.stage)
        assertEquals(13, s.totalXp)
    }

    @Test fun `un dia tipico da base mas series, sin calentamientos`() {
        val s = PlanetEngine.compute(listOf(session(1, 1, workSets = 12, warmups = 3)), emptyList())
        assertEquals(22, s.totalXp) // 10 + 12
        assertEquals(22, s.lastDayXp)
    }

    @Test fun `menos de 3 series al dia no puntua`() {
        val s = PlanetEngine.compute(listOf(session(1, 1, workSets = 2)), emptyList())
        assertEquals(0, s.totalXp)
        assertTrue(s.hasHistory)
    }

    @Test fun `trocear el entreno en varias sesiones no da mas puntos`() {
        // Mismo día: 2 sesiones de 6 series = 1 sesión de 12 series (base única por día).
        val troceado = listOf(session(1, 1, 6), session(2, 1, 6))
        val entero = listOf(session(3, 1, 12))
        assertEquals(
            PlanetEngine.compute(entero, emptyList()).totalXp,
            PlanetEngine.compute(troceado, emptyList()).totalXp
        )
    }

    @Test fun `el tope diario evita series basura`() {
        val s = PlanetEngine.compute(listOf(session(1, 1, workSets = 40)), emptyList())
        assertEquals(25, s.totalXp) // 10 + min(40, 15)
    }

    @Test fun `las etapas avanzan con el total`() {
        // 10 días × 22 pts = 220 -> Mar Primigenio (210 <= 220 < 330).
        val s = PlanetEngine.compute(trainingDays(10), emptyList())
        assertEquals(PlanetStage.MAR, s.stage)
        assertEquals(220, s.xpInPlanet)
        assertEquals(330, s.nextStageAt)
    }

    @Test fun `completar el planeta arranca el siguiente con el excedente`() {
        // 28 días × 22 = 616; planeta 0 cuesta 600 -> planeta 1 con 16 pts (ya en Pulso).
        val s = PlanetEngine.compute(trainingDays(28), emptyList())
        assertEquals(1, s.planetIndex)
        assertEquals(16, s.xpInPlanet)
        assertEquals(PlanetStage.PULSO, s.stage) // 16 >= umbral escalado 10*120/100 = 12
        assertEquals(1, s.completedSeeds.size)
        assertEquals(720, s.planetCost) // +20%
    }

    @Test fun `el coste escala 20 por ciento con tope x2`() {
        assertEquals(600, PlanetEngine.planetCost(0))
        assertEquals(720, PlanetEngine.planetCost(1))
        assertEquals(1200, PlanetEngine.planetCost(5))
        assertEquals(1200, PlanetEngine.planetCost(30)) // tope
    }

    @Test fun `la marca de agua impide que el planeta regrese`() {
        // El historial deriva 22 pts, pero ya habíamos visto 220: se mantiene 220.
        val s = PlanetEngine.compute(trainingDays(1), emptyList(), highWaterXp = 220)
        assertEquals(220, s.totalXp)
        assertEquals(PlanetStage.MAR, s.stage)
    }

    @Test fun `la semilla persistida congela el aspecto`() {
        val sesiones = trainingDays(5)
        val primera = PlanetEngine.compute(sesiones, emptyList())
        // Aunque el historial cambie (se borró la primera sesión), la semilla guardada manda.
        val trasBorrar = PlanetEngine.compute(sesiones.drop(1), emptyList(), baseSeed = primera.baseSeed)
        assertEquals(primera.seed, trasBorrar.seed)
    }

    @Test fun `mismo historial - misma semilla, distinto planeta - distinta`() {
        val sesiones = trainingDays(3)
        val a = PlanetEngine.compute(sesiones, emptyList())
        val b = PlanetEngine.compute(sesiones, emptyList())
        assertEquals(a.seed, b.seed)
        assertNotEquals(PlanetEngine.seedFor(a.baseSeed, 0), PlanetEngine.seedFor(a.baseSeed, 1))
    }

    @Test fun `el orden de las sesiones no cambia el resultado`() {
        val ordenadas = trainingDays(5)
        assertEquals(
            PlanetEngine.compute(ordenadas, emptyList()),
            PlanetEngine.compute(ordenadas.reversed(), emptyList())
        )
    }

    @Test fun `las especies piden un minimo de series y arrancan en 1 con historial`() {
        val rows = muscleRows(MuscleGroup.CHEST, 6) + muscleRows(MuscleGroup.BACK, 5)
        val s = PlanetEngine.compute(trainingDays(1), rows)
        assertEquals(1, s.species) // pecho sí (6), espalda no (5); mínimo 1 con historial
        val dos = muscleRows(MuscleGroup.CHEST, 6) + muscleRows(MuscleGroup.BACK, 6)
        assertEquals(2, PlanetEngine.compute(trainingDays(1), dos).species)
    }

    @Test fun `la estimacion usa la mediana de puntos por dia`() {
        // 3 días de 22 pts: mediana 22. En Pulso (22..110): faltan 88 -> ceil(88/22) = 4.
        val s = PlanetEngine.compute(trainingDays(3).take(1), emptyList())
        assertEquals(22, s.medianDayXp)
        assertEquals(PlanetStage.PULSO, s.stage)
        assertEquals(4, s.daysToNextStage)
    }
}
