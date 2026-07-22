package com.aesthetic.gym.ui.galaxy

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.planet.PlanetEngine
import com.aesthetic.gym.domain.planet.PlanetPrefs
import com.aesthetic.gym.domain.planet.PlanetState
import com.aesthetic.gym.social.AccountManager
import com.aesthetic.gym.social.GalaxyPlanet
import com.aesthetic.gym.social.GalaxyResult
import com.aesthetic.gym.social.SessionState
import com.aesthetic.gym.social.SocialApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** La galaxia global, con sus tres estados honestos. */
sealed interface GlobalGalaxyUi {
    data object NeedsLogin : GlobalGalaxyUi
    data object Loading : GlobalGalaxyUi
    data object Error : GlobalGalaxyUi
    data class Ready(val planets: List<GalaxyPlanet>) : GlobalGalaxyUi
}

class GalaxyViewModel(
    private val repo: GymRepository,
    private val appContext: Context
) : ViewModel() {

    /** Estado del planeta propio, derivado en vivo del historial + prefs. */
    val planet: StateFlow<PlanetState> = combine(
        repo.sessionsWithSetsHot,
        repo.setMuscleRowsHot
    ) { sessions, rows ->
        val state = PlanetEngine.compute(
            sessions = sessions,
            muscleRows = rows,
            baseSeed = PlanetPrefs.baseSeed(appContext),
            highWaterXp = PlanetPrefs.highWater(appContext)
        )
        // Congela la semilla y sube la marca de agua (idempotente, barato).
        PlanetPrefs.persist(appContext, state.baseSeed, state.totalXp)
        state
    }
        // Derivar el planeta itera el historial completo: fuera del hilo principal.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanetState())

    val session: StateFlow<SessionState> = AccountManager.state

    private val _global = MutableStateFlow<GlobalGalaxyUi>(GlobalGalaxyUi.Loading)
    val global: StateFlow<GlobalGalaxyUi> = _global

    private var publishedXp = -1
    private var fetchJob: Job? = null

    init {
        // Publica el snapshot y trae la galaxia global al abrir la pantalla (con sesión).
        viewModelScope.launch {
            combine(planet, session) { p, s -> p to s }
                .collect { (p, s) ->
                    if (s is SessionState.LoggedIn) {
                        if (p.hasHistory && p.totalXp != publishedXp) {
                            publishedXp = p.totalXp
                            publish(p)
                        }
                    }
                }
        }
        viewModelScope.launch {
            session.collect { refreshGlobal() }
        }
    }

    private fun publish(p: PlanetState) {
        val token = AccountManager.currentToken() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = SocialApi.publishPlanet(token, totalXp = p.totalXp, species = p.species, seed = p.seed)
            // Fallo (red, 429 del rate limit…): permite reintentar en la próxima emisión
            // o desde el worker periódico, en vez de dar el snapshot por publicado.
            if (!ok && publishedXp == p.totalXp) publishedXp = -1
        }
    }

    fun refreshGlobal() {
        val token = AccountManager.currentToken()
        if (token == null) {
            _global.value = GlobalGalaxyUi.NeedsLogin
            return
        }
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _global.value = GlobalGalaxyUi.Loading
            val result = withContext(Dispatchers.IO) { SocialApi.fetchGalaxy(token) }
            _global.value = when (result) {
                is GalaxyResult.Success -> GlobalGalaxyUi.Ready(result.planets)
                GalaxyResult.NeedsLogin -> GlobalGalaxyUi.NeedsLogin
                GalaxyResult.NetworkError -> GlobalGalaxyUi.Error
            }
        }
    }

    companion object {
        fun factory(repo: GymRepository, context: Context) = viewModelFactory {
            initializer { GalaxyViewModel(repo, context.applicationContext) }
        }
    }
}
