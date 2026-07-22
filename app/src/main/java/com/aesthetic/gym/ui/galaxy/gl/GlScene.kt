package com.aesthetic.gym.ui.galaxy.gl

import android.graphics.Bitmap
import android.opengl.GLES20.*
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Piezas OpenGL ES 2.0 compartidas por la vista de planeta y el mapa de galaxia:
 * compilación de programas, malla de esfera (pos+normal+uv), campo de estrellas y el
 * "modelo de planeta" con sus tres pasadas (superficie iluminada, nubes, halo atmosférico).
 * Todo vive en el hilo GL; las texturas llegan como Bitmaps generados fuera.
 */
internal object GlUtil {

    fun program(vs: String, fs: String): Int {
        fun shader(type: Int, src: String): Int {
            val id = glCreateShader(type)
            glShaderSource(id, src)
            glCompileShader(id)
            val ok = IntArray(1)
            glGetShaderiv(id, GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "shader: " + glGetShaderInfoLog(id) }
            return id
        }
        val p = glCreateProgram()
        glAttachShader(p, shader(GL_VERTEX_SHADER, vs))
        glAttachShader(p, shader(GL_FRAGMENT_SHADER, fs))
        glLinkProgram(p)
        val ok = IntArray(1)
        glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "link: " + glGetProgramInfoLog(p) }
        return p
    }

    fun texture(bitmap: Bitmap, recycle: Boolean = true): Int {
        val id = IntArray(1)
        glGenTextures(1, id, 0)
        glBindTexture(GL_TEXTURE_2D, id[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0)
        if (recycle) bitmap.recycle()
        return id[0]
    }

    /** Textura 1x1 negra: sampler seguro cuando una capa no tiene mapa. */
    fun blackTexture(): Int {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, -0x1000000)
        return texture(bmp)
    }

    fun floatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(data); position(0) }

    fun shortBuffer(data: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(data.size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply { put(data); position(0) }
}

/** Esfera unitaria interleaved: pos(3) normal(3) uv(2). */
internal class SphereMesh(stacks: Int = 40, slices: Int = 64) {
    val vertices: FloatBuffer
    val indices: ShortBuffer
    val indexCount: Int

    init {
        val verts = FloatArray((stacks + 1) * (slices + 1) * 8)
        var vi = 0
        for (i in 0..stacks) {
            val phi = Math.PI * i / stacks // 0..π desde el polo norte
            val y = cos(phi).toFloat()
            val r = sin(phi).toFloat()
            for (j in 0..slices) {
                val theta = 2.0 * Math.PI * j / slices
                val x = (r * cos(theta)).toFloat()
                val z = (r * sin(theta)).toFloat()
                verts[vi++] = x; verts[vi++] = y; verts[vi++] = z
                verts[vi++] = x; verts[vi++] = y; verts[vi++] = z
                verts[vi++] = 1f - j.toFloat() / slices; verts[vi++] = i.toFloat() / stacks
            }
        }
        val idx = ShortArray(stacks * slices * 6)
        var ii = 0
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val a = (i * (slices + 1) + j).toShort()
                val b = ((i + 1) * (slices + 1) + j).toShort()
                idx[ii++] = a; idx[ii++] = b; idx[ii++] = (a + 1).toShort()
                idx[ii++] = b; idx[ii++] = (b + 1).toShort(); idx[ii++] = (a + 1).toShort()
            }
        }
        vertices = GlUtil.floatBuffer(verts)
        indices = GlUtil.shortBuffer(idx)
        indexCount = idx.size
    }
}

private const val PLANET_VS = """
uniform mat4 uMvp;
uniform mat4 uModel;
attribute vec3 aPos;
attribute vec3 aNormal;
attribute vec2 aUv;
varying vec3 vN;
varying vec3 vPos;
varying vec2 vUv;
void main() {
    gl_Position = uMvp * vec4(aPos, 1.0);
    vN = mat3(uModel) * aNormal;
    vPos = (uModel * vec4(aPos, 1.0)).xyz;
    vUv = aUv;
}
"""

private const val PLANET_FS = """
precision mediump float;
uniform sampler2D uDay;
uniform sampler2D uNight;
uniform vec3 uLightDir;
uniform vec3 uCamPos;
uniform vec3 uAtmo;
uniform float uHasNight;
uniform float uHasAtmo;
uniform float uUseTex;
uniform vec3 uBase;
varying vec3 vN;
varying vec3 vPos;
varying vec2 vUv;
void main() {
    vec3 N = normalize(vN);
    vec3 L = normalize(uLightDir);
    vec3 V = normalize(uCamPos - vPos);
    float ndl = dot(N, L);
    float dayF = pow(clamp(ndl, 0.0, 1.0), 0.85);
    vec4 dayTex = mix(vec4(uBase, 0.03), texture2D(uDay, vUv), uUseTex);
    vec3 col = dayTex.rgb * (0.05 + 0.95 * dayF);
    vec3 H = normalize(L + V);
    float spec = pow(max(dot(N, H), 0.0), 48.0) * dayTex.a * dayF;
    col += vec3(1.0, 0.96, 0.86) * spec * 0.7;
    float nightF = smoothstep(0.03, 0.28, -ndl);
    col += texture2D(uNight, vUv).rgb * nightF * uHasNight * uUseTex;
    float rim = pow(1.0 - max(dot(N, V), 0.0), 2.6);
    col += uAtmo * rim * (0.22 + 0.55 * dayF) * uHasAtmo;
    gl_FragColor = vec4(col, 1.0);
}
"""

private const val CLOUD_FS = """
precision mediump float;
uniform sampler2D uDay;
uniform vec3 uLightDir;
varying vec3 vN;
varying vec2 vUv;
varying vec3 vPos;
void main() {
    vec3 N = normalize(vN);
    float dayF = pow(clamp(dot(N, normalize(uLightDir)), 0.0, 1.0), 0.7);
    float a = texture2D(uDay, vUv).a;
    gl_FragColor = vec4(vec3(0.10 + 0.90 * dayF), a * 0.85);
}
"""

private const val ATMO_FS = """
precision mediump float;
uniform vec3 uAtmo;
uniform vec3 uCamPos;
varying vec3 vN;
varying vec3 vPos;
varying vec2 vUv;
void main() {
    vec3 N = normalize(vN);
    vec3 V = normalize(uCamPos - vPos);
    float d = abs(dot(N, V));
    float a = pow(1.0 - d, 2.2) * 0.9;
    gl_FragColor = vec4(uAtmo, a);
}
"""

private const val STAR_VS = """
uniform mat4 uMvp;
attribute vec3 aPos;
attribute float aSize;
attribute float aAlpha;
varying float vAlpha;
void main() {
    gl_Position = uMvp * vec4(aPos, 1.0);
    gl_PointSize = aSize;
    vAlpha = aAlpha;
}
"""

private const val STAR_FS = """
precision mediump float;
uniform vec4 uTint;
varying float vAlpha;
void main() {
    float d = length(gl_PointCoord - vec2(0.5));
    float a = smoothstep(0.5, 0.12, d) * vAlpha;
    gl_FragColor = vec4(uTint.rgb, uTint.a * a);
}
"""

/** Texturas GL ya subidas de un planeta. */
internal class PlanetGlTex(
    val day: Int, val night: Int, val clouds: Int,
    val atmo: FloatArray, val hasAtmo: Boolean, val hasNight: Boolean, val hasClouds: Boolean
)

/**
 * Dibuja planetas (superficie + nubes + halo). Crear DESPUÉS de tener contexto GL.
 * La dirección de luz es fija en mundo (sol arriba-izquierda-delante, como el 2D).
 */
internal class PlanetModel {
    private val mesh = SphereMesh()
    private val prog = GlUtil.program(PLANET_VS, PLANET_FS)
    private val cloudProg = GlUtil.program(PLANET_VS, CLOUD_FS)
    private val atmoProg = GlUtil.program(PLANET_VS, ATMO_FS)
    private val black = GlUtil.blackTexture()
    val lightDir = floatArrayOf(-0.55f, 0.42f, 0.72f)

    private fun bindMesh(p: Int) {
        val stride = 8 * 4
        mesh.vertices.position(0)
        val aPos = glGetAttribLocation(p, "aPos")
        glEnableVertexAttribArray(aPos)
        glVertexAttribPointer(aPos, 3, GL_FLOAT, false, stride, mesh.vertices)
        mesh.vertices.position(3)
        val aN = glGetAttribLocation(p, "aNormal")
        if (aN >= 0) {
            glEnableVertexAttribArray(aN)
            glVertexAttribPointer(aN, 3, GL_FLOAT, false, stride, mesh.vertices)
        }
        mesh.vertices.position(6)
        val aUv = glGetAttribLocation(p, "aUv")
        if (aUv >= 0) {
            glEnableVertexAttribArray(aUv)
            glVertexAttribPointer(aUv, 2, GL_FLOAT, false, stride, mesh.vertices)
        }
        mesh.vertices.position(0)
    }

    /**
     * [mvp]/[model]: matrices ya compuestas del planeta (con su escala/rotación/posición).
     * [tex] null → esfera de color plano [baseColor] (placeholder mientras se generan texturas).
     */
    fun draw(
        mvp: FloatArray, model: FloatArray, camPos: FloatArray,
        tex: PlanetGlTex?, baseColor: FloatArray,
        cloudMvp: FloatArray? = null, cloudModel: FloatArray? = null
    ) {
        glUseProgram(prog)
        glUniformMatrix4fv(glGetUniformLocation(prog, "uMvp"), 1, false, mvp, 0)
        glUniformMatrix4fv(glGetUniformLocation(prog, "uModel"), 1, false, model, 0)
        glUniform3fv(glGetUniformLocation(prog, "uLightDir"), 1, lightDir, 0)
        glUniform3fv(glGetUniformLocation(prog, "uCamPos"), 1, camPos, 0)
        glUniform3fv(glGetUniformLocation(prog, "uAtmo"), 1, tex?.atmo ?: baseColor, 0)
        glUniform1f(glGetUniformLocation(prog, "uHasNight"), if (tex?.hasNight == true) 1f else 0f)
        glUniform1f(glGetUniformLocation(prog, "uHasAtmo"), if (tex?.hasAtmo == true) 1f else 0.25f)
        glUniform1f(glGetUniformLocation(prog, "uUseTex"), if (tex != null) 1f else 0f)
        glUniform3fv(glGetUniformLocation(prog, "uBase"), 1, baseColor, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, tex?.day ?: black)
        glUniform1i(glGetUniformLocation(prog, "uDay"), 0)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, tex?.night ?: black)
        glUniform1i(glGetUniformLocation(prog, "uNight"), 1)
        bindMesh(prog)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)
        glDrawElements(GL_TRIANGLES, mesh.indexCount, GL_UNSIGNED_SHORT, mesh.indices)

        // Nubes: esfera un 1.8% mayor, con su propia rotación (pasa su propio par de matrices).
        if (tex != null && tex.hasClouds && cloudMvp != null && cloudModel != null) {
            glUseProgram(cloudProg)
            glUniformMatrix4fv(glGetUniformLocation(cloudProg, "uMvp"), 1, false, cloudMvp, 0)
            glUniformMatrix4fv(glGetUniformLocation(cloudProg, "uModel"), 1, false, cloudModel, 0)
            glUniform3fv(glGetUniformLocation(cloudProg, "uLightDir"), 1, lightDir, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, tex.clouds)
            glUniform1i(glGetUniformLocation(cloudProg, "uDay"), 0)
            bindMesh(cloudProg)
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            glDepthMask(false)
            glDrawElements(GL_TRIANGLES, mesh.indexCount, GL_UNSIGNED_SHORT, mesh.indices)
            glDepthMask(true)
            glDisable(GL_BLEND)
        }

        // Halo atmosférico: casco un 5.5% mayor, caras frontales fuera, aditivo.
        if (tex?.hasAtmo == true && cloudMvp != null && cloudModel != null) {
            glUseProgram(atmoProg)
            glUniformMatrix4fv(glGetUniformLocation(atmoProg, "uMvp"), 1, false, cloudMvp, 0)
            glUniformMatrix4fv(glGetUniformLocation(atmoProg, "uModel"), 1, false, cloudModel, 0)
            glUniform3fv(glGetUniformLocation(atmoProg, "uAtmo"), 1, tex.atmo, 0)
            glUniform3fv(glGetUniformLocation(atmoProg, "uCamPos"), 1, camPos, 0)
            bindMesh(atmoProg)
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE)
            glCullFace(GL_FRONT)
            glDepthMask(false)
            glDrawElements(GL_TRIANGLES, mesh.indexCount, GL_UNSIGNED_SHORT, mesh.indices)
            glDepthMask(true)
            glCullFace(GL_BACK)
            glDisable(GL_BLEND)
        }
        glDisable(GL_CULL_FACE)
    }

    /** Sube el juego de texturas al contexto GL (llamar en el hilo GL) y libera el anterior. */
    fun upload(set: PlanetTextureSet, previous: PlanetGlTex?): PlanetGlTex {
        previous?.let { release(it) }
        val day = GlUtil.texture(set.day)
        val night = GlUtil.texture(set.night)
        val clouds = set.clouds?.let { GlUtil.texture(it) } ?: black
        return PlanetGlTex(
            day, night, clouds, set.atmo,
            hasAtmo = set.hasAtmo, hasNight = set.hasNight, hasClouds = set.clouds != null
        )
    }

    fun release(tex: PlanetGlTex) {
        val ids = mutableListOf(tex.day, tex.night)
        if (tex.clouds != black) ids.add(tex.clouds)
        glDeleteTextures(ids.size, ids.toIntArray(), 0)
    }
}

/** Campo de estrellas fijo (semilla constante) + polvo de brazos espirales opcional. */
internal class StarField(count: Int, radius: Float, seed: Int = 42) {
    private val prog = GlUtil.program(STAR_VS, STAR_FS)
    private val buffer: FloatBuffer
    private val n = count

    init {
        val rnd = Random(seed)
        val data = FloatArray(count * 5)
        var i = 0
        repeat(count) {
            // Punto uniforme en la esfera lejana.
            val z = rnd.nextFloat() * 2f - 1f
            val t = rnd.nextFloat() * 2f * Math.PI.toFloat()
            val r = sqrt(1f - z * z)
            data[i++] = r * cos(t) * radius
            data[i++] = z * radius * 0.9f
            data[i++] = r * sin(t) * radius
            data[i++] = 1.5f + rnd.nextFloat() * 3.5f          // tamaño en px
            data[i++] = 0.25f + rnd.nextFloat() * 0.65f        // alpha
        }
        buffer = GlUtil.floatBuffer(data)
    }

    fun draw(vp: FloatArray, tint: FloatArray = floatArrayOf(1f, 1f, 1f, 1f)) {
        glUseProgram(prog)
        glUniformMatrix4fv(glGetUniformLocation(prog, "uMvp"), 1, false, vp, 0)
        glUniform4fv(glGetUniformLocation(prog, "uTint"), 1, tint, 0)
        val stride = 5 * 4
        buffer.position(0)
        val aPos = glGetAttribLocation(prog, "aPos")
        glEnableVertexAttribArray(aPos)
        glVertexAttribPointer(aPos, 3, GL_FLOAT, false, stride, buffer)
        buffer.position(3)
        val aSize = glGetAttribLocation(prog, "aSize")
        glEnableVertexAttribArray(aSize)
        glVertexAttribPointer(aSize, 1, GL_FLOAT, false, stride, buffer)
        buffer.position(4)
        val aAlpha = glGetAttribLocation(prog, "aAlpha")
        glEnableVertexAttribArray(aAlpha)
        glVertexAttribPointer(aAlpha, 1, GL_FLOAT, false, stride, buffer)
        buffer.position(0)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE)
        glDepthMask(false)
        glDrawArrays(GL_POINTS, 0, n)
        glDepthMask(true)
        glDisable(GL_BLEND)
    }
}

/** Polvo galáctico: puntos tenues siguiendo dos brazos espirales, aditivos. */
internal class SpiralDust(count: Int, rMax: Float, seed: Int = 7) {
    private val star = StarFieldRaw(build(count, rMax, seed))

    private fun build(count: Int, rMax: Float, seed: Int): FloatArray {
        val rnd = Random(seed)
        val data = FloatArray(count * 5)
        var i = 0
        repeat(count) { k ->
            val arm = if (k % 2 == 0) 0f else Math.PI.toFloat()
            val t = rnd.nextFloat()
            val r = rMax * sqrt(t)
            val jitter = (rnd.nextFloat() - 0.5f) * (0.9f * (1f - r / rMax) + 0.25f)
            val theta = arm + r * 0.42f + jitter
            data[i++] = r * cos(theta)
            data[i++] = (rnd.nextFloat() - 0.5f) * 0.5f * (1f - r / (rMax * 1.4f))
            data[i++] = r * sin(theta)
            data[i++] = 2.5f + rnd.nextFloat() * 6f
            data[i++] = 0.10f + rnd.nextFloat() * 0.22f
        }
        return data
    }

    fun draw(vp: FloatArray) {
        star.draw(vp, floatArrayOf(0.62f, 0.5f, 1f, 1f))     // violeta
        star.draw(vp, floatArrayOf(0.32f, 0.75f, 0.9f, 0.5f)) // un velo cian encima
    }
}

/** Como StarField pero con datos arbitrarios ya construidos. */
internal class StarFieldRaw(data: FloatArray) {
    private val prog = GlUtil.program(STAR_VS, STAR_FS)
    private val buffer = GlUtil.floatBuffer(data)
    private val n = data.size / 5

    fun draw(vp: FloatArray, tint: FloatArray) {
        glUseProgram(prog)
        glUniformMatrix4fv(glGetUniformLocation(prog, "uMvp"), 1, false, vp, 0)
        glUniform4fv(glGetUniformLocation(prog, "uTint"), 1, tint, 0)
        val stride = 5 * 4
        buffer.position(0)
        val aPos = glGetAttribLocation(prog, "aPos")
        glEnableVertexAttribArray(aPos)
        glVertexAttribPointer(aPos, 3, GL_FLOAT, false, stride, buffer)
        buffer.position(3)
        val aSize = glGetAttribLocation(prog, "aSize")
        glEnableVertexAttribArray(aSize)
        glVertexAttribPointer(aSize, 1, GL_FLOAT, false, stride, buffer)
        buffer.position(4)
        val aAlpha = glGetAttribLocation(prog, "aAlpha")
        glEnableVertexAttribArray(aAlpha)
        glVertexAttribPointer(aAlpha, 1, GL_FLOAT, false, stride, buffer)
        buffer.position(0)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE)
        glDepthMask(false)
        glDrawArrays(GL_POINTS, 0, n)
        glDepthMask(true)
        glDisable(GL_BLEND)
    }
}
