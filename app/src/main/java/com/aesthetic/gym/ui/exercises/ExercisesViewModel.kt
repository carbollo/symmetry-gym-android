package com.aesthetic.gym.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.data.repo.GymRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ExercisesViewModel(repo: GymRepository) : ViewModel() {

    val exercises: StateFlow<List<ExerciseEntity>> =
        repo.exercisesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory { initializer { ExercisesViewModel(repo) } }
    }
}
