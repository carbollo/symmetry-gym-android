package com.aesthetic.gym.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.RoutineWithDays
import com.aesthetic.gym.data.db.WorkoutSessionEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.Rank
import com.aesthetic.gym.domain.model.Sex
import com.aesthetic.gym.domain.rank.RankCalculator
import com.aesthetic.gym.util.WorkoutMath
import com.aesthetic.gym.util.epochToLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class HomeUiState(
    val name: String = "",
    val overallScore: Int = 0,
    val overallRank: Rank = Rank.SILVER_I,
    val streak: Int = 0,
    val totalWorkouts: Int = 0,
    val activeRoutine: RoutineWithDays? = null,
    val recent: List<WorkoutSessionEntity> = emptyList(),
    val volumeChangePct: Int? = null,
    val loading: Boolean = true
)

class HomeViewModel(private val repo: GymRepository) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        repo.profileHot,
        repo.activeRoutineHot,
        repo.sessionsWithSetsHot,
        repo.setMuscleRowsHot
    ) { profile, active, sessionsWithSets, rows ->
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
            streak = computeStreak(sessions.map { it.startedAt }),
            totalWorkouts = sessions.size,
            activeRoutine = active,
            recent = sessions.take(5),
            volumeChangePct = changePct,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun startSession(routineId: Long?, dayId: Long?, name: String, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.startSession(routineId, dayId, name)
            onReady(id)
        }
    }

    private fun computeStreak(starts: List<Long>): Int {
        if (starts.isEmpty()) return 0
        val days = starts.map { epochToLocalDate(it) }.toSortedSet().toList().reversed()
        val today = LocalDate.now()
        var cursor = when {
            days.first() == today -> today
            days.first() == today.minusDays(1) -> today.minusDays(1)
            else -> return 0
        }
        val set = days.toHashSet()
        var streak = 0
        while (set.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory {
            initializer { HomeViewModel(repo) }
        }
    }
}
