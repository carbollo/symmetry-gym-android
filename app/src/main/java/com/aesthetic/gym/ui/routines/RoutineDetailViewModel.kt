package com.aesthetic.gym.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.RoutineWithDays
import com.aesthetic.gym.data.repo.GymRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutineDetailViewModel(
    private val repo: GymRepository,
    private val routineId: Long
) : ViewModel() {

    val routine: StateFlow<RoutineWithDays?> =
        repo.routineWithDaysFlow(routineId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
