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
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class CacheViewModel @Inject constructor(
    private val repository: StorageRepository
) : ViewModel() {

    private val TAG = "CacheViewModel"

    private val _uiState = MutableStateFlow(CacheUiState())
    val uiState: StateFlow<CacheUiState> = _uiState.asStateFlow()

    init {
        loadCachedThenRefreshIfNeeded()
    }

    /**
     * Önce son taramanın önbelleğe alınmış sonucunu gösterir.
     *
     * Her ekran açılışında yüzlerce uygulama için StorageStats sorgusu yapmak
     * (uygulama başına bir binder çağrısı) gözle görülür bir gecikme yaratıyordu.
     */
    private fun loadCachedThenRefreshIfNeeded() {
        viewModelScope.launch {
            try {
                val cached = repository.getCachedScanResults()?.cacheInfos.orEmpty()
                if (cached.isNotEmpty()) {
                    publish(cached, isLoading = false)
                    return@launch
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Önbellekli veriler okunamadı", e)
            }
            loadCache()
        }
    }

    /** Cihazdan taze önbellek bilgisi okur. */
    fun loadCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoading = it.cacheInfos.isEmpty()) }
            try {
                publish(repository.getCacheInfo(), isLoading = false)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Önbellek bilgisi alınamadı", e)
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    private fun publish(cacheInfos: List<CacheInfo>, isLoading: Boolean) {
        val sorted = cacheInfos.sortedByDescending { it.cacheSize }
        _uiState.update {
            it.copy(
                cacheInfos = sorted,
                totalCacheSize = sorted.sumOf { info -> info.cacheSize },
                isLoading = isLoading,
                isRefreshing = false
            )
        }
    }
}
