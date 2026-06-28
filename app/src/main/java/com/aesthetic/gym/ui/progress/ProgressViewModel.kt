package com.aesthetic.gym.ui.progress

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.BodyMetricEntity
import com.aesthetic.gym.data.db.BodyPhotoEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.util.WorkoutMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class WeeklyStats(val workouts: Int = 0, val volumeKg: Double = 0.0, val kcal: Int = 0)

class ProgressViewModel(private val repo: GymRepository) : ViewModel() {

    val photos: StateFlow<List<BodyPhotoEntity>> =
        repo.photosFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val metrics: StateFlow<List<BodyMetricEntity>> =
        repo.metricsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekly: StateFlow<WeeklyStats> = combine(
        repo.finishedSessionsWithSetsFlow(),
        repo.profileFlow()
    ) { list, profile ->
        val bw = profile?.bodyweightKg ?: 75.0
        val weekAgo = repo.now() - 7L * 24 * 3600 * 1000
        val recent = list.filter { it.session.startedAt >= weekAgo }
        WeeklyStats(
            workouts = recent.size,
            volumeKg = recent.sumOf { WorkoutMath.volumeKg(it.sets) },
            kcal = recent.sumOf { WorkoutMath.caloriesKcal(it.session, it.sets, bw) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyStats())

    fun addWeight(kg: Double) {
        viewModelScope.launch { repo.addMetric(BodyMetricEntity(takenAt = repo.now(), weightKg = kg)) }
    }

    fun deleteWeight(id: Long) = viewModelScope.launch { repo.deleteMetric(id) }

    fun addPhoto(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "photo_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                repo.addPhoto(BodyPhotoEntity(filePath = file.absolutePath, takenAt = repo.now()))
            } catch (_: Exception) {
            }
        }
    }

    fun deletePhoto(photo: BodyPhotoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(photo.filePath).delete()
            } catch (_: Exception) {
            }
            repo.deletePhoto(photo.id)
        }
    }

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory { initializer { ProgressViewModel(repo) } }
    }
}
