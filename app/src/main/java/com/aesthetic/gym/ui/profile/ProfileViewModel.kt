package com.aesthetic.gym.ui.profile

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.db.ProfileEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.util.CsvExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(private val repo: GymRepository) : ViewModel() {

    val profile: StateFlow<ProfileEntity?> =
        repo.profileHot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun save(profile: ProfileEntity) {
        viewModelScope.launch {
            val createdAt = profile.createdAt.takeIf { it != 0L } ?: repo.now()
            repo.saveProfile(profile.copy(id = 1, createdAt = createdAt, onboarded = true))
        }
    }

    /**
     * Writes the whole training history as CSV to the location the user picked.
     * Returns the number of exported rows, or null if it failed.
     */
    suspend fun exportCsv(resolver: ContentResolver, uri: Uri): Int? = withContext(Dispatchers.IO) {
        try {
            val rows = repo.exportRows()
            resolver.openOutputStream(uri)?.use { out ->
                out.write(CsvExport.build(rows).toByteArray(Charsets.UTF_8))
            } ?: return@withContext null
            rows.size
        } catch (_: Exception) {
            null
        }
    }

    fun suggestedFileName(): String = CsvExport.suggestedFileName(repo.now())

    /** Persists only the reminder fields (saved instantly, not via the "Save profile" button). */
    fun updateReminders(profile: ProfileEntity) {
        viewModelScope.launch { repo.saveProfile(profile.copy(id = 1)) }
    }

    companion object {
        fun factory(repo: GymRepository) = viewModelFactory { initializer { ProfileViewModel(repo) } }
    }
}
