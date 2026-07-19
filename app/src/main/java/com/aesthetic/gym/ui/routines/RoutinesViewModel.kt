package com.aesthetic.gym.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.RoutineEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.util.epochToLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Weekly adherence: % of planned sets actually confirmed, plus per-day set counts. */
data class WeeklyEfficiency(val percent: Int = 0, val bars: List<Float> = List(7) { 0f })

class RoutinesViewModel(private val repo: GymRepository) : ViewModel() {

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
