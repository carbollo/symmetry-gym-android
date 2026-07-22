package com.aesthetic.gym.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.RoutineWithDays
import com.aesthetic.gym.data.db.WorkoutSessionEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.Rank
import com.aesthetic.gym.domain.planet.PlanetEngine
import com.aesthetic.gym.domain.planet.PlanetPrefs
import com.aesthetic.gym.domain.planet.PlanetState
import com.aesthetic.gym.domain.streak.WeeklyStreak
import com.aesthetic.gym.domain.streak.computeWeeklyStreak
import com.aesthetic.gym.domain.streak.weeklyTargetFor
import com.aesthetic.gym.domain.model.Sex
import com.aesthetic.gym.domain.rank.RankCalculator
import com.aesthetic.gym.util.WorkoutMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class HomeUiState(
    val name: String = "",
    val overallScore: Int = 0,
    val overallRank: Rank = Rank.SILVER_I,
    val weekly: WeeklyStreak = WeeklyStreak(),
    val totalWorkouts: Int = 0,
    val activeRoutine: RoutineWithDays? = null,
    val recent: List<WorkoutSessionEntity> = emptyList(),
    val volumeChangePct: Int? = null,
    val loading: Boolean = true,
    /** Diagnostics switch for the ad slot (Perfil > Anuncios). */
    val adsTestMode: Boolean = false,
    /** Estado del planeta (sistema de motivación): tarjeta "Tu planeta". */
    val planet: PlanetState = PlanetState()
)

class HomeViewModel(
    private val repo: GymRepository,
    private val appContext: Context
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        repo.profileHot,
        repo.activeRoutineHot,
        repo.sessionsWithSetsHot,
        repo.setMuscleRowsHot
    ) { profile, active, sessionsWithSets, rows ->
        // Días de entreno reales: una sesión sin ninguna serie confirmada no cuenta.
        val trainingDays = sessionsWithSets
            .filter { sw -> sw.sets.any { it.completed } }
            .map { it.session.startedAt }
        val weekly = computeWeeklyStreak(trainingDays, weeklyTargetFor(active))

        val bw = profile?.bodyweightKg ?: 75.0
        val sex = profile?.sex ?: Sex.MALE
        val summary = RankCalculator.compute(rows, bw, sex)
        val sessions = sessionsWithSets.map { it.session }

        // Week-over-week training volume, used by the "Análisis biométrico" card.
        val now = repo.now()
        val week = 7L * 24 * 3600 * 1000
        val volThis = sessionsWithSets
            .filter { it.session.startedAt >= now - week }
            .sumOf { WorkoutMath.volumeKg(it.sets) }
        val volPrev = sessionsWithSets
            .filter { it.session.startedAt in (now - 2 * week) until (now - week) }
            .sumOf { WorkoutMath.volumeKg(it.sets) }
        val changePct = if (volPrev > 0.0) (((volThis - volPrev) / volPrev) * 100).roundToInt() else null

        // Planeta: derivado del mismo historial; persistir congela la semilla y sube la
        // marca de agua aunque el usuario nunca abra la pantalla de galaxia.
        val planet = PlanetEngine.compute(
            sessions = sessionsWithSets,
            muscleRows = rows,
            baseSeed = PlanetPrefs.baseSeed(appContext),
            highWaterXp = PlanetPrefs.highWater(appContext)
        )
        PlanetPrefs.persist(appContext, planet.baseSeed, planet.totalXp)

        HomeUiState(
            name = profile?.name?.takeIf { it.isNotBlank() } ?: "Atleta",
            overallScore = summary.overallScore,
            overallRank = summary.overallRank,
            weekly = weekly,
            totalWorkouts = sessions.size,
            activeRoutine = active,
            recent = sessions.take(5),
            volumeChangePct = changePct,
            loading = false,
            adsTestMode = profile?.adsTestMode == true,
            planet = planet
        )
    }
        // Rank + planeta iteran el historial completo: fuera del hilo principal.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun startSession(routineId: Long?, dayId: Long?, name: String, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.startSession(routineId, dayId, name)
            onReady(id)
        }
    }


    companion object {
        fun factory(repo: GymRepository, context: Context) = viewModelFactory {
            initializer { HomeViewModel(repo, context.applicationContext) }
        }
    }
}
