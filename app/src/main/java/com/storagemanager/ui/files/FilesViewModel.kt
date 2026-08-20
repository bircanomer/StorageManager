package com.storagemanager.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.data.preferences.ScanSettings
import com.storagemanager.domain.model.LargeFile
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.storagemanager.ui.components.localeCollator

enum class SortBy {
    SIZE_DESC, SIZE_ASC, DATE_DESC, DATE_ASC, NAME_ASC
}

enum class FileCategoryFilter(@androidx.annotation.StringRes val labelRes: Int) {
    ALL(com.storagemanager.R.string.category_all),
    VIDEO(com.storagemanager.R.string.category_video),
    IMAGE(com.storagemanager.R.string.category_image),
    AUDIO(com.storagemanager.R.string.category_audio),
    DOCS(com.storagemanager.R.string.category_document),
    OTHER(com.storagemanager.R.string.category_other)
}

data class FilesUiState(
    val largeFiles: List<LargeFile> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val sortBy: SortBy = SortBy.SIZE_DESC,
    val minSizeMB: Int = 50,
    val selectedCategory: FileCategoryFilter = FileCategoryFilter.ALL
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: StorageRepository,
    private val scanSettings: ScanSettings
) : ViewModel() {

    private val TAG = "FilesViewModel"

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Ayarlardaki minimum dosya boyutunu başlangıç değeri olarak al
            val configured = scanSettings.current().minLargeFileSizeMb
            _uiState.update { it.copy(minSizeMB = configured) }
            loadCachedFiles()
        }
    }

    private suspend fun loadCachedFiles() {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val cachedResult = repository.getCachedScanResults()
                if (cachedResult != null && cachedResult.largeFiles.isNotEmpty()) {
                    _uiState.update { state ->
                        state.copy(
                            largeFiles = sortFiles(cachedResult.largeFiles, state.sortBy),
                            isLoading = false
                        )
                    }
                } else {
                    loadFiles()
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Önbellekli dosyalar okunamadı", e)
                loadFiles()
            }
    }

    fun loadFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Kullanıcının seçtiği eşik gerçekten taramaya iletiliyor (eskiden sabit 10 MB'tı)
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
                android.util.Log.w(TAG, "Operation failed", e)
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

    fun removeFilesFromState(pathsToRemove: Set<String>) {
        _uiState.update { state ->
            val updatedLargeFiles = state.largeFiles.filterNot { it.path in pathsToRemove }
            state.copy(
                largeFiles = updatedLargeFiles,
                selectedPaths = state.selectedPaths - pathsToRemove
            )
        }
    }

    fun deleteSingleFile(path: String) {
        viewModelScope.launch {
            val fileToDelete = _uiState.value.largeFiles.firstOrNull { it.path == path } ?: return@launch
            _uiState.update { it.copy(isDeleting = true) }
            try {
                removeFilesFromState(setOf(path))
                repository.deleteFiles(listOf(fileToDelete))
            } finally {
                _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedFiles = state.largeFiles.filter { it.path in state.selectedPaths }
            if (selectedFiles.isEmpty()) return@launch

            _uiState.update { it.copy(isDeleting = true) }
            try {
                val pathsToDelete = selectedFiles.map { it.path }.toSet()
                removeFilesFromState(pathsToDelete)
                repository.deleteFiles(selectedFiles)
            } finally {
                _uiState.update { it.copy(isDeleting = false) }
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

    /**
     * Minimum dosya boyutu filtresini değiştirir.
     *
     * Eşik mevcut taramanın alt sınırından küçükse (ör. 50 MB ile tarandı, kullanıcı
     * 10 MB seçti) elde veri olmadığı için yeniden tarama gerekir.
     */
    fun setMinSize(mb: Int) {
        val previous = _uiState.value.minSizeMB
        _uiState.update { it.copy(minSizeMB = mb) }
        viewModelScope.launch { scanSettings.setMinFileSizeMb(mb) }

        if (mb < previous || _uiState.value.largeFiles.isEmpty()) {
            loadFiles()
        }
    }

    fun setCategoryFilter(category: FileCategoryFilter) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    private fun sortFiles(files: List<LargeFile>, sortBy: SortBy): List<LargeFile> {
        return when (sortBy) {
            SortBy.SIZE_DESC -> files.sortedByDescending { it.size }
            SortBy.SIZE_ASC -> files.sortedBy { it.size }
            SortBy.DATE_DESC -> files.sortedByDescending { it.lastModified }
            SortBy.DATE_ASC -> files.sortedBy { it.lastModified }
            SortBy.NAME_ASC -> files.sortedWith(compareBy(localeCollator()) { it.name })
        }
    }
}
