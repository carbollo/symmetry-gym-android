package com.aesthetic.gym.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesthetic.gym.social.AccountManager
import com.aesthetic.gym.social.FriendActionResult
import com.aesthetic.gym.social.FriendRequest
import com.aesthetic.gym.social.FriendStatus
import com.aesthetic.gym.social.FriendsResult
import com.aesthetic.gym.social.RequestsResult
import com.aesthetic.gym.social.SearchResult
import com.aesthetic.gym.social.SocialApi
import com.aesthetic.gym.social.UserSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FriendsViewModel : ViewModel() {

    private val _searchResults = MutableStateFlow<List<UserSummary>>(emptyList())
    val searchResults: StateFlow<List<UserSummary>> = _searchResults

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private val _requests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val requests: StateFlow<List<FriendRequest>> = _requests

    private val _friends = MutableStateFlow<List<UserSummary>>(emptyList())
    val friends: StateFlow<List<UserSummary>> = _friends

    /** Usuarios a los que acabo de enviar solicitud (para reflejar el botón en la búsqueda). */
    private val _sent = MutableStateFlow<Set<String>>(emptySet())
    val sent: StateFlow<Set<String>> = _sent

    private val _msg = MutableStateFlow<String?>(null)
    val msg: StateFlow<String?> = _msg

    init { refresh() }

    /** Recarga solicitudes entrantes y lista de amigos. */
    fun refresh() {
        val token = AccountManager.currentToken() ?: return
        viewModelScope.launch {
            val req = withContext(Dispatchers.IO) { SocialApi.incomingRequests(token) }
            if (req is RequestsResult.Success) _requests.value = req.requests
            val fr = withContext(Dispatchers.IO) { SocialApi.listFriends(token) }
            if (fr is FriendsResult.Success) _friends.value = fr.friends
        }
    }

    private var searchJob: Job? = null

    fun search(query: String) {
        val token = AccountManager.currentToken() ?: return
        val q = query.trim()
        searchJob?.cancel() // cancela una búsqueda previa: su respuesta tardía no pisará estos resultados
        if (q.length < 2) { _searchResults.value = emptyList(); _searching.value = false; return }
        _searching.value = true
        searchJob = viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { SocialApi.searchUsers(token, q) }
            _searching.value = false
            if (r is SearchResult.Success) _searchResults.value = r.users
        }
    }

    fun clearSearch() { searchJob?.cancel(); _searchResults.value = emptyList() }

    fun addFriend(username: String) {
        val token = AccountManager.currentToken() ?: return
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { SocialApi.sendFriendRequest(token, username) }
            when (r) {
                is FriendActionResult.Success -> {
                    _sent.value = _sent.value + username
                    if (r.status == FriendStatus.ACCEPTED) {
                        _msg.value = "Ahora sois amigos ✓"
                        refresh()
                    } else {
                        _msg.value = "Solicitud enviada a @$username"
                    }
                }
                FriendActionResult.CannotAddSelf -> _msg.value = "No puedes agregarte a ti mismo."
                FriendActionResult.UserNotFound -> _msg.value = "Usuario no encontrado."
                FriendActionResult.Blocked -> _msg.value = "No disponible."
                FriendActionResult.Unauthorized, FriendActionResult.NetworkError ->
                    _msg.value = "No se pudo. Inténtalo de nuevo."
            }
        }
    }

    fun accept(req: FriendRequest) {
        val token = AccountManager.currentToken() ?: return
        _requests.value = _requests.value.filterNot { it.id == req.id } // optimista
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { SocialApi.acceptRequest(token, req.id) }
            _msg.value = if (ok) "Ahora sois amigos ✓" else "No se pudo aceptar."
            refresh()
        }
    }

    fun reject(req: FriendRequest) {
        val token = AccountManager.currentToken() ?: return
        _requests.value = _requests.value.filterNot { it.id == req.id } // optimista
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SocialApi.rejectRequest(token, req.id) }
            refresh() // reconcilia por si falló la red (reaparece)
        }
    }

    fun remove(user: UserSummary) {
        val token = AccountManager.currentToken() ?: return
        _friends.value = _friends.value.filterNot { it.id == user.id } // optimista
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SocialApi.unfriend(token, user.username) }
            refresh() // reconcilia por si falló la red
        }
    }

    fun clearMsg() { _msg.value = null }
}
