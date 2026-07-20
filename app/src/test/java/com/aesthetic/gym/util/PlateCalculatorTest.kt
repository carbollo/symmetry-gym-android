package com.aesthetic.gym.util

import com.aesthetic.gym.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The target is always the weight of the PLATES: no bar is ever discounted, and the total is
 * split evenly across the loading points (2 = barbell sides, 4 = leg press posts).
 */
class PlateCalculatorTest {

    private fun assertExact(totalKg: Double, points: Int, perPoint: List<Double>) {
        val r = PlateCalculator.compute(totalKg, points)
        assertTrue("$totalKg/$points debería ser exacto", r.isExact)
        assertEquals(perPoint, r.best!!.perPoint)
        assertEquals(totalKg, r.best!!.totalKg, 0.0001)
        assertEquals(totalKg / points, r.best!!.perPointKg, 0.0001)
    }

    @Test fun `sin peso no pide discos`() {
        val r = PlateCalculator.compute(0.0, 2)
        assertTrue(r.isExact)
        assertTrue(r.best!!.perPoint.isEmpty())
    }

    @Test fun `100 kg en una barra son 50 por lado`() =
        assertExact(100.0, 2, listOf(25.0, 25.0))

    @Test fun `100 kg en prensa de 4 palos son 25 por palo`() =
        assertExact(100.0, 4, listOf(25.0))

    @Test fun `100 kg en un solo sitio`() =
        assertExact(100.0, 1, listOf(25.0, 25.0, 25.0, 25.0))

    @Test fun `el reparto usa el minimo numero de discos`() =
        assertExact(80.0, 2, listOf(25.0, 15.0)) // no 25+10+5

    @Test fun `discos de kilo y cuarto`() {
        assertExact(2.5, 2, listOf(1.25))
        assertExact(102.5, 2, listOf(25.0, 25.0, 1.25))
        assertExact(62.5, 2, listOf(25.0, 5.0, 1.25))
        assertExact(105.0, 4, listOf(25.0, 1.25))
    }

    @Test fun `cuando no cuadra elige el mas cercano`() {
        // 101 entre 2: abajo 100 (falta 1), arriba 102.5 (sobra 1.5) -> gana abajo
        val r = PlateCalculator.compute(101.0, 2)
        assertFalse(r.isExact)
        assertEquals(100.0, r.best!!.totalKg, 0.0001)
        assertEquals(100.0, r.below!!.totalKg, 0.0001)
        assertEquals(102.5, r.above!!.totalKg, 0.0001)
        assertEquals(-1.0, r.diffKg, 0.0001)
    }

    @Test fun `si sobra menos que lo que falta se pasa`() {
        // 104 entre 2: abajo 102.5 (falta 1.5), arriba 105 (sobra 1) -> gana arriba
        val r = PlateCalculator.compute(104.0, 2)
        assertEquals(105.0, r.best!!.totalKg, 0.0001)
        assertEquals(1.0, r.diffKg, 0.0001)
    }

    @Test fun `en un empate se queda corto`() {
        val r = PlateCalculator.compute(101.25, 2)
        assertFalse(r.isExact)
        assertEquals(100.0, r.best!!.totalKg, 0.0001)
    }

    @Test fun `por debajo del escalon minimo propone el de arriba`() {
        val r = PlateCalculator.compute(1.0, 2)
        assertFalse(r.isExact)
        assertEquals(2.5, r.best!!.totalKg, 0.0001)
        assertEquals(0.0, r.below!!.totalKg, 0.0001)
    }

    @Test fun `el salto depende de los puntos de carga`() {
        assertEquals(1.25, PlateCalculator.compute(50.0, 1).stepUpKg!!, 0.0001)
        assertEquals(2.5, PlateCalculator.compute(50.0, 2).stepUpKg!!, 0.0001)
        assertEquals(5.0, PlateCalculator.compute(50.0, 4).stepUpKg!!, 0.0001)
        assertEquals(7.5, PlateCalculator.compute(50.0, 6).stepUpKg!!, 0.0001)
    }

    @Test fun `reparto entre 3 puntos`() {
        assertExact(97.5, 3, listOf(25.0, 5.0, 2.5))
        val r = PlateCalculator.compute(100.0, 3)
        assertFalse(r.isExact) // 100/3 no es múltiplo de 1.25
        assertEquals(101.25, r.best!!.totalKg, 0.0001) // sobra 1.25 < falta 2.5
    }

    @Test fun `pesos altos`() {
        assertExact(140.0, 2, listOf(25.0, 25.0, 20.0))
        assertExact(180.0, 4, listOf(25.0, 20.0))
        assertExact(200.0, 4, listOf(25.0, 25.0))
        val r = PlateCalculator.compute(500.0, 2)
        assertTrue(r.isExact)
        assertEquals(10, r.best!!.perPoint.size) // 250 por lado = 10 x 25
    }

    @Test fun `entradas absurdas no rompen nada`() {
        assertEquals(0.0, PlateCalculator.compute(-5.0, 2).targetKg, 0.0001)
        assertEquals(1, PlateCalculator.compute(100.0, 0).points)
        assertEquals(8, PlateCalculator.compute(100.0, 99).points)
        assertEquals(2000.0, PlateCalculator.compute(3000.0, 2).targetKg, 0.0001)
        // Lo no finito se sanea a 0, no al tope: es basura, no un peso enorme.
        assertEquals(0.0, PlateCalculator.compute(Double.NaN, 2).targetKg, 0.0001)
        assertEquals(0.0, PlateCalculator.compute(Double.POSITIVE_INFINITY, 2).targetKg, 0.0001)
    }

    @Test fun `agrupa los discos repetidos`() {
        val grouped = PlateCalculator.grouped(listOf(25.0, 25.0, 5.0))
        assertEquals(listOf(25.0 to 2, 5.0 to 1), grouped)
    }

    @Test fun `el disco de 1_25 no se redondea a 1_3`() {
        assertEquals("1.25", formatKg(1.25))
        assertEquals("2.5", formatKg(2.5))
        assertEquals("25", formatKg(25.0))
        assertEquals("31.25", formatKg(31.25))
    }
}

/** El cálculo usa SOLO los discos que el usuario dice tener. */
class PlatePickerTest {

    private val soloCincoDiezVeinte = listOf(5_000, 10_000, 20_000)

    @Test fun `sin discos de 25 recalcula con los que hay`() {
        // 100 kg / 2 lados = 50 por lado -> 20+20+10, no 25+25
        val r = PlateCalculator.compute(100.0, 2, soloCincoDiezVeinte)
        assertTrue(r.isExact)
        assertEquals(listOf(20.0, 20.0, 10.0), r.best!!.perPoint)
    }

    @Test fun `un peso que ya no se puede montar deja de ser exacto`() {
        // 102.5 kg necesitaría 1.25 por lado, y no lo tiene
        val r = PlateCalculator.compute(102.5, 2, soloCincoDiezVeinte)
        assertFalse(r.isExact)
        assertEquals(100.0, r.below!!.totalKg, 0.0001)
        assertEquals(110.0, r.above!!.totalKg, 0.0001)
        assertEquals(100.0, r.best!!.totalKg, 0.0001)
    }

    @Test fun `los saltos se adaptan a los discos disponibles`() {
        // Con el disco más pequeño de 5 kg y 2 lados, el salto del total es 10 kg
        val r = PlateCalculator.compute(100.0, 2, soloCincoDiezVeinte)
        assertEquals(10.0, r.stepUpKg!!, 0.0001)
        assertEquals(10.0, r.stepDownKg!!, 0.0001)
    }

    @Test fun `detecta huecos que un algoritmo voraz no veria`() {
        // Con solo 10 y 25: 15 por lado es IMPOSIBLE aunque 15 sea multiplo de 5
        val soloDiezYVeinticinco = listOf(10_000, 25_000)
        val r = PlateCalculator.compute(30.0, 2, soloDiezYVeinticinco)
        assertFalse("15 kg por lado no se puede montar con 10 y 25", r.isExact)
        assertEquals(20.0, r.below!!.totalKg, 0.0001)  // 10 por lado
        assertEquals(40.0, r.above!!.totalKg, 0.0001)  // 20 por lado
    }

    @Test fun `el disco de 30 se puede activar`() {
        val conTreinta = PlateCalculator.ALL_PLATES_G
        val r = PlateCalculator.compute(120.0, 2, conTreinta)
        assertTrue(r.isExact)
        assertEquals(listOf(30.0, 30.0), r.best!!.perPoint) // 2 discos, no 25+25+10
    }

    @Test fun `sin ningun disco marcado no revienta`() {
        val r = PlateCalculator.compute(100.0, 2, emptyList())
        assertEquals(null, r.best)
        assertEquals(null, r.below)
        assertFalse(r.isExact)
    }

    @Test fun `guardar y releer la seleccion`() {
        assertEquals("5000,10000,20000", PlateCalculator.formatPlates(listOf(20_000, 5_000, 10_000)))
        assertEquals(listOf(5_000, 10_000, 20_000), PlateCalculator.parsePlates("5000,10000,20000"))
        // Basura o vacío -> el juego por defecto, nunca una calculadora inservible
        assertEquals(PlateCalculator.DEFAULT_PLATES_G, PlateCalculator.parsePlates(null))
        assertEquals(PlateCalculator.DEFAULT_PLATES_G, PlateCalculator.parsePlates(""))
        assertEquals(PlateCalculator.DEFAULT_PLATES_G, PlateCalculator.parsePlates("hola,999"))
    }
}

/** Los pesos no se redondean a 1 decimal: 3.75 kg es 3.75, no 3.8. */
class WeightFormatTest {

    @Test fun `mantiene hasta tres decimales`() {
        assertEquals("3.75", formatKg(3.75))
        assertEquals("1.25", formatKg(1.25))
        assertEquals("0.125", formatKg(0.125))
        assertEquals("62.5", formatKg(62.5))
    }

    @Test fun `quita los ceros que no aportan`() {
        assertEquals("60", formatKg(60.0))
        assertEquals("60", formatKg(60.000))
        assertEquals("2.5", formatKg(2.50))
        assertEquals("0", formatKg(0.0))
    }

    @Test fun `negativos y valores altos`() {
        assertEquals("-3.75", formatKg(-3.75))
        assertEquals("2000", formatKg(2000.0))
        assertEquals("1234.5", formatKg(1234.5))
    }

    @Test fun `el campo de peso de una serie muestra lo que se tecleo`() {
        assertEquals("3.75", formatWeightValue(3.75, WeightUnit.KG))
        assertEquals("102.5", formatWeightValue(102.5, WeightUnit.KG))
    }

    @Test fun `en libras sigue convirtiendo`() {
        // 60 kg = 132.277... lb -> se queda en 3 decimales, sin notación científica
        assertEquals("132.277", formatWeightValue(60.0, WeightUnit.LB))
        assertTrue(formatWeight(60.0, WeightUnit.KG).endsWith(WeightUnit.KG.label))
    }
}
