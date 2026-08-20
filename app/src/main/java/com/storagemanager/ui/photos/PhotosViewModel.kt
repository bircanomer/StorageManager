package com.storagemanager.ui.photos

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.domain.model.JunkPhoto
import com.storagemanager.domain.model.JunkType
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PhotosSortBy {
    DATE_DESC, DATE_ASC, SIZE_DESC, SIZE_ASC
}

data class PhotosUiState(
    val blurryPhotos: List<JunkPhoto> = emptyList(),
    val duplicatePhotos: List<JunkPhoto> = emptyList(),
    val screenshots: List<JunkPhoto> = emptyList(),
    val oldPhotos: List<JunkPhoto> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val selectedTab: JunkType = JunkType.BLURRY,
    val sortBy: PhotosSortBy = PhotosSortBy.DATE_DESC
)

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val application: Application,
    private val repository: StorageRepository
) : ViewModel() {

    private val TAG = "PhotosViewModel"

    private val _uiState = MutableStateFlow(PhotosUiState())
    val uiState: StateFlow<PhotosUiState> = _uiState.asStateFlow()

    private val _deleteIntentSender = MutableSharedFlow<IntentSender>()
    val deleteIntentSender: SharedFlow<IntentSender> = _deleteIntentSender.asSharedFlow()

    /** Seçili sekmedeki fotoğraf listesini döndürür. */
    val currentTabPhotos: List<JunkPhoto>
        get() = when (_uiState.value.selectedTab) {
            JunkType.BLURRY -> _uiState.value.blurryPhotos
            JunkType.DUPLICATE -> _uiState.value.duplicatePhotos
            JunkType.SCREENSHOT -> _uiState.value.screenshots
            JunkType.OLD -> _uiState.value.oldPhotos
        }

    init {
        loadCachedPhotos()
    }

    private fun sortPhotos(photos: List<JunkPhoto>, sortBy: PhotosSortBy, tab: JunkType): List<JunkPhoto> {
        if (tab == JunkType.DUPLICATE) {
            val grouped = photos.groupBy { it.groupId ?: it.id.toString() }
            val sortedGroups = when (sortBy) {
                PhotosSortBy.DATE_DESC -> grouped.entries.sortedByDescending { it.value.maxOfOrNull { p -> p.dateModified } ?: 0L }
                PhotosSortBy.DATE_ASC -> grouped.entries.sortedBy { it.value.minOfOrNull { p -> p.dateModified } ?: 0L }
                PhotosSortBy.SIZE_DESC -> grouped.entries.sortedByDescending { it.value.sumOf { p -> p.size } }
                PhotosSortBy.SIZE_ASC -> grouped.entries.sortedBy { it.value.sumOf { p -> p.size } }
            }
            return sortedGroups.flatMap { it.value }
        } else {
            return when (sortBy) {
                PhotosSortBy.DATE_DESC -> photos.sortedByDescending { it.dateModified }
                PhotosSortBy.DATE_ASC -> photos.sortedBy { it.dateModified }
                PhotosSortBy.SIZE_DESC -> photos.sortedByDescending { it.size }
                PhotosSortBy.SIZE_ASC -> photos.sortedBy { it.size }
            }
        }
    }

    private fun updatePhotosState(photos: List<JunkPhoto>, sortBy: PhotosSortBy) {
        val blurry = photos.filter { it.junkType == JunkType.BLURRY }
        val duplicates = photos.filter { it.junkType == JunkType.DUPLICATE }
        val screenshots = photos.filter { it.junkType == JunkType.SCREENSHOT }
        val old = photos.filter { it.junkType == JunkType.OLD }

        _uiState.update {
            it.copy(
                blurryPhotos = sortPhotos(blurry, sortBy, JunkType.BLURRY),
                duplicatePhotos = sortPhotos(duplicates, sortBy, JunkType.DUPLICATE),
                screenshots = sortPhotos(screenshots, sortBy, JunkType.SCREENSHOT),
                oldPhotos = sortPhotos(old, sortBy, JunkType.OLD),
                sortBy = sortBy,
                isLoading = false
            )
        }
    }

    private fun loadCachedPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val cachedResult = repository.getCachedScanResults()
                if (cachedResult != null && cachedResult.junkPhotos.isNotEmpty()) {
                    updatePhotosState(cachedResult.junkPhotos, _uiState.value.sortBy)
                } else {
                    loadPhotos()
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Operation failed", e)
                loadPhotos()
            }
        }
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val photos = repository.scanJunkPhotos()
                updatePhotosState(photos, _uiState.value.sortBy)
                _uiState.update { it.copy(selectedIds = emptySet()) }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Operation failed", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleSelection(photoId: Long) {
        _uiState.update { state ->
            val newSelection = if (photoId in state.selectedIds) {
                state.selectedIds - photoId
            } else {
                state.selectedIds + photoId
            }
            state.copy(selectedIds = newSelection)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allIds = currentTabPhotos.map { it.id }.toSet()
            state.copy(selectedIds = state.selectedIds + allIds)
        }
    }

    fun selectDuplicateCopies() {
        _uiState.update { state ->
            val grouped = state.duplicatePhotos.groupBy { it.groupId ?: it.id.toString() }
            val idsToSelect = mutableSetOf<Long>()
            for ((_, group) in grouped) {
                if (group.size > 1) {
                    val copies = group.drop(1)
                    idsToSelect.addAll(copies.map { it.id })
                }
            }
            state.copy(selectedIds = state.selectedIds + idsToSelect)
        }
    }

    fun selectGroupCopies(groupPhotos: List<JunkPhoto>) {
        if (groupPhotos.size <= 1) return
        _uiState.update { state ->
            val copiesIds = groupPhotos.drop(1).map { it.id }.toSet()
            state.copy(selectedIds = state.selectedIds + copiesIds)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    private var pendingDeleteIds: Set<Long> = emptySet()
    private var pendingDeletePaths: Set<String> = emptySet()

    fun deletePhotosList(photosToDelete: List<JunkPhoto>) {
        viewModelScope.launch {
            if (photosToDelete.isEmpty()) return@launch
            _uiState.update { it.copy(isDeleting = true) }
            var systemTrashRequested = false
            try {
                val targetIds = photosToDelete.map { it.id }.toSet()
                val targetPaths = photosToDelete.map { it.path }.toSet()
                pendingDeleteIds = targetIds
                pendingDeletePaths = targetPaths

                val result = repository.deletePhotos(photosToDelete)
                if (result.isSuccess) {
                    removeDeletedFromState(targetIds, targetPaths)
                    pendingDeleteIds = emptySet()
                    pendingDeletePaths = emptySet()
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val uris = photosToDelete.map { Uri.parse(it.uri) }
                            val pendingIntent = MediaStore.createTrashRequest(application.contentResolver, uris, true)
                            systemTrashRequested = true
                            _deleteIntentSender.emit(pendingIntent.intentSender)
                        } catch (e: Exception) {
                            android.util.Log.w(TAG, "Operation failed", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Operation failed", e)
            } finally {
                if (!systemTrashRequested) {
                    _uiState.update { it.copy(isDeleting = false) }
                }
            }
        }
    }

    fun deleteSelected() {
        val state = _uiState.value
        val selectedPhotos = listOf(
            state.blurryPhotos,
            state.duplicatePhotos,
            state.screenshots,
            state.oldPhotos
        ).flatten().filter { it.id in state.selectedIds }

        deletePhotosList(selectedPhotos)
    }

    fun deleteSinglePhoto(photo: JunkPhoto) {
        deletePhotosList(listOf(photo))
    }

    fun onPhotosDeletedSuccessfully() {
        val idsToRemove = pendingDeleteIds + _uiState.value.selectedIds
        val pathsToRemove = pendingDeletePaths
        removeDeletedFromState(idsToRemove, pathsToRemove)
        pendingDeleteIds = emptySet()
        pendingDeletePaths = emptySet()
    }

    fun onPhotosDeleteCancelled() {
        pendingDeleteIds = emptySet()
        pendingDeletePaths = emptySet()
        _uiState.update { it.copy(isDeleting = false) }
    }

    private fun removeDeletedFromState(deletedIds: Set<Long>, deletedPaths: Set<String> = emptySet()) {
        _uiState.update { state ->
            val isPhotoDeleted: (JunkPhoto) -> Boolean = { p ->
                (p.id in deletedIds) || (p.path in deletedPaths)
            }

            val cleanBlurry = state.blurryPhotos.filterNot(isPhotoDeleted)
            val cleanScreenshots = state.screenshots.filterNot(isPhotoDeleted)
            val cleanOld = state.oldPhotos.filterNot(isPhotoDeleted)

            val cleanDuplicates = state.duplicatePhotos
                .filterNot(isPhotoDeleted)
                .groupBy { it.groupId ?: it.id.toString() }
                .filterValues { group -> group.size >= 2 }
                .values.flatten()

            state.copy(
                blurryPhotos = sortPhotos(cleanBlurry, state.sortBy, JunkType.BLURRY),
                duplicatePhotos = sortPhotos(cleanDuplicates, state.sortBy, JunkType.DUPLICATE),
                screenshots = sortPhotos(cleanScreenshots, state.sortBy, JunkType.SCREENSHOT),
                oldPhotos = sortPhotos(cleanOld, state.sortBy, JunkType.OLD),
                selectedIds = state.selectedIds - deletedIds,
                isDeleting = false
            )
        }
        viewModelScope.launch {
            if (deletedIds.isNotEmpty()) {
                repository.removePhotosFromCache(deletedIds)
            }
        }
    }

    fun setTab(tab: JunkType) {
        _uiState.update { state ->
            state.copy(selectedTab = tab)
        }
    }

    fun setSortBy(sortBy: PhotosSortBy) {
        _uiState.update { state ->
            state.copy(
                blurryPhotos = sortPhotos(state.blurryPhotos, sortBy, JunkType.BLURRY),
                duplicatePhotos = sortPhotos(state.duplicatePhotos, sortBy, JunkType.DUPLICATE),
                screenshots = sortPhotos(state.screenshots, sortBy, JunkType.SCREENSHOT),
                oldPhotos = sortPhotos(state.oldPhotos, sortBy, JunkType.OLD),
                sortBy = sortBy
            )
        }
    }
}
