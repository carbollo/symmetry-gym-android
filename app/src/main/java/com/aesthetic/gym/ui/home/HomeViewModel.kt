package com.aesthetic.gym.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.RoutineWithDays
import com.aesthetic.gym.data.db.WorkoutSessionEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.Rank
import com.aesthetic.gym.domain.rank.RankCalculator
import com.aesthetic.gym.util.epochToLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val name: String = "",
    val overallScore: Int = 0,
    val overallRank: Rank = Rank.PRINCIPIANTE,
    val streak: Int = 0,
    val totalWorkouts: Int = 0,
    val activeRoutine: RoutineWithDays? = null,
    val recent: List<WorkoutSessionEntity> = emptyList(),
    val loading: Boolean = true
)

class HomeViewModel(private val repo: GymRepository) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        repo.profileFlow(),
        repo.activeRoutineFlow(),
        repo.allFinishedSessionsFlow(),
        repo.setMuscleRowsFlow()
    ) { profile, active, sessions, rows ->
        val bw = profile?.bodyweightKg ?: 75.0
        val sex = profile?.sex ?: com.aesthetic.gym.domain.model.Sex.MALE
        val summary = RankCalculator.compute(rows, bw, sex)
        HomeUiState(
            name = profile?.name?.takeIf { it.isNotBlank() } ?: "Atleta",
            overallScore = summary.overallScore,
            overallRank = summary.overallRank,
            streak = computeStreak(sessions.map { it.startedAt }),
            totalWorkouts = sessions.size,
            activeRoutine = active,
            recent = sessions.take(5),
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
        // Streak only counts if the most recent workout was today or yesterday.
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
