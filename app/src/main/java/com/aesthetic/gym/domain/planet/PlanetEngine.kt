package com.aesthetic.gym.domain.planet

import com.aesthetic.gym.data.db.SessionWithSets
import com.aesthetic.gym.data.db.SetMuscleRow

/**
 * Sistema de motivación "planeta": entrenar da puntos de vida que hacen evolucionar un mundo
 * por etapas (Génesis → Edad de las Luces). Al completarse, el planeta pasa a la galaxia
 * privada y empieza otro más caro. Con cuenta, un snapshot mínimo se publica a la galaxia
 * global para que otros vean tu mundo.
 *
 * Principios (retención ética):
 *  - Los puntos se derivan del historial: quien lleva meses entrenando abre la feature con
 *    su planeta ya crecido.
 *  - Se puntúa por DÍA de entreno, no por sesión: trocear un entreno en varias sesiones no
 *    da nada extra, y añadir series basura satura en el tope diario.
 *  - El planeta NUNCA regresa: sin timers, sin decaimiento, sin culpa. Editar o borrar
 *    sesiones tampoco lo encoge (marca de agua persistida por el llamador).
 *  - Matemática entera pura: el servidor deriva etapa/planeta desde totalXp con esta misma
 *    fórmula y ambos llegan SIEMPRE al mismo resultado (sin floats, sin drift).
 */

/** Etapas de evolución, en orden físico creíble: la atmósfera precede al agua líquida. */
enum class PlanetStage(val displayName: String, val hint: String) {
    GENESIS("Génesis", "Un mundo dormido espera tu primer entreno"),
    PULSO("Pulso Interno", "El núcleo despierta con tu esfuerzo"),
    ALIENTO("Primer Aliento", "Una atmósfera propia envuelve el planeta"),
    MAR("Mar Primigenio", "El agua cubre las cuencas"),
    VERDE("Umbral Verde", "La vida vegetal se abre paso"),
    LATIDO("Latido Salvaje", "Criaturas pueblan tu mundo"),
    LUCES("Edad de las Luces", "Ciudades brillan en la noche: tu mundo está vivo")
}

data class PlanetState(
    /** Índice del planeta actual == nº de planetas ya completados. */
    val planetIndex: Int = 0,
    val stage: PlanetStage = PlanetStage.GENESIS,
    /** Puntos acumulados dentro del planeta actual. */
    val xpInPlanet: Int = 0,
    /** Puntos para completar este planeta. */
    val planetCost: Int = PlanetEngine.BASE_COST,
    /** Umbral donde empezó la etapa actual (para la barra de progreso). */
    val stageStart: Int = 0,
    /** Próximo umbral (== planetCost si la etapa es la última). */
    val nextStageAt: Int = 0,
    /** Puntos de vida totales (con la marca de agua aplicada). */
    val totalXp: Int = 0,
    /** Grupos musculares bien entrenados → variedad de especies (1..12, mín. 1 con historial). */
    val species: Int = 1,
    /** Semilla visual del planeta actual (int32: viaja tal cual al backend). */
    val seed: Int = 0,
    /** Entropía base efectiva; el llamador la persiste la primera vez para congelarla. */
    val baseSeed: Long = 0L,
    /** Semillas de los planetas completados, en orden: la galaxia privada. */
    val completedSeeds: List<Int> = emptyList(),
    /** Puntos del último día de entreno puntuado (feedback "+N"). */
    val lastDayXp: Int = 0,
    /** Mediana de puntos por día de entreno: para estimar "a ~N entrenos de X". */
    val medianDayXp: Int = PlanetEngine.DEFAULT_DAY_XP,
    val hasHistory: Boolean = false
) {
    /** Progreso 0..1 dentro de la etapa actual. */
    val stageFraction: Float
        get() {
            val span = nextStageAt - stageStart
            if (span <= 0) return 1f
            return ((xpInPlanet - stageStart).toFloat() / span).coerceIn(0f, 1f)
        }

    /** Bucket 0..3 de progreso intra-etapa: gradúa la densidad visual sin regenerar nada. */
    val progressQ: Int get() = (stageFraction * 4).toInt().coerceIn(0, 3)

    /** Entrenos estimados hasta la siguiente etapa, en la unidad honesta (días de entreno). */
    val daysToNextStage: Int
        get() {
            val remaining = nextStageAt - xpInPlanet
            if (remaining <= 0) return 0
            val perDay = medianDayXp.coerceAtLeast(1)
            return (remaining + perDay - 1) / perDay
        }

    /** Siguiente etapa (null si esta es la última: lo próximo es un mundo nuevo). */
    val nextStage: PlanetStage?
        get() = PlanetStage.entries.getOrNull(stage.ordinal + 1)
}

object PlanetEngine {

    /**
     * Umbrales de etapa del planeta base y coste total. El primer umbral (10) es MENOR que
     * el mínimo de un día puntuable (13): cualquier primer entreno despierta el planeta.
     * A ~20-25 pts/día: etapa nueva cada 1-2 semanas, planeta 1 completo en ~26 días de
     * entreno (≈2 meses a 3-4/semana).
     */
    val BASE_THRESHOLDS = intArrayOf(0, 10, 110, 210, 330, 460)
    const val BASE_COST = 600

    /** Mínimo/tope del día: por debajo de 3 series no puntúa; por encima de 15 satura. */
    const val MIN_DAY_SETS = 3
    const val MAX_DAY_SETS = 15
    const val DAY_BASE = 10

    /** Estimación por defecto de pts/día antes de tener historial. */
    const val DEFAULT_DAY_XP = 20

    private const val DAY_MS = 86_400_000L

    /** Mínimo de series totales en un grupo muscular para que cuente como especie. */
    private const val SETS_PER_SPECIES = 6
    const val MAX_SPECIES = 12

    /**
     * Multiplicador de coste en PORCENTAJE entero: +20% por planeta, tope ×2. Entero para
     * que cliente (Kotlin) y servidor (TS) calculen exactamente lo mismo.
     */
    fun costPct(planetIndex: Int): Int = minOf(100 + 20 * planetIndex, 200)

    fun planetCost(planetIndex: Int): Int = BASE_COST * costPct(planetIndex) / 100

    fun thresholdsFor(planetIndex: Int): IntArray {
        val pct = costPct(planetIndex)
        return IntArray(BASE_THRESHOLDS.size) { BASE_THRESHOLDS[it] * pct / 100 }
    }

    /** Puntos de un día con [sets] series válidas (completadas, no calentamiento). */
    fun dayXp(sets: Int): Int =
        if (sets < MIN_DAY_SETS) 0 else DAY_BASE + minOf(sets, MAX_DAY_SETS)

    /**
     * Semilla visual reproducible: entropía base (persistida) mezclada con el índice del
     * planeta vía xorshift. int32 para que sobreviva intacta al JSON del backend.
     */
    fun seedFor(baseSeed: Long, planetIndex: Int): Int {
        var h = baseSeed xor (baseSeed ushr 32)
        h = h * 31 + planetIndex
        h = h xor (h shl 13)
        h = h xor (h ushr 7)
        h = h xor (h shl 17)
        return h.toInt()
    }

    /**
     * Deriva el estado del planeta desde el historial.
     *
     * @param baseSeed entropía visual ya persistida; 0 = primera vez (se toma el timestamp
     *   de la primera sesión y el llamador debe guardarla para congelar el aspecto).
     * @param highWaterXp máximo totalXp visto hasta ahora: borrar/editar sesiones nunca
     *   hace regresar el planeta. El llamador persiste el nuevo máximo tras cada cómputo.
     */
    fun compute(
        sessions: List<SessionWithSets>,
        muscleRows: List<SetMuscleRow>,
        baseSeed: Long = 0L,
        highWaterXp: Int = 0
    ): PlanetState {
        // Series válidas agrupadas por día UTC (determinista en cualquier zona horaria).
        val setsPerDay = HashMap<Long, Int>()
        var firstStart = Long.MAX_VALUE
        for (sw in sessions) {
            if (sw.session.startedAt < firstStart) firstStart = sw.session.startedAt
            val day = Math.floorDiv(sw.session.startedAt, DAY_MS)
            val valid = sw.sets.count { it.completed && !it.isWarmup }
            if (valid > 0) setsPerDay[day] = (setsPerDay[day] ?: 0) + valid
        }
        val dayXps = setsPerDay.entries
            .sortedBy { it.key }
            .map { dayXp(it.value) }
            .filter { it > 0 }

        val derived = dayXps.sum()
        val totalXp = maxOf(derived, highWaterXp)
        val effectiveBase = if (baseSeed != 0L) baseSeed
        else if (firstStart != Long.MAX_VALUE) firstStart else 0L

        // Reparte el total en planetas consecutivos: el excedente pasa al siguiente.
        var index = 0
        var remaining = totalXp
        while (remaining >= planetCost(index)) {
            remaining -= planetCost(index)
            index++
        }

        val thresholds = thresholdsFor(index)
        val cost = planetCost(index)
        var stageIdx = 0
        for (k in thresholds.indices) {
            if (remaining >= thresholds[k]) stageIdx = k
        }
        val nextAt = if (stageIdx < thresholds.size - 1) thresholds[stageIdx + 1] else cost

        val species = muscleRows
            .groupingBy { it.primaryMuscle }
            .eachCount()
            .count { it.value >= SETS_PER_SPECIES }
            .coerceIn(if (sessions.isNotEmpty()) 1 else 0, MAX_SPECIES)

        val median = if (dayXps.isEmpty()) DEFAULT_DAY_XP else dayXps.sorted()[dayXps.size / 2]

        return PlanetState(
            planetIndex = index,
            stage = PlanetStage.entries[stageIdx],
            xpInPlanet = remaining,
            planetCost = cost,
            stageStart = thresholds[stageIdx],
            nextStageAt = nextAt,
            totalXp = totalXp,
            species = species,
            seed = seedFor(effectiveBase, index),
            baseSeed = effectiveBase,
            completedSeeds = List(index) { seedFor(effectiveBase, it) },
            lastDayXp = dayXps.lastOrNull() ?: 0,
            medianDayXp = median,
            hasHistory = sessions.isNotEmpty()
        )
    }
}
