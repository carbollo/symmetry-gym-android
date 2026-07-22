package com.aesthetic.gym.ui.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.aesthetic.gym.BuildConfig
import com.aesthetic.gym.premium.DeviceIdentity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Cómo terminó un intento de ver el anuncio recompensado. */
sealed interface RewardOutcome {
    /** El servidor de AdMob confirmó (SSV) que el anuncio se vio de verdad: dar la recompensa. */
    object Verified : RewardOutcome

    /** El usuario cerró el anuncio antes de completarlo: no hay recompensa. */
    object NotEarned : RewardOutcome

    /** Se vio, pero el servidor no confirmó a tiempo (callback lento, red caída…). Sin recompensa. */
    data class Unverified(val reason: String) : RewardOutcome

    /** Ni siquiera se pudo mostrar (no había anuncio, o la SDK falló al abrirlo). */
    data class NotShown(val reason: String) : RewardOutcome
}

/** Estado visible para la UI mientras se ve/verifica un anuncio. */
enum class RewardPhase { Idle, Showing, Verifying }

/**
 * Un intersticial recompensado precargado, mostrado de forma VOLUNTARIA (el usuario pulsa un
 * botón), con VERIFICACIÓN POR SERVIDOR (SSV) de AdMob antes de conceder la recompensa.
 *
 * Flujo completo:
 *  1. Antes de mostrar, se genera un `nonce` único y se adjunta al anuncio como custom_data,
 *     junto a un userId estable del dispositivo (ServerSideVerificationOptions).
 *  2. El usuario ve el anuncio.
 *  3. Los servidores de AdMob llaman a TU servidor (URL de SSV configurada en la consola) con
 *     los datos + una firma. Tu servidor la valida y marca ese nonce como "verificado".
 *  4. Al cerrarse el anuncio, la app consulta a tu servidor por ese nonce y SOLO concede la
 *     recompensa (RewardOutcome.Verified) cuando el servidor lo confirma.
 *
 * El callback de recompensa del cliente (OnUserEarnedRewardListener) NO concede nada por sí solo:
 * únicamente nos dice que el usuario llegó al final, y a partir de ahí esperamos al servidor.
 *
 * Reglas: cargar/mostrar SIEMPRE en el hilo principal; precargar el siguiente al cerrarse; nunca
 * bloquear la app si no hay anuncio ni si el servidor no responde (falla "en cerrado", sin premio).
 */
object RewardedAds {

    /**
     * Unidad de PRUEBA de Google. Sirve para comprobar que el anuncio se muestra, pero NO permite
     * probar SSV: pertenece a Google, no a tu cuenta, así que no puedes configurarle una URL de
     * verificación. Para probar SSV de extremo a extremo usa tu unidad real registrando tu móvil
     * como dispositivo de prueba en AdMob (ver README del servidor).
     */
    const val TEST_UNIT = "ca-app-pub-3940256099942544/5354046379"

    /**
     * ID real de la unidad "Intersticial recompensado" (app ca-app-pub-8070709640197888~7006093711).
     * Es la única que admite SSV; la URL de verificación se configura en ESTA unidad en AdMob.
     * Solo se usa en builds de release (en debug se usa TEST_UNIT).
     */
    const val LIVE_UNIT = "ca-app-pub-8070709640197888/1024842017"

    /**
     * URL base pública del servidor Premium/SSV en Railway (servicio `ads-server`, proyecto
     * splendid-acceptance/production), SIN barra final. La app consulta "$BASE/reward/status".
     */
    const val VERIFY_BASE_URL = "https://ads-server-production-4a8a.up.railway.app"

    private val unitId: String get() = if (BuildConfig.DEBUG) TEST_UNIT else LIVE_UNIT

    // Ritmo del sondeo al servidor tras cerrarse el anuncio.
    private const val FIRST_DELAY_MS = 1_500L      // el callback de Google suele tardar 1-2 s
    private const val POLL_INTERVAL_MS = 2_000L
    private const val VERIFY_TIMEOUT_MS = 25_000L  // tras esto: Unverified (sin premio)
    private const val NET_TIMEOUT_MS = 5_000

    private val _loaded = MutableStateFlow(false)

    /** true cuando hay un anuncio en memoria listo para mostrarse de inmediato. */
    val loaded: StateFlow<Boolean> = _loaded

    private val _phase = MutableStateFlow(RewardPhase.Idle)

    /** Para que la UI muestre "Verificando…" mientras se espera la confirmación del servidor. */
    val phase: StateFlow<RewardPhase> = _phase

    private var ad: RewardedInterstitialAd? = null
    private var loading = false

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Pide un anuncio si no hay ninguno cargado ni otra petición en curso. Seguro llamarlo de más.
     * Marshala al hilo principal por si lo invocan desde un hilo de fondo.
     */
    fun preload(context: Context) {
        val appContext = context.applicationContext
        main.post {
            if (loading || ad != null) return@post
            loading = true
            RewardedInterstitialAd.load(
                appContext,
                unitId,
                AdRequest.Builder().build(),
                object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(loaded: RewardedInterstitialAd) {
                        ad = loaded
                        loading = false
                        _loaded.value = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        ad = null
                        loading = false
                        _loaded.value = false
                    }
                }
            )
        }
    }

    /**
     * Muestra el anuncio precargado. Debe llamarse desde el hilo principal (una acción de UI).
     * Entrega el resultado por [onOutcome], siempre en el hilo principal y una sola vez.
     */
    fun show(activity: Activity, onOutcome: (RewardOutcome) -> Unit) {
        val current = ad
        if (current == null) {
            preload(activity) // que quede listo para el siguiente intento
            onOutcome(RewardOutcome.NotShown("todavía no hay anuncio listo"))
            return
        }

        val nonce = UUID.randomUUID().toString()
        current.setServerSideVerificationOptions(
            ServerSideVerificationOptions.Builder()
                // Mismo deviceId con el que Premium reclama: asi el servidor acredita al dispositivo
                // correcto y solo el (que tiene la clave privada) puede luego reclamarlo.
                .setUserId(DeviceIdentity.deviceId())
                .setCustomData(nonce)
                .build()
        )

        var earnedClient = false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                _phase.value = RewardPhase.Showing
            }

            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                _phase.value = RewardPhase.Idle
                preload(activity.applicationContext)
                onOutcome(RewardOutcome.NotShown("no se pudo abrir: ${e.message}"))
            }

            override fun onAdDismissedFullScreenContent() {
                preload(activity.applicationContext) // el siguiente ya, en paralelo
                if (!earnedClient) {
                    _phase.value = RewardPhase.Idle
                    onOutcome(RewardOutcome.NotEarned)
                    return
                }
                // Se completó: ahora esperamos a que TU servidor confirme el nonce vía SSV.
                _phase.value = RewardPhase.Verifying
                scope.launch {
                    val verified = awaitServerVerification(nonce)
                    _phase.value = RewardPhase.Idle
                    onOutcome(
                        if (verified) RewardOutcome.Verified
                        else RewardOutcome.Unverified("el servidor no confirmó el anuncio a tiempo")
                    )
                }
            }
        }

        // Se consume al mostrarse: a partir de aquí ya no está "listo".
        ad = null
        _loaded.value = false
        current.show(activity) { earnedClient = true }
    }

    /** Sondea "$VERIFY_BASE_URL/reward/status" hasta que confirme el nonce o venza el tiempo. */
    private suspend fun awaitServerVerification(nonce: String): Boolean {
        val start = SystemClock.elapsedRealtime()
        delay(FIRST_DELAY_MS)
        while (SystemClock.elapsedRealtime() - start < VERIFY_TIMEOUT_MS) {
            if (withContext(Dispatchers.IO) { checkVerified(nonce) } == true) return true
            delay(POLL_INTERVAL_MS)
        }
        return false
    }

    /** true = verificado; false = aún no; null = error de red (se reintenta). */
    private fun checkVerified(nonce: String): Boolean? = runCatching {
        val url = URL("$VERIFY_BASE_URL/reward/status?nonce=" + URLEncoder.encode(nonce, "UTF-8"))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NET_TIMEOUT_MS
            readTimeout = NET_TIMEOUT_MS
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optBoolean("verified", false)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
