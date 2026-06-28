package com.aesthetic.gym.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.domain.model.Rank
import com.aesthetic.gym.domain.model.Sex
import com.aesthetic.gym.domain.rank.MuscleRank
import com.aesthetic.gym.domain.rank.RankCalculator
import com.aesthetic.gym.domain.rank.RankSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class BodyViewModel(private val repo: GymRepository) : ViewModel() {

    private val empty = RankSummary(
        perMuscle = MuscleGroup.ranked.map { MuscleRank(it, 0, Rank.PRINCIPIANTE, 0.0, false) },
        overallScore = 0,
        overallRank = Rank.PRINCIPIANTE
    )

    val summary: StateFlow<RankSummary> = combine(
        repo.profileFlow(),
        repo.setMuscleRowsFlow()
    ) { profile, rows ->
        RankCalculator.compute(rows, profile?.bodyweightKg ?: 75.0, profile?.sex ?: Sex.MALE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), empty)

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory { initializer { BodyViewModel(repo) } }
    }
}
