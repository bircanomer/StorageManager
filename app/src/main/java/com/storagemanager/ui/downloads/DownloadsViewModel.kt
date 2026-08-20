package com.storagemanager.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.domain.model.DownloadJunk
import com.storagemanager.domain.model.DuplicateDocumentGroup
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.storagemanager.ui.components.localeCollator
import com.storagemanager.R

enum class DownloadsSortBy {
    SIZE_DESC, SIZE_ASC, DATE_DESC, DATE_ASC, NAME_ASC
}

sealed class DownloadsState {
    data object Loading : DownloadsState()
    data class Success(
        val duplicateDocs: List<DuplicateDocumentGroup>,
        val downloadJunks: List<DownloadJunk>,
        val sortBy: DownloadsSortBy = DownloadsSortBy.SIZE_DESC
    ) : DownloadsState()
    data class Error(val message: String) : DownloadsState()
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val repository: StorageRepository
) : ViewModel() {

    private val TAG = "DownloadsViewModel"

    private val _uiState = MutableStateFlow<DownloadsState>(DownloadsState.Loading)
    val uiState: StateFlow<DownloadsState> = _uiState.asStateFlow()

    init {
        loadCachedData()
    }

    private fun loadCachedData() {
        viewModelScope.launch {
            try {
                val cachedResult = repository.getCachedScanResults()
                if (cachedResult != null && (cachedResult.duplicateDocuments.isNotEmpty() || cachedResult.downloadJunks.isNotEmpty())) {
                    _uiState.value = DownloadsState.Success(
                        duplicateDocs = sortDuplicateDocs(cachedResult.duplicateDocuments, DownloadsSortBy.SIZE_DESC),
                        downloadJunks = sortDownloadJunks(cachedResult.downloadJunks, DownloadsSortBy.SIZE_DESC),
                        sortBy = DownloadsSortBy.SIZE_DESC
                    )
                } else {
                    loadData()
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Operation failed", e)
                loadData()
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            if (_uiState.value !is DownloadsState.Success) {
                _uiState.value = DownloadsState.Loading
            }
            try {
                val duplicateDocs = repository.scanDuplicateDocuments()
                val downloadJunks = repository.scanDownloadJunk()
                val currentSort = (_uiState.value as? DownloadsState.Success)?.sortBy ?: DownloadsSortBy.SIZE_DESC
                _uiState.value = DownloadsState.Success(
                    duplicateDocs = sortDuplicateDocs(duplicateDocs, currentSort),
                    downloadJunks = sortDownloadJunks(downloadJunks, currentSort),
                    sortBy = currentSort
                )
            } catch (e: Exception) {
                if (_uiState.value !is DownloadsState.Success) {
                    _uiState.value = DownloadsState.Error(e.localizedMessage ?: context.getString(R.string.unknown_error))
                }
            }
        }
    }

    fun setSortBy(sortBy: DownloadsSortBy) {
        val currentState = _uiState.value
        if (currentState is DownloadsState.Success) {
            _uiState.value = DownloadsState.Success(
                duplicateDocs = sortDuplicateDocs(currentState.duplicateDocs, sortBy),
                downloadJunks = sortDownloadJunks(currentState.downloadJunks, sortBy),
                sortBy = sortBy
            )
        }
    }

    fun moveToTrash(filePath: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is DownloadsState.Success) {
                val cleanJunks = currentState.downloadJunks.filterNot { it.path == filePath }
                val cleanDocs = currentState.duplicateDocs
                    .map { group ->
                        group.copy(files = group.files.filterNot { it.path == filePath })
                    }
                    .filter { group -> group.files.size >= 2 }

                _uiState.value = DownloadsState.Success(
                    duplicateDocs = sortDuplicateDocs(cleanDocs, currentState.sortBy),
                    downloadJunks = sortDownloadJunks(cleanJunks, currentState.sortBy),
                    sortBy = currentState.sortBy
                )
            }

            repository.moveToTrash(filePath, "DOCUMENT")
        }
    }

    fun deleteAllDuplicateCopies() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is DownloadsState.Success) {
                val filesToDelete = currentState.duplicateDocs.flatMap { it.files.drop(1) }
                if (filesToDelete.isEmpty()) return@launch

                val pathsToDelete = filesToDelete.map { it.path }.toSet()

                for (file in filesToDelete) {
                    repository.moveToTrash(file.path, "DOCUMENT")
                }

                val cleanJunks = currentState.downloadJunks.filterNot { it.path in pathsToDelete }
                val cleanDocs = currentState.duplicateDocs
                    .map { group ->
                        group.copy(
                            files = group.files.filterNot { it.path in pathsToDelete },
                            totalSize = group.files.filterNot { it.path in pathsToDelete }.sumOf { it.size }
                        )
                    }
                    .filter { group -> group.files.size >= 2 }

                _uiState.value = DownloadsState.Success(
                    duplicateDocs = sortDuplicateDocs(cleanDocs, currentState.sortBy),
                    downloadJunks = sortDownloadJunks(cleanJunks, currentState.sortBy),
                    sortBy = currentState.sortBy
                )
            }
        }
    }

    private fun sortDuplicateDocs(
        groups: List<DuplicateDocumentGroup>,
        sortBy: DownloadsSortBy
    ): List<DuplicateDocumentGroup> {
        return when (sortBy) {
            DownloadsSortBy.SIZE_DESC -> groups.sortedByDescending { it.totalSize }
            DownloadsSortBy.SIZE_ASC -> groups.sortedBy { it.totalSize }
            DownloadsSortBy.DATE_DESC -> groups.sortedByDescending { it.files.maxOfOrNull { f -> f.lastModified } ?: 0L }
            DownloadsSortBy.DATE_ASC -> groups.sortedBy { it.files.minOfOrNull { f -> f.lastModified } ?: 0L }
            DownloadsSortBy.NAME_ASC -> groups.sortedWith(compareBy(localeCollator()) { it.files.firstOrNull()?.name ?: "" })
        }
    }

    private fun sortDownloadJunks(
        junks: List<DownloadJunk>,
        sortBy: DownloadsSortBy
    ): List<DownloadJunk> {
        return when (sortBy) {
            DownloadsSortBy.SIZE_DESC -> junks.sortedByDescending { it.size }
            DownloadsSortBy.SIZE_ASC -> junks.sortedBy { it.size }
            DownloadsSortBy.DATE_DESC -> junks.sortedByDescending { it.lastModified }
            DownloadsSortBy.DATE_ASC -> junks.sortedBy { it.lastModified }
            DownloadsSortBy.NAME_ASC -> junks.sortedWith(compareBy(localeCollator()) { it.name })
        }
    }
}
