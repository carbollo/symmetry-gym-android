package com.aesthetic.gym.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.RoutineWithDays
import com.aesthetic.gym.data.db.WorkoutSessionEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.Rank
import com.aesthetic.gym.domain.streak.WeeklyStreak
import com.aesthetic.gym.domain.streak.computeWeeklyStreak
import com.aesthetic.gym.domain.streak.weeklyTargetFor
import com.aesthetic.gym.domain.model.Sex
import com.aesthetic.gym.domain.rank.RankCalculator
import com.aesthetic.gym.util.WorkoutMath
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val adsTestMode: Boolean = false
)

class HomeViewModel(private val repo: GymRepository) : ViewModel() {

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
            adsTestMode = profile?.adsTestMode == true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun startSession(routineId: Long?, dayId: Long?, name: String, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.startSession(routineId, dayId, name)
            onReady(id)
        }
    }


    companion object {
        fun factory(repo: GymRepository) = viewModelFactory {
            initializer { HomeViewModel(repo) }
        }
    }
}
