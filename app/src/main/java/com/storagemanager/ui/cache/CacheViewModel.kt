package com.storagemanager.ui.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.domain.model.CacheInfo
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CacheUiState(
    val cacheInfos: List<CacheInfo> = emptyList(),
    val totalCacheSize: Long = 0L,
    val isLoading: Boolean = true
)

@HiltViewModel
class CacheViewModel @Inject constructor(
    private val repository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CacheUiState())
    val uiState: StateFlow<CacheUiState> = _uiState.asStateFlow()

    init {
        loadCache()
    }

    fun loadCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val cacheInfos = repository.getCacheInfo()
                val total = cacheInfos.sumOf { it.cacheSize }
                _uiState.update {
                    it.copy(
                        cacheInfos = cacheInfos.sortedByDescending { c -> c.cacheSize },
                        totalCacheSize = total,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
