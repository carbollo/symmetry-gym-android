package com.aesthetic.gym.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.RoutineEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.social.AcceptResult
import com.aesthetic.gym.social.AccountManager
import com.aesthetic.gym.social.IncomingResult
import com.aesthetic.gym.social.IncomingShare
import com.aesthetic.gym.social.RoutinePortable
import com.aesthetic.gym.social.SocialApi
import com.aesthetic.gym.util.epochToLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Weekly adherence: % of planned sets actually confirmed, plus per-day set counts. */
data class WeeklyEfficiency(val percent: Int = 0, val bars: List<Float> = List(7) { 0f })

class RoutinesViewModel(private val repo: GymRepository) : ViewModel() {

    // ---- Bandeja de entrada: rutinas que otros me han enviado ----
    private val _incoming = MutableStateFlow<List<IncomingShare>>(emptyList())
    val incoming: StateFlow<List<IncomingShare>> = _incoming

    /** Mensaje breve tras aceptar/rechazar (para un aviso en pantalla). */
    private val _shareMsg = MutableStateFlow<String?>(null)
    val shareMsg: StateFlow<String?> = _shareMsg

    init { refreshIncoming() }

    /** Recarga la bandeja desde el servidor (best-effort; sin sesión, se vacía). */
    fun refreshIncoming() {
        val token = AccountManager.currentToken()
        if (token == null) { _incoming.value = emptyList(); return }
        viewModelScope.launch {
            when (val r = withContext(Dispatchers.IO) { SocialApi.incomingShares(token) }) {
                is IncomingResult.Success -> _incoming.value = r.shares
                IncomingResult.Unauthorized -> _incoming.value = emptyList()
                IncomingResult.NetworkError -> Unit // mantiene lo que hubiera
            }
        }
    }

    // Ids con un accept en vuelo: evita que un doble toque importe la rutina dos veces.
    private val accepting = mutableSetOf<String>()

    private enum class Outcome { Added, ImportFailed, Gone, Retry }

    /** Acepta una rutina entrante: la importa a la galería y la quita de la bandeja. */
    fun accept(share: IncomingShare) {
        val token = AccountManager.currentToken() ?: return
        if (!accepting.add(share.id)) return // ya se está aceptando
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                when (val r = SocialApi.acceptShare(token, share.id)) {
                    is AcceptResult.Success ->
                        if (RoutinePortable.import(r.payload, repo) != null) Outcome.Added else Outcome.ImportFailed
                    AcceptResult.Gone -> Outcome.Gone
                    AcceptResult.Unauthorized, AcceptResult.NetworkError -> Outcome.Retry
                }
            }
            accepting.remove(share.id)
            when (outcome) {
                // El servidor ya la consumió (accept atómico): en Added/ImportFailed/Gone la quitamos.
                Outcome.Added -> {
                    _incoming.value = _incoming.value.filterNot { it.id == share.id }
                    _shareMsg.value = "Rutina \"${share.name}\" añadida a tus rutinas ✓"
                }
                Outcome.ImportFailed -> {
                    _incoming.value = _incoming.value.filterNot { it.id == share.id }
                    _shareMsg.value = "No se pudo añadir la rutina (formato no válido)."
                }
                Outcome.Gone -> _incoming.value = _incoming.value.filterNot { it.id == share.id }
                // Fallo transitorio: la dejamos en la bandeja para reintentar.
                Outcome.Retry -> _shareMsg.value = "No se pudo aceptar. Inténtalo de nuevo."
            }
        }
    }

    /** Rechaza una rutina entrante (optimista: la quita ya de la bandeja). */
    fun decline(share: IncomingShare) {
        val token = AccountManager.currentToken() ?: return
        _incoming.value = _incoming.value.filterNot { it.id == share.id }
        viewModelScope.launch { withContext(Dispatchers.IO) { SocialApi.declineShare(token, share.id) } }
    }

    fun clearShareMsg() { _shareMsg.value = null }

    val routines: StateFlow<List<RoutineEntity>> =
        repo.routinesHot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val efficiency: StateFlow<WeeklyEfficiency> =
        repo.sessionsWithSetsHot.map { list ->
            val today = LocalDate.now()
            val weekStart = today.minusDays(6)
            val recent = list.filter { epochToLocalDate(it.session.startedAt) >= weekStart }

            val planned = recent.sumOf { it.sets.size }
            val done = recent.sumOf { sw -> sw.sets.count { it.completed } }
            val pct = if (planned > 0) (done * 100) / planned else 0

            val perDay = (0..6).map { offset ->
                val day = weekStart.plusDays(offset.toLong())
                recent.filter { epochToLocalDate(it.session.startedAt) == day }
                    .sumOf { sw -> sw.sets.count { it.completed } }
                    .toFloat()
            }
            val max = perDay.maxOrNull()?.takeIf { it > 0f } ?: 1f
            WeeklyEfficiency(pct, perDay.map { it / max })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyEfficiency())

    fun setActive(id: Long) = viewModelScope.launch { repo.setActiveRoutine(id) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteRoutine(id) }

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory { initializer { RoutinesViewModel(repo) } }
    }
}
