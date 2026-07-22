package com.aesthetic.gym.premium

import android.content.Context
import android.util.Base64
import com.aesthetic.gym.ui.ads.RewardedAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Estado de premium del dispositivo, con AUTORIDAD EN EL SERVIDOR.
 *
 * El "dueno" del premium es el servidor: cada anuncio verificado por SSV le suma 24 h al deviceId.
 * La app solo pregunta su estado y recibe un entitlement { deviceId, premiumUntil } FIRMADO por el
 * servidor. La app NO se fia de ningun premium que no venga con esa firma (la verifica contra la
 * clave publica incrustada aqui), asi que nadie puede inyectarle un "premium hasta el ano 3000"
 * editando preferencias o interponiendose en la red.
 *
 * Funciona offline: el ultimo entitlement firmado se cachea y vale hasta que caduque su premiumUntil.
 *
 * Limite honesto: como quitar los anuncios se aplica EN el movil, un APK recompilado que borre esta
 * comprobacion podria saltarsela. Lo que NO puede es fabricar el contenido premium del servidor,
 * que solo se entrega si el servidor ve premium activo (ver /premium/content).
 */
object Premium {

    /**
     * Clave publica del servidor (SPKI DER en base64) con la que se firman los entitlements.
     * Pareja de la ENTITLEMENT_PRIVATE_KEY del servicio `ads-server` en Railway.
     */
    const val SERVER_ENTITLEMENT_PUBLIC_KEY =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE0YPXCcxnzq6VAydgJCmZjrG+oRIBtSBk7ROAQZjHHZJyrURVBGCn/u1UPqXXQtghy/7mhzPBxWAPa+9Oz4JOkg=="

    private const val PREFS = "zenit_premium"
    private const val NET_TIMEOUT_MS = 5_000

    private val _premiumUntil = MutableStateFlow(0L)

    /** Epoch ms hasta el que hay premium (0 = nunca). La UI compara contra la hora actual. */
    val premiumUntil: StateFlow<Long> = _premiumUntil

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** ¿premium activo ahora mismo? */
    fun isActive(nowMs: Long = System.currentTimeMillis()): Boolean = _premiumUntil.value > nowMs

    /** Carga el entitlement cacheado (verificando su firma) al arrancar. Rapido y offline. */
    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val deviceId = prefs.getString("deviceId", null) ?: return
        val premiumUntil = prefs.getLong("premiumUntil", 0L)
        val issuedAt = prefs.getLong("issuedAt", 0L)
        val signature = prefs.getString("signature", null) ?: return
        if (verifyEntitlement(deviceId, premiumUntil, issuedAt, signature) &&
            deviceId == DeviceIdentity.deviceId()
        ) {
            _premiumUntil.value = premiumUntil
        }
    }

    /** Pide al servidor el estado actual (tras un anuncio, o al abrir la app). Best-effort. */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val ent = withContext(Dispatchers.IO) { claim() } ?: return@launch
            if (ent.deviceId != DeviceIdentity.deviceId()) return@launch
            if (!verifyEntitlement(ent.deviceId, ent.premiumUntil, ent.issuedAt, ent.signature)) return@launch
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("deviceId", ent.deviceId)
                .putLong("premiumUntil", ent.premiumUntil)
                .putLong("issuedAt", ent.issuedAt)
                .putString("signature", ent.signature)
                .apply()
            _premiumUntil.value = ent.premiumUntil
        }
    }

    private data class Entitlement(
        val deviceId: String,
        val premiumUntil: Long,
        val issuedAt: Long,
        val signature: String
    )

    /** POST /premium/claim autenticado con la identidad del dispositivo. null si falla la red. */
    private fun claim(): Entitlement? = runCatching {
        val ts = System.currentTimeMillis()
        val deviceId = DeviceIdentity.deviceId()
        val body = JSONObject()
            .put("publicKey", DeviceIdentity.publicKeyDerBase64())
            .put("timestamp", ts)
            .put("signature", DeviceIdentity.sign("$deviceId.$ts"))
            .toString()

        val conn = (URL("${RewardedAds.VERIFY_BASE_URL}/premium/claim").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = NET_TIMEOUT_MS
            readTimeout = NET_TIMEOUT_MS
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            Entitlement(
                deviceId = json.getString("deviceId"),
                premiumUntil = json.getLong("premiumUntil"),
                issuedAt = json.getLong("issuedAt"),
                signature = json.getString("signature")
            )
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Verifica que el entitlement lo firmo de verdad el servidor (clave publica incrustada). */
    private fun verifyEntitlement(
        deviceId: String,
        premiumUntil: Long,
        issuedAt: Long,
        signatureB64: String
    ): Boolean = runCatching {
        val pub = serverPublicKey() ?: return false
        val content = "$deviceId.$premiumUntil.$issuedAt"
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(pub)
            update(content.toByteArray(Charsets.UTF_8))
            verify(Base64.decode(signatureB64, Base64.DEFAULT))
        }
    }.getOrDefault(false)

    private fun serverPublicKey(): PublicKey? = runCatching {
        val der = Base64.decode(SERVER_ENTITLEMENT_PUBLIC_KEY, Base64.DEFAULT)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
    }.getOrNull()
}
