package com.aesthetic.gym.ui.progress

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.BodyMetricEntity
import com.aesthetic.gym.data.db.BodyPhotoEntity
import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.data.repo.GymRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ProgressViewModel(private val repo: GymRepository) : ViewModel() {

    val photos: StateFlow<List<BodyPhotoEntity>> =
        repo.photosFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val metrics: StateFlow<List<BodyMetricEntity>> =
        repo.metricsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loggedExercises: StateFlow<List<ExerciseEntity>> = combine(
        repo.loggedExerciseIdsFlow(),
        repo.exercisesFlow
    ) { ids, all ->
        val set = ids.toSet()
        all.filter { it.id in set }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addWeight(kg: Double) {
        viewModelScope.launch { repo.addMetric(BodyMetricEntity(takenAt = repo.now(), weightKg = kg)) }
    }

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
