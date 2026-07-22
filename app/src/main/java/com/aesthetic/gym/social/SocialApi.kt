package com.aesthetic.gym.social

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente HTTP del backend social (registro/login/perfil).
 *
 * Sigue la misma convención ligera que el resto de la app (ver [com.aesthetic.gym.premium.Premium]):
 * [HttpURLConnection] + `org.json`, sin Retrofit/OkHttp. Todas las llamadas son BLOQUEANTES: se
 * invocan desde [AccountManager] dentro de un dispatcher de IO.
 *
 * El backend social es un servicio APARTE del de anuncios (otro dominio de Railway), aunque
 * comparten la base de datos. Rellena [BASE_URL] con la URL pública que te dé Railway.
 */
object SocialApi {

    /**
     * URL pública del servicio social en Railway (sin barra final).
     * Servicio `social-api` en el proyecto splendid-acceptance (entorno production).
     */
    const val BASE_URL = "https://social-api-production-8ff7.up.railway.app"

    private const val TIMEOUT_MS = 8_000

    /** POST /auth/register. */
    fun register(email: String, password: String, username: String, displayName: String?): AuthResult {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("username", username)
            .apply { if (!displayName.isNullOrBlank()) put("displayName", displayName) }
        return authCall("/auth/register", body)
    }

    /** POST /auth/login. */
    fun login(email: String, password: String): AuthResult {
        val body = JSONObject().put("email", email).put("password", password)
        return authCall("/auth/login", body)
    }

    // ---- Vía B: enlace público ----

    /** URL pública compartible de una rutina, a partir de su código: BASE_URL/r/CODE. */
    fun routineLink(code: String): String = "$BASE_URL/r/$code"

    /** POST /routines (auth): guarda la rutina y devuelve el código para construir el enlace. */
    fun createLink(token: String, name: String, payload: JSONObject): CreateLinkResult = runCatching {
        val body = JSONObject().put("name", name).put("payload", payload)
        val conn = open("/routines", "POST").apply {
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val code = json.optString("code", "")
                    if (code.isNotEmpty()) CreateLinkResult.Success(code) else CreateLinkResult.NetworkError
                }
                401 -> CreateLinkResult.Unauthorized
                else -> CreateLinkResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(CreateLinkResult.NetworkError)

    /** GET /routines/:code (PÚBLICO, sin token): trae la rutina del enlace para importarla. */
    fun fetchRoutine(code: String): FetchRoutineResult = runCatching {
        val conn = open("/routines/" + URLEncoder.encode(code, "UTF-8"), "GET")
        try {
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val payload = json.optJSONObject("payload")
                    if (payload != null) {
                        FetchRoutineResult.Success(
                            name = json.optString("name", ""),
                            payload = payload,
                            ownerUsername = json.optString("ownerUsername", "")
                        )
                    } else {
                        FetchRoutineResult.NetworkError
                    }
                }
                404 -> FetchRoutineResult.NotFound
                else -> FetchRoutineResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(FetchRoutineResult.NetworkError)

    // ---- Vía A: envío dirigido + bandeja ----

    /** POST /routine-shares (auth): envía la rutina a un usuario por su nombre. */
    fun sendRoutine(token: String, toUsername: String, name: String, payload: JSONObject): SendRoutineResult = runCatching {
        val body = JSONObject().put("toUsername", toUsername).put("name", name).put("payload", payload)
        val conn = open("/routine-shares", "POST").apply {
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    SendRoutineResult.Success(json.optString("toUsername", toUsername))
                }
                404 -> SendRoutineResult.UserNotFound
                403 -> SendRoutineResult.NotFriends
                400 -> {
                    val err = errorJson(conn)?.optString("error").orEmpty()
                    if (err == "cannot_share_self") SendRoutineResult.CannotShareSelf else SendRoutineResult.NetworkError
                }
                401 -> SendRoutineResult.Unauthorized
                else -> SendRoutineResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(SendRoutineResult.NetworkError)

    /** GET /routine-shares/incoming (auth): rutinas que me han enviado y esperan aceptación. */
    fun incomingShares(token: String): IncomingResult = runCatching {
        val conn = open("/routine-shares/incoming", "GET").apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val arr = json.optJSONArray("shares") ?: org.json.JSONArray()
                    val list = (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        val id = o.optString("id")
                        if (id.isBlank()) null
                        else IncomingShare(id, o.optString("fromUsername"), o.optString("name"))
                    }
                    IncomingResult.Success(list)
                }
                401 -> IncomingResult.Unauthorized
                else -> IncomingResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(IncomingResult.NetworkError)

    /** POST /routine-shares/:id/accept (auth): devuelve la rutina para importarla. */
    fun acceptShare(token: String, id: String): AcceptResult = runCatching {
        val conn = open("/routine-shares/" + URLEncoder.encode(id, "UTF-8") + "/accept", "POST").apply {
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val payload = json.optJSONObject("payload")
                    if (payload != null) AcceptResult.Success(json.optString("name", ""), payload)
                    else AcceptResult.NetworkError
                }
                404 -> AcceptResult.Gone
                401 -> AcceptResult.Unauthorized
                else -> AcceptResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(AcceptResult.NetworkError)

    /** POST /routine-shares/:id/decline (auth). true si se descartó. */
    fun declineShare(token: String, id: String): Boolean = runCatching {
        val conn = open("/routine-shares/" + URLEncoder.encode(id, "UTF-8") + "/decline", "POST").apply {
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    // ---- Fase C: amigos y perfil público ----

    /** GET /users/search?q= — busca usuarios por nombre. */
    fun searchUsers(token: String, query: String): SearchResult = runCatching {
        val conn = open("/users/search?q=" + URLEncoder.encode(query, "UTF-8"), "GET").apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    SearchResult.Success(parseUsers(json.optJSONArray("users")))
                }
                401 -> SearchResult.Unauthorized
                else -> SearchResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(SearchResult.NetworkError)

    /** GET /users/:username — perfil público + estado de amistad. */
    fun getProfile(token: String, username: String): ProfileResult = runCatching {
        val conn = open("/users/" + URLEncoder.encode(username, "UTF-8"), "GET").apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val u = json.optJSONObject("user")
                    val f = json.optJSONObject("friendship")
                    if (u != null) {
                        ProfileResult.Success(
                            PublicProfile(
                                id = u.optString("id"),
                                username = u.optString("username"),
                                displayName = u.optString("displayName"),
                                status = parseStatus(f?.optString("status")),
                                direction = f?.optString("direction")?.takeIf { it.isNotBlank() && it != "null" }
                            )
                        )
                    } else ProfileResult.NetworkError
                }
                404 -> ProfileResult.NotFound
                401 -> ProfileResult.Unauthorized
                else -> ProfileResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(ProfileResult.NetworkError)

    /** POST /friends/requests — enviar (o auto-aceptar) solicitud de amistad. */
    fun sendFriendRequest(token: String, toUsername: String): FriendActionResult = runCatching {
        val conn = open("/friends/requests", "POST").apply {
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write(JSONObject().put("toUsername", toUsername).toString().toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    FriendActionResult.Success(parseStatus(json.optString("status")))
                }
                404 -> FriendActionResult.UserNotFound
                403 -> FriendActionResult.Blocked
                400 -> {
                    val err = errorJson(conn)?.optString("error").orEmpty()
                    if (err == "cannot_add_self") FriendActionResult.CannotAddSelf else FriendActionResult.NetworkError
                }
                401 -> FriendActionResult.Unauthorized
                else -> FriendActionResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(FriendActionResult.NetworkError)

    /** GET /friends/requests — solicitudes entrantes. */
    fun incomingRequests(token: String): RequestsResult = runCatching {
        val conn = open("/friends/requests", "GET").apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val arr = json.optJSONArray("requests") ?: org.json.JSONArray()
                    val list = (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        val id = o.optString("id")
                        if (id.isBlank()) null
                        else FriendRequest(id, o.optString("fromUsername"), o.optString("fromDisplayName"))
                    }
                    RequestsResult.Success(list)
                }
                401 -> RequestsResult.Unauthorized
                else -> RequestsResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(RequestsResult.NetworkError)

    /** POST /friends/requests/:id/accept. */
    fun acceptRequest(token: String, id: String): Boolean = postOk("/friends/requests/" + URLEncoder.encode(id, "UTF-8") + "/accept", token)

    /** POST /friends/requests/:id/reject. */
    fun rejectRequest(token: String, id: String): Boolean = postOk("/friends/requests/" + URLEncoder.encode(id, "UTF-8") + "/reject", token)

    /** GET /friends — lista de amigos. */
    fun listFriends(token: String): FriendsResult = runCatching {
        val conn = open("/friends", "GET").apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    FriendsResult.Success(parseUsers(json.optJSONArray("friends")))
                }
                401 -> FriendsResult.Unauthorized
                else -> FriendsResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(FriendsResult.NetworkError)

    /** DELETE /friends/:username — eliminar amigo o cancelar solicitud. */
    fun unfriend(token: String, username: String): Boolean = runCatching {
        val conn = open("/friends/" + URLEncoder.encode(username, "UTF-8"), "DELETE").apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    private fun postOk(path: String, token: String): Boolean = runCatching {
        val conn = open(path, "POST").apply {
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    private fun parseUsers(arr: org.json.JSONArray?): List<UserSummary> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isBlank()) null
            else UserSummary(id, o.optString("username"), o.optString("displayName"))
        }
    }

    private fun parseStatus(s: String?): FriendStatus = when (s) {
        "pending" -> FriendStatus.PENDING
        "accepted" -> FriendStatus.ACCEPTED
        "blocked" -> FriendStatus.BLOCKED
        "self" -> FriendStatus.SELF
        else -> FriendStatus.NONE
    }

    // ---- Gestión de cuenta ----

    /** POST /account/password — cambia la contraseña; devuelve un token nuevo. */
    fun changePassword(token: String, current: String, new: String): ChangePasswordResult = runCatching {
        val body = JSONObject().put("currentPassword", current).put("newPassword", new)
        val conn = open("/account/password", "POST").apply {
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val t = json.optString("token", "")
                    if (t.isNotEmpty()) ChangePasswordResult.Success(t) else ChangePasswordResult.NetworkError
                }
                403 -> ChangePasswordResult.WrongPassword
                400 -> ChangePasswordResult.InvalidInput
                else -> ChangePasswordResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(ChangePasswordResult.NetworkError)

    /** POST /account/profile — edita el nombre visible. */
    fun updateDisplayName(token: String, displayName: String): UpdateAccountResult = runCatching {
        val body = JSONObject().put("displayName", displayName)
        val conn = open("/account/profile", "POST").apply {
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val account = parseAccount(json.optJSONObject("user"))
                    if (account != null) UpdateAccountResult.Success(account) else UpdateAccountResult.NetworkError
                }
                400 -> UpdateAccountResult.InvalidInput
                else -> UpdateAccountResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(UpdateAccountResult.NetworkError)

    /** DELETE /account — borra la cuenta (soft delete). */
    fun deleteAccount(token: String): Boolean = runCatching {
        val conn = open("/account", "DELETE").apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    /** GET /notifications/summary — novedades pendientes. null si falla. */
    fun notificationsSummary(token: String): NotificationSummary? = runCatching {
        val conn = open("/notifications/summary", "GET").apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (conn.responseCode in 200..299) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                NotificationSummary(json.optInt("friendRequests", 0), json.optInt("sharedRoutines", 0))
            } else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    // ---- Premium de pago (Whop) ----

    /** GET /premium/status — epoch ms hasta el que hay premium de pago (0 si ninguno/error). */
    fun premiumStatus(token: String): Long = runCatching {
        val conn = open("/premium/status", "GET").apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (conn.responseCode in 200..299) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val iso = json.optString("premiumUntil", "")
                if (iso.isBlank() || iso == "null") 0L
                else runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
            } else 0L
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(0L)

    /** POST /premium/checkout — pide el enlace de pago de Whop para esta cuenta. */
    fun premiumCheckout(token: String): CheckoutResult = runCatching {
        val conn = open("/premium/checkout", "POST").apply {
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val url = json.optString("url", "")
                    if (url.isNotEmpty()) CheckoutResult.Success(url) else CheckoutResult.NetworkError
                }
                503 -> CheckoutResult.NotConfigured
                401 -> CheckoutResult.NeedsLogin
                else -> CheckoutResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(CheckoutResult.NetworkError)

    // ---- Galaxia (sistema de motivación planeta) ----

    /**
     * POST /galaxy/me — publica el snapshot del planeta. El servidor deriva planeta/etapa
     * desde totalXp con la misma fórmula que PlanetEngine; solo viajan tres números y la
     * versión de la fórmula. true SOLO si el servidor guardó (o ya tenía) estos datos: un
     * 429 significa que el snapshot nuevo NO se guardó (rate limit) y toca reintentar luego.
     * totalXp se satura al tope del servidor: mejor un snapshot congelado en el máximo que
     * un 400 permanente que te borre de la galaxia.
     */
    fun publishPlanet(token: String, totalXp: Int, species: Int, seed: Int): Boolean = runCatching {
        val conn = open("/galaxy/me", "POST").apply {
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val body = JSONObject()
                .put("totalXp", totalXp.coerceIn(0, 120_000))
                .put("species", species.coerceIn(0, 20))
                .put("seed", seed)
                .put("algoVersion", 1)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    /** GET /galaxy — los planetas de la comunidad, los más activos primero. */
    fun fetchGalaxy(token: String): GalaxyResult = runCatching {
        val conn = open("/galaxy", "GET").apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val arr = json.optJSONArray("planets")
                    val list = buildList {
                        for (i in 0 until (arr?.length() ?: 0)) {
                            val p = arr?.optJSONObject(i) ?: continue
                            val username = p.optString("username", "")
                            if (username.isEmpty()) continue
                            add(
                                GalaxyPlanet(
                                    username = username,
                                    displayName = p.optString("displayName", username),
                                    seed = p.optInt("seed", 0),
                                    planetIndex = p.optInt("planetIndex", 0),
                                    stage = p.optInt("stage", 0).coerceIn(0, 6),
                                    xpInPlanet = p.optInt("xpInPlanet", 0),
                                    totalXp = p.optInt("totalXp", 0),
                                    species = p.optInt("species", 0)
                                )
                            )
                        }
                    }
                    GalaxyResult.Success(list)
                }
                401 -> GalaxyResult.NeedsLogin
                else -> GalaxyResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(GalaxyResult.NetworkError)

    /** GET /me con Bearer token: valida un token cacheado al arrancar. */
    fun fetchMe(token: String): MeResult = runCatching {
        val conn = open("/me", "GET").apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            when (conn.responseCode) {
                in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val account = parseAccount(json.getJSONObject("user"))
                    if (account != null) MeResult.Valid(account) else MeResult.NetworkError
                }
                401 -> MeResult.Unauthorized
                else -> MeResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(MeResult.NetworkError)

    /** Ejecuta un POST de auth y traduce el código de estado a [AuthResult]. */
    private fun authCall(path: String, body: JSONObject): AuthResult = runCatching {
        val conn = open(path, "POST").apply { doOutput = true }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            when {
                code in 200..299 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val token = json.optString("token", "")
                    val account = parseAccount(json.optJSONObject("user"))
                    if (token.isNotEmpty() && account != null) {
                        AuthResult.Success(token, account)
                    } else {
                        AuthResult.NetworkError
                    }
                }
                code == 409 -> {
                    val field = errorJson(conn)?.optString("field").orEmpty()
                    AuthResult.Conflict(if (field == "username") "username" else "email")
                }
                code == 400 -> AuthResult.InvalidInput
                code == 401 -> AuthResult.InvalidCredentials
                else -> AuthResult.NetworkError
            }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(AuthResult.NetworkError)

    private fun open(path: String, method: String): HttpURLConnection =
        (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

    /** Lee el cuerpo del error (errorStream) como JSON; null si no hay o no parsea. */
    private fun errorJson(conn: HttpURLConnection): JSONObject? = runCatching {
        conn.errorStream?.bufferedReader()?.use { JSONObject(it.readText()) }
    }.getOrNull()

    private fun parseAccount(user: JSONObject?): Account? {
        if (user == null) return null
        val id = user.optString("id", "")
        if (id.isEmpty()) return null
        return Account(
            id = id,
            email = user.optString("email", ""),
            username = user.optString("username", ""),
            displayName = user.optString("displayName", user.optString("username", ""))
        )
    }
}
