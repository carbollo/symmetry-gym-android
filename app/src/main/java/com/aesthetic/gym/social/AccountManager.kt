package com.aesthetic.gym.social

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sesión social de la app (registro / login / logout), con AUTORIDAD LOCAL sobre la sesión y el
 * backend como fuente de verdad de las credenciales.
 *
 * Modelo híbrido: el núcleo de Zenit funciona sin cuenta; esto solo habilita lo social. El estado
 * arranca en [SessionState.LoggedOut] y solo cambia si el usuario, voluntariamente desde el Perfil,
 * crea cuenta o inicia sesión.
 *
 * El token (JWT de 30 días) y la cuenta cacheada se guardan en `SharedPreferences` privadas del
 * sandbox de la app —igual que el entitlement de premium—. Al arrancar se carga la sesión cacheada
 * (offline) y, si hay red, se revalida contra `GET /me` por si el token caducó o fue revocado.
 */
object AccountManager {

    private const val PREFS = "zenit_account"
    private const val KEY_TOKEN = "token"
    private const val KEY_ID = "id"
    private const val KEY_EMAIL = "email"
    private const val KEY_USERNAME = "username"
    private const val KEY_DISPLAY = "displayName"

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var token: String? = null

    private val _state = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    val state: StateFlow<SessionState> = _state

    /** Epoch ms hasta el que hay premium DE PAGO (Whop) en esta cuenta. 0 = ninguno. */
    private val _premiumUntil = MutableStateFlow(0L)
    val premiumUntil: StateFlow<Long> = _premiumUntil

    /** Consulta al servidor el premium de pago de la cuenta (best-effort). */
    fun refreshPremium() {
        val t = token ?: run { _premiumUntil.value = 0L; return }
        scope.launch {
            val until = withContext(Dispatchers.IO) { SocialApi.premiumStatus(t) }
            // Ignora una respuesta tardía si la sesión cambió (logout / otra cuenta) mientras tanto.
            if (token == t) _premiumUntil.value = until
        }
    }

    /** Inicializa con el contexto de aplicación. Llamar una vez en `ZenitApp.onCreate`. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Carga la sesión cacheada (rápido y offline) y, en segundo plano, la revalida contra el
     * servidor: si el token ya no vale, cierra sesión; si no hay red, mantiene la sesión cacheada.
     */
    fun load() {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedToken = prefs.getString(KEY_TOKEN, null) ?: return
        val id = prefs.getString(KEY_ID, null) ?: return
        token = savedToken
        _state.value = SessionState.LoggedIn(
            Account(
                id = id,
                email = prefs.getString(KEY_EMAIL, "").orEmpty(),
                username = prefs.getString(KEY_USERNAME, "").orEmpty(),
                displayName = prefs.getString(KEY_DISPLAY, "").orEmpty()
            )
        )
        refreshPremium()
        scope.launch {
            val me = SocialApi.fetchMe(savedToken)
            // La revalidación puede tardar hasta 8s; durante esa ventana el usuario pudo cerrar
            // sesión o entrar con otra cuenta. Solo aplicamos el resultado si el token NO cambió,
            // o una respuesta tardía del token viejo revertiría/cerraría la sesión nueva.
            if (token != savedToken) return@launch
            when (me) {
                is MeResult.Valid -> persist(savedToken, me.account) // refresca datos (p.ej. displayName)
                MeResult.Unauthorized -> logout()
                MeResult.NetworkError -> Unit // se conserva lo cacheado
            }
        }
    }

    suspend fun register(email: String, password: String, username: String, displayName: String?): AuthResult {
        val result = withContext(Dispatchers.IO) {
            SocialApi.register(email.trim(), password, username.trim(), displayName?.trim())
        }
        if (result is AuthResult.Success) persist(result.token, result.account)
        return result
    }

    suspend fun login(email: String, password: String): AuthResult {
        val result = withContext(Dispatchers.IO) { SocialApi.login(email.trim(), password) }
        if (result is AuthResult.Success) persist(result.token, result.account)
        return result
    }

    fun logout() {
        token = null
        _premiumUntil.value = 0L
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        _state.value = SessionState.LoggedOut
    }

    /** Token actual para llamadas autenticadas (amigos, rutinas — Fase C/D). null si no hay sesión. */
    fun currentToken(): String? = token

    /**
     * Token para trabajos en segundo plano: en un arranque frío del proceso (WorkManager),
     * `load()` puede no haber terminado aún y [currentToken] daría null aunque haya sesión.
     * Aquí se cae a leer las prefs directamente; si el token estuviera revocado, el servidor
     * responderá 401 y el worker lo tratará como best-effort igualmente.
     */
    fun tokenForBackground(): String? =
        token ?: appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)

    /** Cuenta actual (o null si no hay sesión). */
    fun currentAccount(): Account? = (_state.value as? SessionState.LoggedIn)?.account

    /** Tras cambiar la contraseña: guarda el token nuevo, manteniendo la cuenta. */
    fun onPasswordChanged(newToken: String) {
        val acc = currentAccount() ?: return
        persist(newToken, acc)
    }

    /** Tras editar el perfil: guarda la cuenta actualizada, manteniendo el token. */
    fun onAccountUpdated(account: Account) {
        val t = token ?: return
        persist(t, account)
    }

    private fun persist(newToken: String, account: Account) {
        token = newToken
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, newToken)
            .putString(KEY_ID, account.id)
            .putString(KEY_EMAIL, account.email)
            .putString(KEY_USERNAME, account.username)
            .putString(KEY_DISPLAY, account.displayName)
            .apply()
        _state.value = SessionState.LoggedIn(account)
        refreshPremium()
    }
}
