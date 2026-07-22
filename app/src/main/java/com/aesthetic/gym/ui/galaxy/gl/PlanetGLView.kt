package com.aesthetic.gym.ui.galaxy.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aesthetic.gym.domain.planet.PlanetStage
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.concurrent.thread

/**
 * Planeta 3D realista: esfera con iluminación por píxel, océano especular, lado nocturno
 * emisivo (lava/ciudades), capa de nubes girando aparte y halo atmosférico. Arrastra para
 * girarlo; suelto, rota solo despacio. Fondo = estrellas propias sobre el negro de la app.
 */
@SuppressLint("ViewConstructor")
internal class PlanetGLView(context: Context) : GLSurfaceView(context) {

    private val renderer = PlanetRenderer()
    private var lastX = 0f
    private var lastY = 0f

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setPlanet(seed: Int, stage: PlanetStage, progressQ: Int, species: Int, completed: Boolean) {
        renderer.setPlanet(seed, stage, progressQ, species, completed) { requestRender() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                renderer.spin((event.x - lastX) / width, (event.y - lastY) / height)
                lastX = event.x; lastY = event.y
            }
        }
        return true
    }
}

internal class PlanetRenderer : GLSurfaceView.Renderer {

    private var model: PlanetModel? = null
    private var stars: StarField? = null
    private var tex: PlanetGlTex? = null
    private var pendingSet: PlanetTextureSet? = null
    private var baseColor = floatArrayOf(0.35f, 0.32f, 0.3f)

    // Identidad del planeta cargado, para no regenerar texturas iguales. Si llega una
    // petición nueva mientras se genera otra, se encola y se atiende al terminar.
    private data class Req(
        val seed: Int, val stage: PlanetStage, val q: Int, val species: Int, val completed: Boolean
    )

    @Volatile private var loadedReq: Req? = null
    @Volatile private var requested: Req? = null
    @Volatile private var generating = false

    private var yaw = 0f
    private var pitch = 0f
    private val camPos = floatArrayOf(0f, 0f, 2.7f)
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val m = FloatArray(16)
    private val mvp = FloatArray(16)
    private val cloudM = FloatArray(16)
    private val cloudMvp = FloatArray(16)
    private val tmp = FloatArray(16)

    fun spin(dx: Float, dy: Float) {
        yaw += dx * 180f
        pitch = (pitch + dy * 120f).coerceIn(-60f, 60f)
    }

    fun setPlanet(
        seed: Int, stage: PlanetStage, progressQ: Int, species: Int, completed: Boolean,
        onReady: () -> Unit
    ) {
        val r = Req(seed, stage, progressQ, species, completed)
        if (r == loadedReq) return
        requested = r
        baseColor = buildPalette3D(seed).rockHigh
        startIfIdle(onReady)
    }

    private fun startIfIdle(onReady: () -> Unit) {
        if (generating) return
        val r = requested ?: return
        if (r == loadedReq) return
        generating = true
        thread(name = "planet-tex") {
            val set = generatePlanetTextures(r.seed, r.stage, r.q, r.species, r.completed, width = 512)
            pendingSet = set
            loadedReq = r
            generating = false
            onReady()
            startIfIdle(onReady) // por si entró otra petición mientras generábamos
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glClearColor(0.043f, 0.043f, 0.063f, 1f) // #0B0B10
        glEnable(GL_DEPTH_TEST)
        model = PlanetModel()
        stars = StarField(220, radius = 60f, seed = 42)
        // Contexto nuevo: las texturas anteriores murieron con él. Regenerar lo cargado.
        tex = null
        if (pendingSet == null && loadedReq != null) {
            requested = loadedReq
            loadedReq = null
            startIfIdle {}
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height
        Matrix.perspectiveM(proj, 0, 42f, aspect, 0.1f, 200f)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingSet?.let { set ->
            tex = model?.upload(set, tex)
            pendingSet = null
        }
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        val t = SystemClock.uptimeMillis() / 1000f

        Matrix.setLookAtM(view, 0, camPos[0], camPos[1], camPos[2], 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        stars?.draw(vp)

        val autoYaw = t * 3.2f // giro lento propio
        Matrix.setIdentityM(m, 0)
        Matrix.rotateM(m, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(m, 0, yaw + autoYaw, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, vp, 0, m, 0)

        // Las nubes giran un poco más rápido que la superficie y son algo mayores.
        Matrix.setIdentityM(cloudM, 0)
        Matrix.rotateM(cloudM, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(cloudM, 0, yaw + autoYaw * 1.35f + 8f, 0f, 1f, 0f)
        Matrix.scaleM(cloudM, 0, 1.02f, 1.02f, 1.02f)
        Matrix.multiplyMM(cloudMvp, 0, vp, 0, cloudM, 0)

        model?.draw(mvp, m, camPos, tex, baseColor, cloudMvp, cloudM)
    }
}

/** El planeta 3D listo para Compose. Gestiona pause/resume con el lifecycle. */
@Composable
fun Planet3D(
    seed: Int,
    stage: PlanetStage,
    progressQ: Int,
    species: Int,
    modifier: Modifier = Modifier,
    completed: Boolean = false
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { arrayOfNulls<PlanetGLView>(1) }

    AndroidView(
        factory = { ctx -> PlanetGLView(ctx).also { holder[0] = it } },
        modifier = modifier,
        update = { it.setPlanet(seed, stage, progressQ, species, completed) }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
