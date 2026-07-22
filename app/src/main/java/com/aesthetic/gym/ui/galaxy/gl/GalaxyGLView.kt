package com.aesthetic.gym.ui.galaxy.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.aesthetic.gym.domain.planet.PlanetStage
import com.aesthetic.gym.social.GalaxyPlanet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mapa 3D de la galaxia global: una espiral de polvo y estrellas con el planeta de cada
 * usuario colocado de forma estable (hash del username → misma posición siempre). Arrastra
 * para orbitar, pellizca para acercarte, toca un planeta para volar hasta él.
 * Las texturas de cada mundo se generan en segundo plano (128px) y aparecen al estar listas.
 */
@SuppressLint("ViewConstructor")
internal class GalaxyGLView(context: Context) : GLSurfaceView(context) {

    val renderer = GalaxyRenderer()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Aviso a Compose de qué planeta quedó seleccionado (null = ninguno). */
    var onSelected: ((GalaxyPlanet?) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                renderer.zoom(d.scaleFactor)
                return true
            }
        })

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
            ): Boolean {
                renderer.orbit(dx / width, dy / height)
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val picked = renderer.pick(e.x, e.y, resources.displayMetrics.density)
                mainHandler.post { onSelected?.invoke(picked) }
                return true
            }
        })

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }
}

internal class GalaxyRenderer : GLSurfaceView.Renderer {

    internal class MapPlanet(
        val data: GalaxyPlanet,
        val pos: FloatArray,
        val scale: Float,
        @Volatile var tex: PlanetGlTex? = null,
        @Volatile var pending: PlanetTextureSet? = null,
        val base: FloatArray,
        val phase: Float
    )

    private var model: PlanetModel? = null
    private var stars: StarField? = null
    private var dust: SpiralDust? = null
    private var markers: StarFieldRaw? = null

    @Volatile private var planets: List<MapPlanet> = emptyList()
    @Volatile private var texturesStarted = false
    @Volatile private var markersDirty = false

    // Cámara orbital. "Home" = encuadre que contiene todos los mundos publicados.
    private var azimuth = 0.6f
    private var elevation = 0.55f
    private var distance = 16f
    private val target = floatArrayOf(0f, 0f, 0f)
    private val targetGoal = floatArrayOf(0f, 0f, 0f)
    private var distanceGoal = 16f
    private val homeTarget = floatArrayOf(0f, 0f, 0f)
    private var homeDistance = 16f

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val m = FloatArray(16)
    private val mvp = FloatArray(16)
    private val cloudM = FloatArray(16)
    private val cloudMvp = FloatArray(16)
    private val camPos = FloatArray(3)
    private var vpW = 1
    private var vpH = 1

    companion object {
        const val R_MAX = 11f
    }

    /** Posición estable en la espiral a partir del username (no cambia entre visitas). */
    private fun positionFor(username: String): FloatArray {
        val h = username.hashCode()
        val a = ((h and 0xFFFF) / 65535f)
        val b = (((h ushr 16) and 0x7FFF) / 32767f)
        val arm = if ((h ushr 31) == 0) 0f else Math.PI.toFloat()
        val r = 1.6f + (R_MAX - 1.6f) * sqrt(a)
        val theta = arm + r * 0.42f + (b - 0.5f) * (1.1f * (1f - r / R_MAX) + 0.3f)
        val y = (((h ushr 8) and 0xFF) / 255f - 0.5f) * 1.4f * (1f - r / (R_MAX * 1.3f))
        return floatArrayOf(r * cos(theta), y, r * sin(theta))
    }

    fun setPlanets(list: List<GalaxyPlanet>) {
        if (list.map { it.username to it.totalXp } == planets.map { it.data.username to it.data.totalXp }) return
        planets = list.map { p ->
            MapPlanet(
                data = p,
                pos = positionFor(p.username),
                scale = 0.55f + 0.06f * p.stage + 0.09f * minOf(p.planetIndex, 4),
                base = buildPalette3D(p.seed).rockHigh,
                phase = (p.seed % 360).toFloat()
            )
        }
        texturesStarted = false
        markersDirty = true

        // Auto-encuadre: con pocos mundos, la vista por defecto los muestra de cerca en vez
        // de enseñar una espiral vacía; con muchos, se abre hasta abarcar la galaxia.
        val ps = planets
        if (ps.isNotEmpty()) {
            val c = floatArrayOf(0f, 0f, 0f)
            for (mp in ps) { c[0] += mp.pos[0]; c[1] += mp.pos[1]; c[2] += mp.pos[2] }
            val n = ps.size.toFloat()
            c[0] = c[0] / n; c[1] = c[1] / n; c[2] = c[2] / n
            var maxR = 0f
            for (mp in ps) {
                val dx = mp.pos[0] - c[0]; val dy = mp.pos[1] - c[1]; val dz = mp.pos[2] - c[2]
                val r = sqrt(dx * dx + dy * dy + dz * dz) + mp.scale
                if (r > maxR) maxR = r
            }
            homeTarget[0] = c[0]; homeTarget[1] = c[1]; homeTarget[2] = c[2]
            homeDistance = (maxR * 2.4f + 3.5f).coerceIn(5f, 28f)
            resetCamera()
        }
    }

    /** Genera las texturas de todos los planetas, una a una, fuera del hilo GL. */
    private fun startTextureGeneration() {
        if (texturesStarted) return
        texturesStarted = true
        val snapshot = planets
        thread(name = "galaxy-tex") {
            for (mp in snapshot) {
                if (mp.tex != null || mp.pending != null) continue
                val d = mp.data
                mp.pending = generatePlanetTextures(
                    d.seed, PlanetStage.entries[d.stage.coerceIn(0, 6)],
                    progressQ = 3, species = d.species, completed = false, width = 256
                )
            }
        }
    }

    fun orbit(dx: Float, dy: Float) {
        azimuth += dx * 3.5f
        elevation = (elevation + dy * 2.5f).coerceIn(0.08f, 1.35f)
    }

    fun zoom(factor: Float) {
        distanceGoal = (distanceGoal / factor).coerceIn(2.2f, 34f)
    }

    /** Vuelve a la vista general (el encuadre que contiene todos los mundos). */
    fun resetCamera() {
        targetGoal[0] = homeTarget[0]; targetGoal[1] = homeTarget[1]; targetGoal[2] = homeTarget[2]
        distanceGoal = homeDistance
    }

    /** Planeta más cercano al toque en pantalla (o null); si hay, la cámara vuela hasta él. */
    fun pick(x: Float, y: Float, density: Float): GalaxyPlanet? {
        val threshold = 42f * density
        var best: MapPlanet? = null
        var bestD = Float.MAX_VALUE
        val v = FloatArray(4)
        for (mp in planets) {
            v[0] = mp.pos[0]; v[1] = mp.pos[1]; v[2] = mp.pos[2]; v[3] = 1f
            val out = FloatArray(4)
            Matrix.multiplyMV(out, 0, vp, 0, v, 0)
            if (out[3] <= 0f) continue
            val sx = (out[0] / out[3] * 0.5f + 0.5f) * vpW
            val sy = (1f - (out[1] / out[3] * 0.5f + 0.5f)) * vpH
            val dx = sx - x; val dy = sy - y
            val d = sqrt(dx * dx + dy * dy)
            if (d < bestD) { bestD = d; best = mp }
        }
        return if (best != null && bestD < threshold) {
            targetGoal[0] = best.pos[0]; targetGoal[1] = best.pos[1]; targetGoal[2] = best.pos[2]
            distanceGoal = best.scale * 4.6f
            best.data
        } else {
            null
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glClearColor(0.043f, 0.043f, 0.063f, 1f)
        glEnable(GL_DEPTH_TEST)
        model = PlanetModel()
        stars = StarField(500, radius = 90f, seed = 42)
        dust = SpiralDust(900, R_MAX * 1.05f)
        // Contexto nuevo: invalida texturas y marcadores para que se regeneren.
        for (mp in planets) { mp.tex = null }
        texturesStarted = false
        markers = null
        markersDirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        vpW = width; vpH = height
        Matrix.perspectiveM(proj, 0, 48f, width.toFloat() / height, 0.1f, 300f)
    }

    override fun onDrawFrame(gl: GL10?) {
        startTextureGeneration()
        if (markersDirty) {
            // Un halo tenue por mundo: los hace localizables entre las estrellas desde lejos.
            markersDirty = false
            val snapshot = planets
            val data = FloatArray(snapshot.size * 5)
            var i = 0
            for (mp in snapshot) {
                data[i++] = mp.pos[0]; data[i++] = mp.pos[1]; data[i++] = mp.pos[2]
                data[i++] = 34f; data[i++] = 0.30f
            }
            markers = if (snapshot.isEmpty()) null else StarFieldRaw(data)
        }

        // Suavizado de cámara (fly-to y zoom).
        for (i in 0..2) target[i] += (targetGoal[i] - target[i]) * 0.10f
        distance += (distanceGoal - distance) * 0.10f

        camPos[0] = target[0] + distance * cos(elevation) * cos(azimuth)
        camPos[1] = target[1] + distance * sin(elevation)
        camPos[2] = target[2] + distance * cos(elevation) * sin(azimuth)

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        Matrix.setLookAtM(
            view, 0, camPos[0], camPos[1], camPos[2],
            target[0], target[1], target[2], 0f, 1f, 0f
        )
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        stars?.draw(vp)
        dust?.draw(vp)
        markers?.draw(vp, floatArrayOf(0.62f, 0.5f, 1f, 1f))

        val t = SystemClock.uptimeMillis() / 1000f
        val pm = model ?: return
        for (mp in planets) {
            mp.pending?.let { set ->
                mp.tex = pm.upload(set, mp.tex)
                mp.pending = null
            }
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, mp.pos[0], mp.pos[1], mp.pos[2])
            Matrix.rotateM(m, 0, mp.phase + t * 4f, 0f, 1f, 0f)
            Matrix.scaleM(m, 0, mp.scale, mp.scale, mp.scale)
            Matrix.multiplyMM(mvp, 0, vp, 0, m, 0)

            Matrix.setIdentityM(cloudM, 0)
            Matrix.translateM(cloudM, 0, mp.pos[0], mp.pos[1], mp.pos[2])
            Matrix.rotateM(cloudM, 0, mp.phase + t * 5.5f, 0f, 1f, 0f)
            val cs = mp.scale * 1.02f
            Matrix.scaleM(cloudM, 0, cs, cs, cs)
            Matrix.multiplyMM(cloudMvp, 0, vp, 0, cloudM, 0)

            pm.draw(mvp, m, camPos, mp.tex, mp.base, cloudMvp, cloudM)
        }
    }
}
