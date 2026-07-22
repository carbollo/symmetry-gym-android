package com.aesthetic.gym.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.social.AccountManager
import com.aesthetic.gym.social.ProfileResult
import com.aesthetic.gym.social.SocialApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PublicProfileViewModel(private val username: String) : ViewModel() {

    /** null = cargando; luego un ProfileResult. */
    private val _state = MutableStateFlow<ProfileResult?>(null)
    val state: StateFlow<ProfileResult?> = _state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    init { load() }

    fun load() {
        val token = AccountManager.currentToken() ?: return
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { SocialApi.getProfile(token, username) }
        }
    }

    /** Añadir amigo, o aceptar la solicitud entrante (el backend auto-acepta si él ya te la envió). */
    fun addOrAccept() {
        val token = AccountManager.currentToken() ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SocialApi.sendFriendRequest(token, username) }
            _busy.value = false
            load()
        }
    }

    /** Eliminar amigo o cancelar la solicitud enviada. */
    fun removeOrCancel() {
        val token = AccountManager.currentToken() ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SocialApi.unfriend(token, username) }
            _busy.value = false
            load()
        }
    }

    companion object {
        fun factory(username: String) =
            viewModelFactory { initializer { PublicProfileViewModel(username) } }
    }
}
