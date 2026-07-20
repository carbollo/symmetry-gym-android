package com.aesthetic.gym.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.SetLogEntity
import com.aesthetic.gym.data.db.WorkoutSessionEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.MeasureType
import com.aesthetic.gym.util.WorkoutMath
import com.aesthetic.gym.util.epley1RM
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

/** One exercise within a past session, with its sets in order. */
data class SessionExercise(
    val exerciseId: String,
    val name: String,
    val muscle: com.aesthetic.gym.domain.model.MuscleGroup?,
    val muscleName: String,
    val measure: MeasureType,
    val sets: List<SetLogEntity>
) {
    /** Best estimated 1RM across the completed working sets, for the "top set" line. */
    val bestSet: SetLogEntity? =
        sets.filter { it.completed && !it.isWarmup && it.measure == MeasureType.REPS }
            .maxByOrNull { epley1RM(it.weightKg, it.reps) }

    val volumeKg: Double = WorkoutMath.volumeKg(sets)
}

data class SessionDetailState(
    val loading: Boolean = true,
    val session: WorkoutSessionEntity? = null,
    val exercises: List<SessionExercise> = emptyList(),
    val durationMin: Int = 0,
    val kcal: Int = 0,
    val volumeKg: Double = 0.0,
    val sets: Int = 0,
    /** Days away rewarded here, mirrored from the session (null = no comeback bonus). */
    val comebackDays: Int? = null
) {
    val exerciseCount: Int get() = exercises.size
}

class SessionDetailViewModel(repo: GymRepository, sessionId: Long) : ViewModel() {

    val state: StateFlow<SessionDetailState> = combine(
        repo.sessionsWithSetsHot,
        repo.exercisesHot,
        repo.profileHot
    ) { sessions, catalog, profile ->
        val sw = sessions.firstOrNull { it.session.id == sessionId }
            ?: return@combine SessionDetailState(loading = false)

        val bw = profile?.bodyweightKg ?: 75.0
        val byName = catalog.associateBy { it.id }

        // Keep the order the exercises were first logged in.
        val order = sw.sets.map { it.exerciseId }.distinct()
        val exercises = order.map { id ->
            val ex = byName[id]
            SessionExercise(
                exerciseId = id,
                name = ex?.name ?: id,
                muscle = ex?.primaryMuscle,
                muscleName = ex?.primaryMuscle?.displayName ?: "",
                measure = sw.sets.firstOrNull { it.exerciseId == id }?.measure ?: MeasureType.REPS,
                sets = sw.sets.filter { it.exerciseId == id }.sortedBy { it.setNumber }
            )
        }

        val done = sw.sets.count { it.completed }
        SessionDetailState(
            loading = false,
            session = sw.session,
            exercises = exercises,
            durationMin = WorkoutMath.durationMinutes(sw.session, done).roundToInt(),
            kcal = WorkoutMath.caloriesKcal(sw.session, sw.sets, bw),
            volumeKg = WorkoutMath.volumeKg(sw.sets),
            sets = done,
            comebackDays = sw.session.comebackDays
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionDetailState())

    companion object {
        fun factory(repo: GymRepository, sessionId: Long) =
            viewModelFactory { initializer { SessionDetailViewModel(repo, sessionId) } }
    }
}
