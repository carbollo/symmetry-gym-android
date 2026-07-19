package com.aesthetic.gym.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.WorkoutSessionEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.util.WorkoutMath
import com.aesthetic.gym.util.epochToLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.roundToInt

data class SessionSummary(
    val session: WorkoutSessionEntity,
    val volumeKg: Double,
    val kcal: Int,
    val sets: Int,
    val durationMin: Int
)

data class HistoryState(
    val sessions: List<SessionSummary> = emptyList(),
    val sessionsThisMonth: Int = 0,
    val totalHours: Double = 0.0
)

class HistoryViewModel(repo: GymRepository) : ViewModel() {

    val state: StateFlow<HistoryState> = combine(
        repo.finishedSessionsWithSetsFlow(),
        repo.profileFlow()
    ) { list, profile ->
        val bw = profile?.bodyweightKg ?: 75.0
        val summaries = list.map { sw ->
            val done = sw.sets.count { it.completed }
            SessionSummary(
                session = sw.session,
                volumeKg = WorkoutMath.volumeKg(sw.sets),
                kcal = WorkoutMath.caloriesKcal(sw.session, sw.sets, bw),
                sets = done,
                durationMin = WorkoutMath.durationMinutes(sw.session, done).roundToInt()
            )
        }
        val month = LocalDate.now().withDayOfMonth(1)
        HistoryState(
            sessions = summaries,
            sessionsThisMonth = summaries.count { epochToLocalDate(it.session.startedAt) >= month },
            totalHours = summaries.sumOf { it.durationMin } / 60.0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryState())

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory { initializer { HistoryViewModel(repo) } }
    }
}
