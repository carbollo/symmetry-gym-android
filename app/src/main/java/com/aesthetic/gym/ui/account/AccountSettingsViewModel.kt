package com.aesthetic.gym.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesthetic.gym.social.AccountManager
import com.aesthetic.gym.social.ChangePasswordResult
import com.aesthetic.gym.social.SessionState
import com.aesthetic.gym.social.SocialApi
import com.aesthetic.gym.social.UpdateAccountResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountSettingsViewModel : ViewModel() {

    val session: StateFlow<SessionState> = AccountManager.state

    private val _msg = MutableStateFlow<String?>(null)
    val msg: StateFlow<String?> = _msg

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun saveDisplayName(name: String) {
        val token = AccountManager.currentToken() ?: return
        val n = name.trim()
        if (n.isBlank() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { SocialApi.updateDisplayName(token, n) }
            _busy.value = false
            _msg.value = when (r) {
                is UpdateAccountResult.Success -> { AccountManager.onAccountUpdated(r.account); "Nombre actualizado ✓" }
                UpdateAccountResult.InvalidInput -> "Nombre no válido."
                UpdateAccountResult.NetworkError -> "No se pudo. Inténtalo de nuevo."
            }
        }
    }

    fun changePassword(current: String, new: String, onDone: () -> Unit) {
        val token = AccountManager.currentToken() ?: return
        if (_busy.value) return
        if (current.isBlank()) { _msg.value = "Escribe tu contraseña actual."; return }
        if (new.length < 8) { _msg.value = "La nueva contraseña debe tener al menos 8 caracteres."; return }
        _busy.value = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { SocialApi.changePassword(token, current, new) }
            _busy.value = false
            when (r) {
                is ChangePasswordResult.Success -> {
                    AccountManager.onPasswordChanged(r.token)
                    _msg.value = "Contraseña cambiada ✓"
                    onDone()
                }
                ChangePasswordResult.WrongPassword -> _msg.value = "La contraseña actual no es correcta."
                ChangePasswordResult.InvalidInput -> _msg.value = "Revisa los datos."
                ChangePasswordResult.NetworkError -> _msg.value = "No se pudo. Inténtalo de nuevo."
            }
        }
    }

    fun deleteAccount(onDone: () -> Unit) {
        val token = AccountManager.currentToken() ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { SocialApi.deleteAccount(token) }
            _busy.value = false
            if (ok) {
                AccountManager.logout() // solo cerramos sesión si el servidor confirmó el borrado
                onDone()
            } else {
                _msg.value = "No se pudo borrar la cuenta. Inténtalo de nuevo."
            }
        }
    }

    fun clearMsg() { _msg.value = null }
}
