package com.aesthetic.gym.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.RoutineEntity
import com.aesthetic.gym.data.repo.GymRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutinesViewModel(private val repo: GymRepository) : ViewModel() {

    val routines: StateFlow<List<RoutineEntity>> =
        repo.routinesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setActive(id: Long) = viewModelScope.launch { repo.setActiveRoutine(id) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteRoutine(id) }

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory { initializer { RoutinesViewModel(repo) } }
    }
}
