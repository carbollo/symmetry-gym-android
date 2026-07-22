package com.aesthetic.gym.ui.galaxy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.aesthetic.gym.domain.planet.PlanetStage
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Render procedural del planeta, 100% Canvas (sin bitmaps ni librerías). Toda la geometría
 * se genera UNA vez desde la semilla en espacio unitario (disco de radio 1) y se revela por
 * etapas al dibujar; las animaciones solo mueven capas completas o modulan alphas, nunca
 * recrean paths. La misma semilla produce el mismo planeta en cualquier dispositivo: así la
 * galaxia global muestra los mundos de otros exactamente como los ven sus dueños.
 */

// ---- Geometría en espacio unitario ----

internal class Crater(val c: Offset, val r: Float)
internal class Patch(val c: Offset, val r: Float, val color: Color)
internal class Speck(val c: Offset, val color: Color)

internal class Palette(
    val baseLight: Color, val base: Color, val baseDark: Color,
    val rockDark: Color, val rockLight: Color,
    val ocean: Color, val atmo: Color,
    val floraTones: List<Color>
)

internal class PlanetSpec internal constructor(
    private val pal: Palette,
    private val craters: List<Crater>,
    private val fractures: List<Path>,
    private val lavaCracks: List<Path>,
    private val lavaPools: List<Crater>,
    private val lavaColor: Color,
    private val continents: List<Path>,
    private val floraPerContinent: List<List<Patch>>,
    private val cloudPuffs: List<Path>,
    private val fauna: List<Speck>,
    private val cities: List<Offset>,
    private val cityRoutes: List<Pair<Offset, Offset>>
) {
    private val disc = Path().apply { addOval(Rect(-1f, -1f, 1f, 1f)) }

    private val sphereBrush = Brush.radialGradient(
        0f to pal.baseLight, 0.72f to pal.base, 1f to pal.baseDark,
        center = Offset(-0.35f, -0.35f), radius = 1.5f
    )
    private val nightBrush = Brush.radialGradient(
        0.50f to Color.Transparent,
        0.80f to Color(0x590B0B10),
        1.00f to Color(0xD90B0B10),
        center = Offset(-0.55f, -0.55f), radius = 2.2f
    )
    private val atmoBrush = Brush.radialGradient(
        0.80f to Color.Transparent,
        0.90f to pal.atmo.copy(alpha = 0.35f),
        1f to Color.Transparent,
        center = Offset.Zero, radius = 1.16f
    )
    private val glintColor = Color.White.copy(alpha = 0.12f)
    private val cloudColor = Color.White.copy(alpha = 0.50f)

    // Strokes fijos: crearlos por frame dentro de los bucles sería churn de GC gratuito.
    private val craterRim = Stroke(0.012f)
    private val fractureStroke = Stroke(0.008f)
    private val lavaHalo = Stroke(0.05f, cap = StrokeCap.Round)
    private val lavaCore = Stroke(0.014f, cap = StrokeCap.Round)
    private val sealRing = Stroke(0.03f)

    /**
     * Dibuja el planeta centrado en [center] con radio [radiusPx].
     *
     * @param progressQ 0..3: densidad de elementos dentro de la etapa (más grietas, nubes,
     *   flora... a medida que avanzas), sin regenerar geometría.
     * @param completed planeta sellado: anillo dorado y todo al máximo.
     * @param mini modo miniatura: solo las capas que se leen a 40-72dp.
     */
    fun DrawScope.draw(
        center: Offset,
        radiusPx: Float,
        stage: PlanetStage,
        progressQ: Int,
        completed: Boolean,
        mini: Boolean,
        cloudShift: Float,
        lavaPulse: Float,
        twinkle: Float
    ) {
        val q = if (completed) 3 else progressQ.coerceIn(0, 3)
        val s = if (completed) PlanetStage.LUCES else stage
        withTransform({
            translate(center.x, center.y)
            scale(radiusPx, radiusPx, Offset.Zero)
        }) {
            // Esfera base, siempre.
            drawCircle(sphereBrush, radius = 1f, center = Offset.Zero)

            val hasOcean = s.ordinal >= PlanetStage.MAR.ordinal
            clipPath(disc) {
                if (!hasOcean) {
                    // Mundo rocoso: cráteres y fracturas hasta que llega el mar.
                    for (cr in craters) {
                        drawCircle(pal.rockDark, cr.r, cr.c)
                        if (!mini) {
                            drawArc(
                                color = pal.rockLight, startAngle = 135f, sweepAngle = 160f,
                                useCenter = false,
                                topLeft = cr.c - Offset(cr.r, cr.r), size = Size(cr.r * 2, cr.r * 2),
                                style = craterRim
                            )
                        }
                    }
                    if (!mini) {
                        for (f in fractures) {
                            drawPath(f, pal.rockDark.copy(alpha = 0.25f), style = fractureStroke)
                        }
                    }
                } else {
                    // Océano y continentes sustituyen a los cráteres.
                    drawCircle(pal.ocean, radius = 1f, center = Offset.Zero)
                    for (land in continents) drawPath(land, pal.base)
                    if (!mini) {
                        drawOval(glintColor, topLeft = Offset(-0.55f, -0.5f), size = Size(0.5f, 0.25f))
                    }
                }

                // Grietas de lava del despertar (solo mientras el mundo sigue seco).
                if (s == PlanetStage.PULSO || s == PlanetStage.ALIENTO) {
                    val visible = (2 + q).coerceAtMost(lavaCracks.size)
                    for (i in 0 until visible) {
                        val crack = lavaCracks[i]
                        drawPath(crack, lavaColor.copy(alpha = 0.22f * lavaPulse), style = lavaHalo)
                        drawPath(crack, lavaColor.copy(alpha = lavaPulse), style = lavaCore)
                    }
                    if (!mini) {
                        for (pool in lavaPools) {
                            drawCircle(lavaColor.copy(alpha = 0.55f * lavaPulse), pool.r, pool.c)
                        }
                    }
                }

                // Flora: parches recortados a su continente.
                if (s.ordinal >= PlanetStage.VERDE.ordinal) {
                    val perLand = (6 + 3 * q)
                    for (k in continents.indices) {
                        clipPath(continents[k]) {
                            val patches = floraPerContinent[k]
                            for (i in 0 until minOf(perLand, patches.size)) {
                                val p = patches[i]
                                drawCircle(p.color, p.r, p.c)
                            }
                        }
                    }
                }

                // Nubes: banda que se desplaza y envuelve. Tres copias: con dos, el tramo
                // final del ciclo dejaba la mitad oeste sin nubes y el wrap "popeaba".
                if (s.ordinal >= PlanetStage.ALIENTO.ordinal) {
                    val visible = (3 + 2 * q).coerceAtMost(cloudPuffs.size)
                    val w = 2.4f
                    val dx = cloudShift * w
                    withTransform({ translate(dx - 2 * w, 0f) }) {
                        for (i in 0 until visible) drawPath(cloudPuffs[i], cloudColor)
                    }
                    withTransform({ translate(dx - w, 0f) }) {
                        for (i in 0 until visible) drawPath(cloudPuffs[i], cloudColor)
                    }
                    withTransform({ translate(dx, 0f) }) {
                        for (i in 0 until visible) drawPath(cloudPuffs[i], cloudColor)
                    }
                }

                // Terminador: el lado nocturno, con el propio color de fondo de la app.
                drawCircle(nightBrush, radius = 1f, center = Offset.Zero)

                // Fauna: bioluminiscencia costera, DESPUÉS del terminador para brillar de noche.
                if (s.ordinal >= PlanetStage.LATIDO.ordinal && !mini) {
                    for (sp in fauna) drawCircle(sp.color.copy(alpha = 0.9f), 0.012f, sp.c)
                }

                // Ciudades: puntos dorados que titilan en contrafase, también sobre la noche.
                if (s.ordinal >= PlanetStage.LUCES.ordinal) {
                    val visible = if (completed) cities.size
                    else (6 * (1 + q)).coerceAtMost(cities.size)
                    val gold = Color(0xFFFFD87A)
                    val twB = 1.55f - twinkle
                    for (i in 0 until visible) {
                        val a = 0.9f * (if (i % 2 == 0) twinkle else twB)
                        drawCircle(gold.copy(alpha = a), if (mini) 0.02f else 0.014f, cities[i])
                    }
                    if (!mini) {
                        for ((a, b) in cityRoutes) {
                            drawLine(gold.copy(alpha = 0.15f), a, b, strokeWidth = 0.006f)
                        }
                    }
                }
            }

            // Halo atmosférico exterior (fuera del disco, sin clip).
            if (s.ordinal >= PlanetStage.ALIENTO.ordinal) {
                drawCircle(atmoBrush, radius = 1.16f, center = Offset.Zero)
            }

            // Mundo sellado: anillo dorado de la galaxia privada.
            if (completed) {
                drawCircle(
                    Color(0xFFFFD87A).copy(alpha = 0.6f), radius = 1.08f, center = Offset.Zero,
                    style = sealRing
                )
            }
        }
    }
}

// ---- Generación de la spec desde la semilla ----

private fun Random.range(from: Float, to: Float): Float = from + nextFloat() * (to - from)

private fun Random.hsv(h1: Float, h2: Float, s1: Float, s2: Float, v1: Float, v2: Float): Color =
    Color.hsv(range(h1, h2).mod(360f), range(s1, s2), range(v1, v2))

private fun polar(r: Float, angle: Float) = Offset(r * cos(angle), r * sin(angle))

/**
 * Acota RADIALMENTE al disco visible: los vértices de continentes pueden salirse (se clipan),
 * pero un punto de luz fuera del disco simplemente desaparecería. Acotar por eje (cuadrado)
 * no vale: (0.8, 0.8) pasa la coerción y sigue a distancia 1.13 del centro.
 */
private fun clampToDisc(v: Offset, max: Float = 0.92f): Offset {
    val d = sqrt(v.x * v.x + v.y * v.y)
    return if (d <= max || d == 0f) v else Offset(v.x * max / d, v.y * max / d)
}

private const val TAU = (Math.PI * 2).toFloat()

/** Blob orgánico: polígono irregular suavizado con curvas por los puntos medios. */
private fun blob(rnd: Random, c: Offset, r0: Float): Pair<Path, List<Offset>> {
    val n = 9 + rnd.nextInt(4)
    val pts = List(n) { i -> c + polar(r0 * (0.65f + 0.7f * rnd.nextFloat()), i * TAU / n) }
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
    val path = Path().apply {
        val m0 = mid(pts[0], pts[1])
        moveTo(m0.x, m0.y)
        for (i in 1..n) {
            val p = pts[i % n]
            val m = mid(p, pts[(i + 1) % n])
            quadraticBezierTo(p.x, p.y, m.x, m.y)
        }
        close()
    }
    return path to pts
}

/** Polilínea con jitter: fracturas y grietas de lava. */
private fun crackPath(rnd: Random, from: Offset, segments: Int): Path {
    var dir = polar(1f, rnd.nextFloat() * TAU)
    var p = from
    return Path().apply {
        moveTo(p.x, p.y)
        repeat(segments) {
            val normal = Offset(-dir.y, dir.x)
            p += dir * 0.16f + normal * rnd.range(-0.06f, 0.06f)
            lineTo(p.x, p.y)
        }
    }
}

private fun buildPalette(rnd: Random): Palette {
    fun tones(h1: Float, h2: Float, s1: Float, s2: Float, v1: Float, v2: Float, n: Int) =
        List(n) { rnd.hsv(h1, h2, s1, s2, v1, v2) }
    // 5 arquetipos con HSV acotado: nunca colores libres que desentonen con el fondo #0B0B10.
    return when (rnd.nextInt(5)) {
        0 -> Palette( // Terran
            baseLight = rnd.hsv(25f, 40f, 0.15f, 0.30f, 0.42f, 0.50f),
            base = rnd.hsv(25f, 40f, 0.15f, 0.30f, 0.32f, 0.40f),
            baseDark = rnd.hsv(25f, 40f, 0.18f, 0.32f, 0.20f, 0.28f),
            rockDark = rnd.hsv(25f, 40f, 0.15f, 0.28f, 0.16f, 0.24f),
            rockLight = rnd.hsv(25f, 40f, 0.10f, 0.22f, 0.50f, 0.60f),
            ocean = rnd.hsv(200f, 220f, 0.60f, 0.75f, 0.38f, 0.50f),
            atmo = rnd.hsv(195f, 210f, 0.55f, 0.70f, 0.60f, 0.75f),
            floraTones = tones(95f, 130f, 0.45f, 0.65f, 0.35f, 0.50f, 4)
        )
        1 -> Palette( // Árido
            baseLight = rnd.hsv(15f, 35f, 0.35f, 0.55f, 0.48f, 0.55f),
            base = rnd.hsv(15f, 35f, 0.35f, 0.55f, 0.40f, 0.48f),
            baseDark = rnd.hsv(15f, 35f, 0.38f, 0.55f, 0.26f, 0.34f),
            rockDark = rnd.hsv(15f, 35f, 0.35f, 0.50f, 0.20f, 0.28f),
            rockLight = rnd.hsv(15f, 35f, 0.25f, 0.40f, 0.55f, 0.65f),
            ocean = rnd.hsv(185f, 200f, 0.40f, 0.55f, 0.35f, 0.45f),
            atmo = rnd.hsv(20f, 40f, 0.40f, 0.55f, 0.60f, 0.70f),
            floraTones = tones(70f, 90f, 0.40f, 0.60f, 0.35f, 0.48f, 4)
        )
        2 -> Palette( // Hielo
            baseLight = rnd.hsv(210f, 230f, 0.05f, 0.15f, 0.62f, 0.70f),
            base = rnd.hsv(210f, 230f, 0.05f, 0.15f, 0.50f, 0.60f),
            baseDark = rnd.hsv(210f, 230f, 0.08f, 0.18f, 0.34f, 0.42f),
            rockDark = rnd.hsv(210f, 230f, 0.08f, 0.18f, 0.28f, 0.36f),
            rockLight = rnd.hsv(210f, 230f, 0.02f, 0.10f, 0.72f, 0.82f),
            ocean = rnd.hsv(195f, 215f, 0.40f, 0.55f, 0.45f, 0.55f),
            atmo = rnd.hsv(190f, 210f, 0.30f, 0.45f, 0.70f, 0.80f),
            floraTones = tones(160f, 180f, 0.40f, 0.60f, 0.40f, 0.52f, 4)
        )
        3 -> Palette( // Exo-violeta (marca de la app)
            baseLight = rnd.hsv(260f, 280f, 0.10f, 0.25f, 0.36f, 0.44f),
            base = rnd.hsv(260f, 280f, 0.10f, 0.25f, 0.28f, 0.36f),
            baseDark = rnd.hsv(260f, 280f, 0.14f, 0.28f, 0.18f, 0.26f),
            rockDark = rnd.hsv(260f, 280f, 0.12f, 0.25f, 0.14f, 0.22f),
            rockLight = rnd.hsv(260f, 280f, 0.08f, 0.18f, 0.46f, 0.56f),
            ocean = rnd.hsv(245f, 265f, 0.50f, 0.70f, 0.38f, 0.48f),
            atmo = Color(0xFF7C5CFF),
            floraTones = tones(300f, 330f, 0.45f, 0.60f, 0.40f, 0.50f, 4)
        )
        else -> Palette( // Esmeralda
            baseLight = rnd.hsv(85f, 100f, 0.10f, 0.25f, 0.42f, 0.48f),
            base = rnd.hsv(85f, 100f, 0.10f, 0.25f, 0.34f, 0.42f),
            baseDark = rnd.hsv(85f, 100f, 0.14f, 0.28f, 0.22f, 0.30f),
            rockDark = rnd.hsv(85f, 100f, 0.12f, 0.25f, 0.18f, 0.26f),
            rockLight = rnd.hsv(85f, 100f, 0.06f, 0.18f, 0.50f, 0.60f),
            ocean = rnd.hsv(170f, 190f, 0.55f, 0.70f, 0.40f, 0.50f),
            atmo = rnd.hsv(150f, 175f, 0.45f, 0.60f, 0.60f, 0.72f),
            floraTones = tones(120f, 150f, 0.55f, 0.70f, 0.40f, 0.52f, 4)
        )
    }
}

/** Acentos fijos de la app para la fauna (lima, cian, dorado). */
private val FAUNA_ACCENTS = listOf(Color(0xFFC6FF4D), Color(0xFF4DE3FF), Color(0xFFFFD87A))

internal fun buildPlanetSpec(seed: Int, species: Int): PlanetSpec {
    val rnd = Random(seed)
    val pal = buildPalette(rnd)

    val craters = List(5 + rnd.nextInt(4)) {
        Crater(
            polar(0.85f * sqrt(rnd.nextFloat()), rnd.nextFloat() * TAU),
            rnd.range(0.04f, 0.12f)
        )
    }
    val fractures = List(2 + rnd.nextInt(2)) {
        crackPath(rnd, polar(0.5f * rnd.nextFloat(), rnd.nextFloat() * TAU), 5)
    }
    val lavaCracks = List(5) {
        crackPath(rnd, polar(0.45f * rnd.nextFloat(), rnd.nextFloat() * TAU), 5)
    }
    val lavaPools = List(2 + rnd.nextInt(2)) {
        Crater(polar(0.7f * sqrt(rnd.nextFloat()), rnd.nextFloat() * TAU), rnd.range(0.05f, 0.09f))
    }
    val lavaColor = rnd.hsv(10f, 25f, 0.85f, 0.95f, 0.95f, 1f)

    val landCount = 3 + rnd.nextInt(3)
    val lands = List(landCount) {
        blob(rnd, polar(sqrt(rnd.nextFloat()) * 0.62f, rnd.nextFloat() * TAU), rnd.range(0.18f, 0.38f))
    }
    val continents = lands.map { it.first }
    val continentVerts = lands.map { it.second }

    // Nubes: banda de puffs en x ∈ [0, 2.4] que luego se desplaza con wrap.
    val cloudPuffs = List(7) {
        val cx = rnd.range(0f, 2.4f)
        val cy = rnd.range(-0.72f, 0.72f)
        Path().apply {
            repeat(3 + rnd.nextInt(3)) {
                val ox = cx + rnd.range(-0.16f, 0.16f)
                val oy = cy + rnd.range(-0.05f, 0.05f)
                addOval(Rect(ox - rnd.range(0.10f, 0.2f), oy - 0.05f, ox + rnd.range(0.10f, 0.2f), oy + 0.05f))
            }
        }
    }

    // Flora: tonos según diversidad de especies (más grupos musculares = más variedad).
    val floraTones = pal.floraTones.take((species / 3 + 1).coerceIn(1, 4))
    val floraPerContinent = continentVerts.mapIndexed { k, verts ->
        val c = verts.fold(Offset.Zero) { acc, p -> acc + p } / verts.size.toFloat()
        List(15) {
            Patch(
                c + polar(rnd.range(0f, 0.30f), rnd.nextFloat() * TAU),
                rnd.range(0.03f, 0.08f),
                floraTones[rnd.nextInt(floraTones.size)]
            )
        }
    }

    // Fauna y ciudades usan streams de Random INDEPENDIENTES: si dependieran del stream
    // principal, un cambio de species (que varía cuántas extracciones se hacen antes)
    // recolocaría las luces de un mundo "congelado". Con streams por capa, más species
    // solo AÑADE specks al final (prefijo estable) y las ciudades nunca se mueven.
    val faunaCount = (4 * species).coerceIn(4, 36)
    val allVerts = continentVerts.flatten()
    val rndFauna = Random(seed * 31 + 7)
    val fauna = List(faunaCount) { i ->
        val v = allVerts[rndFauna.nextInt(allVerts.size)]
        Speck(
            clampToDisc(v + polar(rndFauna.range(0.01f, 0.04f), rndFauna.nextFloat() * TAU)),
            FAUNA_ACCENTS[i % FAUNA_ACCENTS.size]
        )
    }

    // Ciudades: vértices con bias al lado nocturno (abajo-derecha) para que luzcan.
    val rndCities = Random(seed * 131 + 17)
    val cityCandidates = allVerts.filter { it.x > -0.2f }.ifEmpty { allVerts }
    val cities = List(24) {
        val v = cityCandidates[rndCities.nextInt(cityCandidates.size)]
        clampToDisc(v + polar(rndCities.range(0f, 0.05f), rndCities.nextFloat() * TAU))
    }
    val cityRoutes = List(5) {
        cities[rndCities.nextInt(cities.size)] to cities[rndCities.nextInt(cities.size)]
    }

    return PlanetSpec(
        pal, craters, fractures, lavaCracks, lavaPools, lavaColor,
        continents, floraPerContinent, cloudPuffs, fauna, cities, cityRoutes
    )
}

// ---- Composable público ----

/**
 * El planeta, listo para usar en cualquier tamaño. La spec se cachea por (seed, species);
 * las animaciones solo se crean si [animated] y sus State se leen únicamente en fase de
 * draw (repintado sin recomposición).
 */
@Composable
fun PlanetCanvas(
    seed: Int,
    stage: PlanetStage,
    progressQ: Int,
    species: Int,
    modifier: Modifier = Modifier,
    completed: Boolean = false,
    animated: Boolean = true,
    mini: Boolean = false
) {
    val spec = remember(seed, species) { buildPlanetSpec(seed, species) }

    // En Génesis no hay nada que se mueva (ni lava, ni nubes, ni luces): sin transición,
    // el canvas no se invalida a 60fps para repintar un planeta estático.
    val hasMotion = animated && (completed || stage != PlanetStage.GENESIS)

    val cloudShift: State<Float>
    val lavaPulse: State<Float>
    val twinkle: State<Float>
    if (hasMotion) {
        val t = rememberInfiniteTransition(label = "planet")
        cloudShift = t.animateFloat(
            0f, 1f, infiniteRepeatable(tween(80_000, easing = LinearEasing)), label = "clouds"
        )
        lavaPulse = t.animateFloat(
            0.7f, 1f, infiniteRepeatable(tween(2_800), RepeatMode.Reverse), label = "lava"
        )
        twinkle = t.animateFloat(
            0.55f, 1f, infiniteRepeatable(tween(2_600), RepeatMode.Reverse), label = "cities"
        )
    } else {
        cloudShift = remember { mutableFloatStateOf(0.3f) }
        lavaPulse = remember { mutableFloatStateOf(0.85f) }
        twinkle = remember { mutableFloatStateOf(0.8f) }
    }

    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.86f // margen para el halo atmosférico
        with(spec) {
            draw(
                center = Offset(size.width / 2f, size.height / 2f),
                radiusPx = r,
                stage = stage,
                progressQ = progressQ,
                completed = completed,
                mini = mini,
                cloudShift = cloudShift.value,
                lavaPulse = lavaPulse.value,
                twinkle = twinkle.value
            )
        }
    }
}
