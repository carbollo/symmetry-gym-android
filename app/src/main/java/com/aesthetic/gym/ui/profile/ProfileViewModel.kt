package com.aesthetic.gym.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.ProfileEntity
import com.aesthetic.gym.data.repo.GymRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: GymRepository) : ViewModel() {

    val profile: StateFlow<ProfileEntity?> =
        repo.profileHot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun save(profile: ProfileEntity) {
        viewModelScope.launch {
            val createdAt = profile.createdAt.takeIf { it != 0L } ?: repo.now()
            repo.saveProfile(profile.copy(id = 1, createdAt = createdAt, onboarded = true))
        }
    }

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory { initializer { ProfileViewModel(repo) } }
    }
}
