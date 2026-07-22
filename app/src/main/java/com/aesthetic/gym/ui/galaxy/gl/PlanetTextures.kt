package com.aesthetic.gym.ui.galaxy.gl

import android.graphics.Bitmap
import android.graphics.Color
import com.aesthetic.gym.domain.planet.PlanetStage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Texturas procedurales del planeta 3D, deterministas por semilla: ruido de valor 3D
 * muestreado SOBRE la esfera (equirrectangular sin costuras) → misma semilla, mismo mundo
 * en cualquier dispositivo, igual que en el render 2D. Se generan en CPU fuera del hilo
 * principal y se suben a GL como bitmaps.
 *
 * Mapa de día: RGB = color, A = máscara especular (el océano brilla, la roca no).
 * Mapa de noche: emisivo (lava incandescente en el despertar; ciudades si hay Luces).
 */

internal class SeededNoise(seed: Int) {
    private val perm = IntArray(512)

    init {
        val p = (0..255).toMutableList()
        p.shuffle(Random(seed))
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    private fun v(xi: Int, yi: Int, zi: Int): Float =
        perm[(xi and 255) + perm[(yi and 255) + perm[zi and 255]]] / 255f

    private fun fade(t: Float) = t * t * (3f - 2f * t)

    fun noise(x: Float, y: Float, z: Float): Float {
        val xi = kotlin.math.floor(x).toInt(); val xf = x - xi
        val yi = kotlin.math.floor(y).toInt(); val yf = y - yi
        val zi = kotlin.math.floor(z).toInt(); val zf = z - zi
        val u = fade(xf); val w = fade(yf); val s = fade(zf)
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        val c000 = v(xi, yi, zi); val c100 = v(xi + 1, yi, zi)
        val c010 = v(xi, yi + 1, zi); val c110 = v(xi + 1, yi + 1, zi)
        val c001 = v(xi, yi, zi + 1); val c101 = v(xi + 1, yi, zi + 1)
        val c011 = v(xi, yi + 1, zi + 1); val c111 = v(xi + 1, yi + 1, zi + 1)
        return lerp(
            lerp(lerp(c000, c100, u), lerp(c010, c110, u), w),
            lerp(lerp(c001, c101, u), lerp(c011, c111, u), w),
            s
        )
    }

    /** Ruido fractal 0..1. */
    fun fbm(x: Float, y: Float, z: Float, octaves: Int = 4): Float {
        var sum = 0f; var amp = 0.5f; var f = 1f; var norm = 0f
        repeat(octaves) {
            sum += noise(x * f, y * f, z * f) * amp
            norm += amp; amp *= 0.5f; f *= 2.03f
        }
        return sum / norm
    }

    /** Crestas (para grietas de lava): 1 en los "filos" del ruido. */
    fun ridge(x: Float, y: Float, z: Float): Float = 1f - abs(2f * fbm(x, y, z, 3) - 1f)
}

internal class Palette3D(
    val rockLow: FloatArray, val rockHigh: FloatArray, val sand: FloatArray,
    val oceanDeep: FloatArray, val oceanShallow: FloatArray,
    val floraA: FloatArray, val floraB: FloatArray,
    val atmo: FloatArray, val lava: FloatArray
)

private fun hsv(h: Float, s: Float, v: Float): FloatArray {
    val c = Color.HSVToColor(floatArrayOf(h.mod(360f), s, v))
    return floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)
}

private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

/** Mismos 5 arquetipos que el render 2D (primera extracción del Random = arquetipo). */
internal fun buildPalette3D(seed: Int): Palette3D {
    val rnd = Random(seed)
    return when (rnd.nextInt(5)) {
        0 -> Palette3D( // Terran
            rockLow = hsv(rnd.range(25f, 40f), 0.30f, 0.22f),
            rockHigh = hsv(rnd.range(25f, 40f), 0.22f, 0.52f),
            sand = hsv(rnd.range(35f, 48f), 0.32f, 0.62f),
            oceanDeep = hsv(rnd.range(205f, 220f), 0.75f, 0.30f),
            oceanShallow = hsv(rnd.range(195f, 210f), 0.60f, 0.55f),
            floraA = hsv(rnd.range(95f, 130f), 0.55f, 0.35f),
            floraB = hsv(rnd.range(95f, 130f), 0.62f, 0.50f),
            atmo = hsv(rnd.range(195f, 210f), 0.62f, 0.80f),
            lava = hsv(rnd.range(12f, 25f), 0.92f, 1f)
        )
        1 -> Palette3D( // Árido
            rockLow = hsv(rnd.range(15f, 35f), 0.50f, 0.26f),
            rockHigh = hsv(rnd.range(15f, 35f), 0.45f, 0.56f),
            sand = hsv(rnd.range(28f, 42f), 0.48f, 0.66f),
            oceanDeep = hsv(rnd.range(185f, 200f), 0.55f, 0.28f),
            oceanShallow = hsv(rnd.range(180f, 195f), 0.45f, 0.50f),
            floraA = hsv(rnd.range(70f, 90f), 0.50f, 0.34f),
            floraB = hsv(rnd.range(70f, 90f), 0.55f, 0.46f),
            atmo = hsv(rnd.range(20f, 40f), 0.50f, 0.72f),
            lava = hsv(rnd.range(10f, 22f), 0.95f, 1f)
        )
        2 -> Palette3D( // Hielo
            rockLow = hsv(rnd.range(210f, 230f), 0.16f, 0.32f),
            rockHigh = hsv(rnd.range(210f, 230f), 0.08f, 0.68f),
            sand = hsv(rnd.range(205f, 225f), 0.10f, 0.75f),
            oceanDeep = hsv(rnd.range(200f, 215f), 0.55f, 0.32f),
            oceanShallow = hsv(rnd.range(190f, 210f), 0.42f, 0.58f),
            floraA = hsv(rnd.range(160f, 180f), 0.50f, 0.38f),
            floraB = hsv(rnd.range(160f, 180f), 0.55f, 0.52f),
            atmo = hsv(rnd.range(190f, 210f), 0.40f, 0.85f),
            lava = hsv(rnd.range(15f, 25f), 0.90f, 1f)
        )
        3 -> Palette3D( // Exo-violeta
            rockLow = hsv(rnd.range(260f, 280f), 0.24f, 0.20f),
            rockHigh = hsv(rnd.range(260f, 280f), 0.16f, 0.48f),
            sand = hsv(rnd.range(270f, 295f), 0.22f, 0.58f),
            oceanDeep = hsv(rnd.range(248f, 265f), 0.68f, 0.30f),
            oceanShallow = hsv(rnd.range(245f, 262f), 0.55f, 0.52f),
            floraA = hsv(rnd.range(300f, 330f), 0.52f, 0.38f),
            floraB = hsv(rnd.range(300f, 330f), 0.58f, 0.52f),
            atmo = floatArrayOf(0.49f, 0.36f, 1f), // violeta de marca #7C5CFF
            lava = hsv(rnd.range(10f, 22f), 0.95f, 1f)
        )
        else -> Palette3D( // Esmeralda
            rockLow = hsv(rnd.range(85f, 100f), 0.22f, 0.22f),
            rockHigh = hsv(rnd.range(85f, 100f), 0.16f, 0.48f),
            sand = hsv(rnd.range(75f, 95f), 0.24f, 0.58f),
            oceanDeep = hsv(rnd.range(172f, 190f), 0.68f, 0.28f),
            oceanShallow = hsv(rnd.range(168f, 186f), 0.55f, 0.52f),
            floraA = hsv(rnd.range(120f, 150f), 0.60f, 0.36f),
            floraB = hsv(rnd.range(120f, 150f), 0.66f, 0.50f),
            atmo = hsv(rnd.range(150f, 175f), 0.50f, 0.80f),
            lava = hsv(rnd.range(12f, 24f), 0.92f, 1f)
        )
    }
}

internal class PlanetTextureSet(
    val day: Bitmap,
    val night: Bitmap,
    val clouds: Bitmap?,
    val atmo: FloatArray,
    val hasAtmo: Boolean,
    val hasNight: Boolean
)

private fun lerp(a: FloatArray, b: FloatArray, t: Float): FloatArray {
    val tt = t.coerceIn(0f, 1f)
    return floatArrayOf(
        a[0] + (b[0] - a[0]) * tt, a[1] + (b[1] - a[1]) * tt, a[2] + (b[2] - a[2]) * tt
    )
}

private fun pack(rgb: FloatArray, a: Int): Int {
    val r = (rgb[0].coerceIn(0f, 1f) * 255).toInt()
    val g = (rgb[1].coerceIn(0f, 1f) * 255).toInt()
    val b = (rgb[2].coerceIn(0f, 1f) * 255).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Genera el juego completo de texturas para (seed, etapa, progreso, especies).
 * [width] par: 512 para el héroe, 128 para los planetas del mapa. Alto = width/2.
 */
internal fun generatePlanetTextures(
    seed: Int,
    stage: PlanetStage,
    progressQ: Int,
    species: Int,
    completed: Boolean,
    width: Int = 512
): PlanetTextureSet {
    val s = if (completed) PlanetStage.LUCES else stage
    val q = (if (completed) 3 else progressQ).coerceIn(0, 3)
    val w = width
    val h = width / 2
    val pal = buildPalette3D(seed)
    val elevN = SeededNoise(seed)
    val moistN = SeededNoise(seed * 31 + 7)
    val detailN = SeededNoise(seed * 131 + 17)
    val cityN = SeededNoise(seed * 7 + 101)

    val hasOcean = s.ordinal >= PlanetStage.MAR.ordinal
    val hasFlora = s.ordinal >= PlanetStage.VERDE.ordinal
    val hasLava = s == PlanetStage.PULSO || s == PlanetStage.ALIENTO
    val hasNightCities = s.ordinal >= PlanetStage.LUCES.ordinal
    val hasAtmo = s.ordinal >= PlanetStage.ALIENTO.ordinal
    val lavaStrength = if (s == PlanetStage.PULSO) 0.55f + 0.15f * q else 0.35f
    val floraCover = if (hasFlora) 0.34f + 0.10f * q + 0.015f * species +
        (if (s.ordinal >= PlanetStage.LATIDO.ordinal) 0.08f else 0f) else 0f

    val dayPx = IntArray(w * h)
    val nightPx = IntArray(w * h)
    val sea = 0.52f

    for (y in 0 until h) {
        val lat = (y.toFloat() / h - 0.5f) * Math.PI.toFloat() // -π/2..π/2
        val cosLat = cos(lat); val sinLat = sin(lat)
        val latFrac = abs(y.toFloat() / h - 0.5f) * 2f // 0 ecuador → 1 polos
        for (x in 0 until w) {
            val lon = x.toFloat() / w * 2f * Math.PI.toFloat()
            // Punto sobre la esfera: garantiza que la textura no tenga costura este-oeste.
            val px = cosLat * cos(lon); val py = sinLat; val pz = cosLat * sin(lon)

            val e = elevN.fbm(px * 2.3f, py * 2.3f, pz * 2.3f)
            val d = detailN.fbm(px * 7f, py * 7f, pz * 7f, 3)
            var rgb: FloatArray
            var specA = 8
            var emiss: FloatArray? = null

            if (!hasOcean) {
                // Mundo seco: roca por altura con grano fino.
                rgb = lerp(pal.rockLow, pal.rockHigh, e * 0.8f + d * 0.2f)
                if (hasLava) {
                    val r = elevN.ridge(px * 3.1f + 11f, py * 3.1f, pz * 3.1f)
                    val crack = smooth(0.90f - 0.015f * q, 0.975f, r) * lavaStrength
                    if (crack > 0.01f) {
                        rgb = lerp(rgb, pal.lava, crack)
                        emiss = floatArrayOf(
                            pal.lava[0] * crack, pal.lava[1] * crack * 0.6f, pal.lava[2] * crack * 0.25f
                        )
                    }
                }
            } else {
                val ice = latFrac > 0.80f + 0.06f * (d - 0.5f)
                if (e < sea) {
                    val depth = ((sea - e) / sea * 2.6f).coerceIn(0f, 1f)
                    rgb = lerp(pal.oceanShallow, pal.oceanDeep, depth)
                    specA = 235
                    if (ice) { rgb = lerp(rgb, floatArrayOf(0.92f, 0.95f, 0.98f), 0.85f); specA = 90 }
                } else {
                    val hgt = ((e - sea) / (1f - sea)).coerceIn(0f, 1f)
                    val m = moistN.fbm(px * 3.7f, py * 3.7f, pz * 3.7f)
                    rgb = lerp(pal.sand, pal.rockHigh, smooth(0.15f, 0.85f, hgt))
                    if (hgt > 0.62f) rgb = lerp(rgb, pal.rockLow, smooth(0.62f, 1f, hgt))
                    // Vegetación: prospera en tierras bajas y húmedas; crece con la etapa.
                    val veg = smooth(floraCover + 0.12f, floraCover - 0.12f, m * 0.65f + hgt * 0.35f)
                    if (veg > 0f && floraCover > 0f) {
                        rgb = lerp(rgb, lerp(pal.floraA, pal.floraB, d), veg * 0.9f)
                    }
                    if (ice) rgb = lerp(rgb, floatArrayOf(0.93f, 0.95f, 0.97f), 0.9f)
                    // Luces de ciudad: tierras bajas, clusters de ruido fino.
                    if (hasNightCities && hgt < 0.4f && !ice) {
                        val c = cityN.fbm(px * 9f, py * 9f, pz * 9f, 3)
                        val lit = smooth(0.70f, 0.86f, c)
                        if (lit > 0f) emiss = floatArrayOf(1f * lit, 0.83f * lit, 0.45f * lit)
                    }
                }
            }

            val i = y * w + x
            dayPx[i] = pack(rgb, specA)
            nightPx[i] = emiss?.let { pack(it, 255) } ?: -0x1000000 // negro opaco
        }
    }

    val day = Bitmap.createBitmap(dayPx, w, h, Bitmap.Config.ARGB_8888)
    val night = Bitmap.createBitmap(nightPx, w, h, Bitmap.Config.ARGB_8888)

    val clouds = if (hasAtmo) {
        val cw = (w / 2).coerceAtLeast(128); val ch = cw / 2
        val cn = SeededNoise(seed * 17 + 3)
        val px2 = IntArray(cw * ch)
        for (y in 0 until ch) {
            val lat = (y.toFloat() / ch - 0.5f) * Math.PI.toFloat()
            val cosLat = cos(lat); val sinLat = sin(lat)
            for (x in 0 until cw) {
                val lon = x.toFloat() / cw * 2f * Math.PI.toFloat()
                val pxs = cosLat * cos(lon); val pys = sinLat; val pzs = cosLat * sin(lon)
                val n = cn.fbm(pxs * 3f, pys * 3f, pzs * 3f, 4)
                val a = (smooth(0.55f - 0.03f * q, 0.74f, n) * 235).toInt()
                px2[y * cw + x] = (a shl 24) or 0xFFFFFF
            }
        }
        Bitmap.createBitmap(px2, cw, ch, Bitmap.Config.ARGB_8888)
    } else null

    return PlanetTextureSet(
        day = day,
        night = night,
        clouds = clouds,
        atmo = pal.atmo,
        hasAtmo = hasAtmo,
        hasNight = hasNightCities || hasLava
    )
}
