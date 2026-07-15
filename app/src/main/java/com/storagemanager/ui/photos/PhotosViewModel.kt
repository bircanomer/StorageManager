package com.storagemanager.ui.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.domain.model.JunkPhoto
import com.storagemanager.domain.model.JunkType
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhotosUiState(
    val blurryPhotos: List<JunkPhoto> = emptyList(),
    val duplicatePhotos: List<JunkPhoto> = emptyList(),
    val screenshots: List<JunkPhoto> = emptyList(),
    val oldPhotos: List<JunkPhoto> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val selectedTab: JunkType = JunkType.BLURRY
)

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val repository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotosUiState())
    val uiState: StateFlow<PhotosUiState> = _uiState.asStateFlow()

    /** Seçili sekmedeki fotoğraf listesini döndürür. */
    val currentTabPhotos: List<JunkPhoto>
        get() = when (_uiState.value.selectedTab) {
            JunkType.BLURRY -> _uiState.value.blurryPhotos
            JunkType.DUPLICATE -> _uiState.value.duplicatePhotos
            JunkType.SCREENSHOT -> _uiState.value.screenshots
            JunkType.OLD -> _uiState.value.oldPhotos
        }

    init {
        loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val photos = repository.scanJunkPhotos()
                _uiState.update {
                    it.copy(
                        blurryPhotos = photos.filter { p -> p.junkType == JunkType.BLURRY },
                        duplicatePhotos = photos.filter { p -> p.junkType == JunkType.DUPLICATE },
                        screenshots = photos.filter { p -> p.junkType == JunkType.SCREENSHOT },
                        oldPhotos = photos.filter { p -> p.junkType == JunkType.OLD },
                        isLoading = false,
                        selectedIds = emptySet()
                    )
                }
            } catch (e: Exception) {
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

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedPhotos = listOf(
                state.blurryPhotos,
                state.duplicatePhotos,
                state.screenshots,
                state.oldPhotos
            ).flatten().filter { it.id in state.selectedIds }

            if (selectedPhotos.isEmpty()) return@launch

            repository.deletePhotos(selectedPhotos).onSuccess {
                loadPhotos()
            }
        }
    }

    fun setTab(tab: JunkType) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
}
