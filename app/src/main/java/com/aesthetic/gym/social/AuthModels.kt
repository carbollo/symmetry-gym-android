package com.aesthetic.gym.social

import org.json.JSONObject

/** Cuenta social del usuario (lo que devuelve el backend en `user`). */
data class Account(
    val id: String,
    val email: String,
    val username: String,
    val displayName: String
)

/** Estado de sesión de la app. El núcleo funciona igual esté cual esté (modelo híbrido). */
sealed interface SessionState {
    /** Sin cuenta: la app se usa con normalidad, solo lo social queda bloqueado. */
    data object LoggedOut : SessionState
    /** Con cuenta iniciada. */
    data class LoggedIn(val account: Account) : SessionState
}

/** Resultado de un registro o inicio de sesión. */
sealed interface AuthResult {
    data class Success(val token: String, val account: Account) : AuthResult
    /** 409 en registro: [field] es "email" o "username". */
    data class Conflict(val field: String) : AuthResult
    /** 400: datos mal formados (correo inválido, contraseña corta, usuario inválido). */
    data object InvalidInput : AuthResult
    /** 401 en login: correo o contraseña incorrectos. */
    data object InvalidCredentials : AuthResult
    /** Sin red, timeout, servidor caído o respuesta inesperada. */
    data object NetworkError : AuthResult
}

/** Resultado de validar un token cacheado contra `GET /me`. */
sealed interface MeResult {
    data class Valid(val account: Account) : MeResult
    /** El token ya no vale (caducado o revocado): hay que cerrar sesión. */
    data object Unauthorized : MeResult
    /** No se pudo comprobar (sin red): se mantiene la sesión cacheada. */
    data object NetworkError : MeResult
}

// ---- Vía B: enlace público (deep link) ----

/** Resultado de crear un enlace público de una rutina (`POST /routines`). */
sealed interface CreateLinkResult {
    /** [code] va dentro de la URL: BASE_URL/r/CODE. */
    data class Success(val code: String) : CreateLinkResult
    data object Unauthorized : CreateLinkResult
    data object NetworkError : CreateLinkResult
}

/** Resultado de traer una rutina por su código público (`GET /routines/:code`, sin auth). */
sealed interface FetchRoutineResult {
    data class Success(val name: String, val payload: JSONObject, val ownerUsername: String) : FetchRoutineResult
    /** No existe una rutina con ese código. */
    data object NotFound : FetchRoutineResult
    data object NetworkError : FetchRoutineResult
}

// ---- Vía A: envío dirigido + bandeja de entrada ----

/** Resultado de enviar una rutina a un usuario (`POST /routine-shares`). */
sealed interface SendRoutineResult {
    data class Success(val toUsername: String) : SendRoutineResult
    /** No existe ningún usuario con ese nombre. */
    data object UserNotFound : SendRoutineResult
    /** No puedes enviártela a ti mismo. */
    data object CannotShareSelf : SendRoutineResult
    /** El destinatario no es tu amigo (solo se envían rutinas a amigos). */
    data object NotFriends : SendRoutineResult
    data object Unauthorized : SendRoutineResult
    data object NetworkError : SendRoutineResult
}

/** Una rutina que alguien te ha enviado y espera tu aceptación. */
data class IncomingShare(val id: String, val fromUsername: String, val name: String)

/** Resultado de listar las rutinas entrantes (`GET /routine-shares/incoming`). */
sealed interface IncomingResult {
    data class Success(val shares: List<IncomingShare>) : IncomingResult
    data object Unauthorized : IncomingResult
    data object NetworkError : IncomingResult
}

/** Resultado de aceptar una rutina entrante (`POST /routine-shares/:id/accept`). */
sealed interface AcceptResult {
    data class Success(val name: String, val payload: JSONObject) : AcceptResult
    /** Ya no está disponible (aceptada/rechazada antes, o no es para ti). */
    data object Gone : AcceptResult
    data object Unauthorized : AcceptResult
    data object NetworkError : AcceptResult
}

// ---- Fase C: amigos y perfil público ----

/** Un usuario en resultados de búsqueda o listas. */
data class UserSummary(val id: String, val username: String, val displayName: String)

/** Estado de amistad respecto a mí. */
enum class FriendStatus { NONE, PENDING, ACCEPTED, BLOCKED, SELF }

/** Perfil público de un usuario. [direction] = "incoming"/"outgoing" cuando [status] es PENDING. */
data class PublicProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val status: FriendStatus,
    val direction: String?
)

/** Una solicitud de amistad entrante. */
data class FriendRequest(val id: String, val fromUsername: String, val fromDisplayName: String)

sealed interface SearchResult {
    data class Success(val users: List<UserSummary>) : SearchResult
    data object Unauthorized : SearchResult
    data object NetworkError : SearchResult
}

sealed interface ProfileResult {
    data class Success(val profile: PublicProfile) : ProfileResult
    data object NotFound : ProfileResult
    data object Unauthorized : ProfileResult
    data object NetworkError : ProfileResult
}

/** Resultado de enviar/actualizar una solicitud de amistad. [status] es el estado resultante. */
sealed interface FriendActionResult {
    data class Success(val status: FriendStatus) : FriendActionResult
    data object UserNotFound : FriendActionResult
    data object CannotAddSelf : FriendActionResult
    data object Blocked : FriendActionResult
    data object Unauthorized : FriendActionResult
    data object NetworkError : FriendActionResult
}

sealed interface FriendsResult {
    data class Success(val friends: List<UserSummary>) : FriendsResult
    data object Unauthorized : FriendsResult
    data object NetworkError : FriendsResult
}

sealed interface RequestsResult {
    data class Success(val requests: List<FriendRequest>) : RequestsResult
    data object Unauthorized : RequestsResult
    data object NetworkError : RequestsResult
}

// ---- Gestión de cuenta ----

/** Resultado de cambiar la contraseña (`POST /account/password`). Devuelve un token nuevo. */
sealed interface ChangePasswordResult {
    data class Success(val token: String) : ChangePasswordResult
    /** La contraseña actual no es correcta. */
    data object WrongPassword : ChangePasswordResult
    data object InvalidInput : ChangePasswordResult
    data object NetworkError : ChangePasswordResult
}

/** Resultado de editar el nombre visible (`POST /account/profile`). */
sealed interface UpdateAccountResult {
    data class Success(val account: Account) : UpdateAccountResult
    data object InvalidInput : UpdateAccountResult
    data object NetworkError : UpdateAccountResult
}

/** Cuántas novedades pendientes tengo (para el aviso local). */
data class NotificationSummary(val friendRequests: Int, val sharedRoutines: Int) {
    val total: Int get() = friendRequests + sharedRoutines
}

/** Resultado de pedir el enlace de pago de premium (`POST /premium/checkout`). */
sealed interface CheckoutResult {
    data class Success(val url: String) : CheckoutResult
    /** El servidor no tiene Whop configurado todavía. */
    data object NotConfigured : CheckoutResult
    data object NeedsLogin : CheckoutResult
    data object NetworkError : CheckoutResult
}

/** Un planeta publicado en la galaxia global (`GET /galaxy`). */
data class GalaxyPlanet(
    val username: String,
    val displayName: String,
    val seed: Int,
    val planetIndex: Int,
    /** Etapa 0..6 derivada por el servidor con la misma fórmula que PlanetEngine. */
    val stage: Int,
    val xpInPlanet: Int,
    val totalXp: Int,
    val species: Int
)

/** Resultado de pedir la galaxia global. */
sealed interface GalaxyResult {
    data class Success(val planets: List<GalaxyPlanet>) : GalaxyResult
    data object NeedsLogin : GalaxyResult
    data object NetworkError : GalaxyResult
}
