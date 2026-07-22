package com.aesthetic.gym.ui.account

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesthetic.gym.social.AccountManager
import com.aesthetic.gym.social.AuthResult
import com.aesthetic.gym.social.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(val loading: Boolean = false, val error: String? = null)

/**
 * Estado de la pantalla de cuenta. La sesión en sí la lleva [AccountManager] (app-scoped); aquí solo
 * se gestiona el formulario: validación cliente instantánea, estado de carga y mensajes de error.
 */
class AccountViewModel : ViewModel() {

    val session: StateFlow<SessionState> = AccountManager.state

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui

    fun clearError() {
        if (_ui.value.error != null) _ui.value = _ui.value.copy(error = null)
    }

    fun login(email: String, password: String) {
        if (_ui.value.loading) return // evita doble envío (botón + tecla Done)
        val err = validateLogin(email, password)
        if (err != null) { _ui.value = AuthUiState(error = err); return }
        _ui.value = AuthUiState(loading = true)
        viewModelScope.launch {
            val result = AccountManager.login(email, password)
            // En éxito, session pasa a LoggedIn y la pantalla se cierra sola; aquí solo limpiamos.
            _ui.value = AuthUiState(loading = false, error = messageFor(result))
        }
    }

    fun register(email: String, password: String, username: String, displayName: String) {
        if (_ui.value.loading) return // evita doble envío (botón + tecla Done)
        val err = validateRegister(email, password, username)
        if (err != null) { _ui.value = AuthUiState(error = err); return }
        _ui.value = AuthUiState(loading = true)
        viewModelScope.launch {
            val result = AccountManager.register(email, password, username, displayName.ifBlank { username })
            _ui.value = AuthUiState(loading = false, error = messageFor(result))
        }
    }

    fun logout() = AccountManager.logout()

    // ---- validación cliente (el servidor es la fuente de verdad; esto es feedback inmediato) ----

    private fun validateLogin(email: String, password: String): String? = when {
        !isEmail(email) -> "Introduce un correo válido."
        password.isEmpty() -> "Escribe tu contraseña."
        else -> null
    }

    private fun validateRegister(email: String, password: String, username: String): String? = when {
        !isEmail(email) -> "Introduce un correo válido."
        password.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
        !USERNAME_REGEX.matches(username) ->
            "El usuario debe tener 3-30 caracteres: letras, números y . _ -"
        else -> null
    }

    private fun isEmail(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    /** null = éxito; en otro caso, el mensaje a mostrar. */
    private fun messageFor(result: AuthResult): String? = when (result) {
        is AuthResult.Success -> null
        is AuthResult.Conflict ->
            if (result.field == "username") "Ese nombre de usuario ya está cogido."
            else "Ese correo ya tiene una cuenta. Inicia sesión."
        AuthResult.InvalidCredentials -> "Correo o contraseña incorrectos."
        AuthResult.InvalidInput -> "Revisa los datos e inténtalo de nuevo."
        AuthResult.NetworkError -> "No se pudo conectar con el servidor. Inténtalo de nuevo."
    }

    companion object {
        private val USERNAME_REGEX = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{2,29}$")
    }
}
