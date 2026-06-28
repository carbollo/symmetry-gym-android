package com.aesthetic.gym.ui.routines

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.RoutineSource
import com.aesthetic.gym.pdf.ImportResult
import com.aesthetic.gym.pdf.PdfTextExtractor
import com.aesthetic.gym.pdf.RoutineImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val importing: Boolean = false,
    val result: ImportResult? = null,
    val error: String? = null
)

class ImportViewModel(
    private val repo: GymRepository,
    private val importer: RoutineImporter
) : ViewModel() {

    var ui by mutableStateOf(ImportUiState())
        private set

    fun importPdf(context: Context, uri: Uri) {
        ui = ImportUiState(importing = true)
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { PdfTextExtractor.extract(context, uri) }
                val name = PdfTextExtractor.displayName(context, uri)
                val result = importer.import(text, name, RoutineSource.PDF)
                ui = ImportUiState(result = result)
            } catch (e: Exception) {
                ui = ImportUiState(error = e.message ?: "No se pudo leer el PDF")
            }
        }
    }

    fun importText(text: String, name: String) {
        if (text.isBlank()) {
            ui = ImportUiState(error = "Pega el texto de tu rutina primero")
            return
        }
        ui = ImportUiState(importing = true)
        viewModelScope.launch {
            try {
                val result = importer.import(
                    text,
                    name.ifBlank { "Rutina importada" },
                    RoutineSource.TEXT
                )
                ui = ImportUiState(result = result)
            } catch (e: Exception) {
                ui = ImportUiState(error = e.message ?: "No se pudo procesar el texto")
            }
        }
    }

    fun activate(routineId: Long) = viewModelScope.launch { repo.setActiveRoutine(routineId) }

    fun reset() { ui = ImportUiState() }

    companion object {
        fun factory(repo: GymRepository, importer: RoutineImporter) =
            viewModelFactory { initializer { ImportViewModel(repo, importer) } }
    }
}
