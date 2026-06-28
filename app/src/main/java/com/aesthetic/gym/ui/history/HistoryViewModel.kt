package com.aesthetic.gym.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.WorkoutSessionEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.util.WorkoutMath
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

data class SessionSummary(
    val session: WorkoutSessionEntity,
    val volumeKg: Double,
    val kcal: Int,
    val sets: Int,
    val durationMin: Int
)

class HistoryViewModel(repo: GymRepository) : ViewModel() {

    val sessions: StateFlow<List<SessionSummary>> = combine(
        repo.finishedSessionsWithSetsFlow(),
        repo.profileFlow()
    ) { list, profile ->
        val bw = profile?.bodyweightKg ?: 75.0
        list.map { sw ->
            val done = sw.sets.count { it.completed }
            SessionSummary(
                session = sw.session,
                volumeKg = WorkoutMath.volumeKg(sw.sets),
                kcal = WorkoutMath.caloriesKcal(sw.session, sw.sets, bw),
                sets = done,
                durationMin = WorkoutMath.durationMinutes(sw.session, done).roundToInt()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory { initializer { HistoryViewModel(repo) } }
    }
}
