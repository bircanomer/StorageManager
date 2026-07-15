package com.storagemanager.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.domain.model.LargeFile
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortBy {
    SIZE_DESC, SIZE_ASC, DATE_DESC, DATE_ASC, NAME_ASC
}

data class FilesUiState(
    val largeFiles: List<LargeFile> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val sortBy: SortBy = SortBy.SIZE_DESC,
    val minSizeMB: Int = 50
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    init {
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val minBytes = _uiState.value.minSizeMB.toLong() * 1024L * 1024L
                val files = repository.scanLargeFiles(minBytes)
                _uiState.update { state ->
                    state.copy(
                        largeFiles = sortFiles(files, state.sortBy),
                        isLoading = false,
                        selectedPaths = emptySet()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleSelection(path: String) {
        _uiState.update { state ->
            val newSelection = if (path in state.selectedPaths) {
                state.selectedPaths - path
            } else {
                state.selectedPaths + path
            }
            state.copy(selectedPaths = newSelection)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedPaths = state.largeFiles.map { it.path }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet()) }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedFiles = state.largeFiles.filter { it.path in state.selectedPaths }
            if (selectedFiles.isEmpty()) return@launch

            repository.deleteFiles(selectedFiles).onSuccess {
                loadFiles()
            }
        }
    }

    fun setSortBy(sortBy: SortBy) {
        _uiState.update { state ->
            state.copy(
                sortBy = sortBy,
                largeFiles = sortFiles(state.largeFiles, sortBy)
            )
        }
    }

    fun setMinSize(mb: Int) {
        _uiState.update { it.copy(minSizeMB = mb) }
        loadFiles()
    }

    private fun sortFiles(files: List<LargeFile>, sortBy: SortBy): List<LargeFile> {
        return when (sortBy) {
            SortBy.SIZE_DESC -> files.sortedByDescending { it.size }
            SortBy.SIZE_ASC -> files.sortedBy { it.size }
            SortBy.DATE_DESC -> files.sortedByDescending { it.lastModified }
            SortBy.DATE_ASC -> files.sortedBy { it.lastModified }
            SortBy.NAME_ASC -> files.sortedBy { it.name.lowercase() }
        }
    }
}
