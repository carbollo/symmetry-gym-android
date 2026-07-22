package com.aesthetic.gym.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.RoutineWithDays
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.social.AccountManager
import com.aesthetic.gym.social.CreateLinkResult
import com.aesthetic.gym.social.RoutinePortable
import com.aesthetic.gym.social.SendRoutineResult
import com.aesthetic.gym.social.SocialApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado del envío dirigido a un usuario. */
enum class SendStatus { Idle, Loading, UserNotFound, Self, NotFriends, Error }

/** Estado del diálogo de "Compartir rutina" (elige entre enviar a un usuario o crear enlace). */
sealed interface ShareUi {
    data object Hidden : ShareUi
    /** Menú inicial: elegir vía. */
    data object Menu : ShareUi
    /** Hace falta iniciar sesión (desde el Perfil) para compartir. */
    data object NeedsLogin : ShareUi
    /** Vía A: formulario de envío a un usuario, con su estado. */
    data class Send(val status: SendStatus) : ShareUi
    /** Vía A: enviada con éxito a [toUsername]. */
    data class Sent(val toUsername: String) : ShareUi
    /** Vía B: creando el enlace. */
    data object LinkLoading : ShareUi
    /** Vía B: enlace listo para copiar/compartir. */
    data class LinkReady(val url: String) : ShareUi
    data object LinkError : ShareUi
}

class RoutineDetailViewModel(
    private val repo: GymRepository,
    private val routineId: Long
) : ViewModel() {

    val routine: StateFlow<RoutineWithDays?> =
        repo.routineWithDaysFlow(routineId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _shareUi = MutableStateFlow<ShareUi>(ShareUi.Hidden)
    val shareUi: StateFlow<ShareUi> = _shareUi

    /** Abre el menú de compartir (o pide login si no hay sesión). */
    fun openShare() {
        _shareUi.value = if (AccountManager.currentToken() == null) ShareUi.NeedsLogin else ShareUi.Menu
    }

    /** Vía A: pasa al formulario de envío a un usuario. */
    fun chooseSend() { _shareUi.value = ShareUi.Send(SendStatus.Idle) }

    fun closeShare() { _shareUi.value = ShareUi.Hidden }

    /** Vía A: envía la rutina al usuario indicado. */
    fun send(toUsername: String) {
        val current = routine.value ?: return
        val token = AccountManager.currentToken() ?: run { _shareUi.value = ShareUi.NeedsLogin; return }
        val target = toUsername.trim()
        if (target.isBlank() || _shareUi.value == ShareUi.Send(SendStatus.Loading)) return
        _shareUi.value = ShareUi.Send(SendStatus.Loading)
        viewModelScope.launch {
            val payload = RoutinePortable.build(current)
            val result = withContext(Dispatchers.IO) {
                SocialApi.sendRoutine(token, target, current.routine.name, payload)
            }
            _shareUi.value = when (result) {
                is SendRoutineResult.Success -> ShareUi.Sent(result.toUsername)
                SendRoutineResult.UserNotFound -> ShareUi.Send(SendStatus.UserNotFound)
                SendRoutineResult.CannotShareSelf -> ShareUi.Send(SendStatus.Self)
                SendRoutineResult.NotFriends -> ShareUi.Send(SendStatus.NotFriends)
                SendRoutineResult.Unauthorized -> ShareUi.NeedsLogin
                SendRoutineResult.NetworkError -> ShareUi.Send(SendStatus.Error)
            }
        }
    }

    /** Vía B: crea un enlace público de la rutina. */
    fun createLink() {
        val current = routine.value ?: return
        val token = AccountManager.currentToken() ?: run { _shareUi.value = ShareUi.NeedsLogin; return }
        _shareUi.value = ShareUi.LinkLoading
        viewModelScope.launch {
            val payload = RoutinePortable.build(current)
            val result = withContext(Dispatchers.IO) {
                SocialApi.createLink(token, current.routine.name, payload)
            }
            _shareUi.value = when (result) {
                is CreateLinkResult.Success -> ShareUi.LinkReady(SocialApi.routineLink(result.code))
                CreateLinkResult.Unauthorized -> ShareUi.NeedsLogin
                CreateLinkResult.NetworkError -> ShareUi.LinkError
            }
        }
    }

    fun setActive() = viewModelScope.launch { repo.setActiveRoutine(routineId) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteRoutine(routineId)
        onDone()
    }

    fun startDay(dayId: Long, name: String, onReady: (Long) -> Unit) = viewModelScope.launch {
        val id = repo.startSession(routineId, dayId, name)
        onReady(id)
    }

    companion object {
        fun factory(repo: GymRepository, routineId: Long) =
            viewModelFactory { initializer { RoutineDetailViewModel(repo, routineId) } }
    }
}
