package com.storagemanager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.domain.model.ScanCategory
import com.storagemanager.domain.model.ScanResult
import com.storagemanager.domain.model.ScanState
import com.storagemanager.domain.model.StorageInfo
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.storagemanager.R

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val repository: StorageRepository,
    private val analytics: com.storagemanager.analytics.Analytics
) : ViewModel() {

    private val TAG = "DashboardVM"

    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _selectedCategories = MutableStateFlow<Set<ScanCategory>>(ScanCategory.entries.toSet())
    val selectedCategories: StateFlow<Set<ScanCategory>> = _selectedCategories.asStateFlow()

    init {
        loadCachedData()
    }

    private fun loadCachedData() {
        viewModelScope.launch {
            try {
                // Önce Room'daki önbellekli depolama verilerini yükle
                val cachedInfo = repository.getCachedStorageInfo()
                if (cachedInfo != null) {
                    _storageInfo.value = cachedInfo
                } else {
                    loadStorageInfo()
                }

                // Room'daki son taranan sonuçları yükle
                val cachedResults = repository.getCachedScanResults()
                if (cachedResults != null) {
                    _scanState.value = ScanState.Completed(cachedResults)
                }
            } catch (e: Exception) {
                loadStorageInfo()
            }
        }
    }

    /**
     * Cihazın temel depolama bilgisini (full scan olmadan) yükler.
     */
    fun loadStorageInfo() {
        viewModelScope.launch {
            try {
                val info = repository.getStorageInfo()
                _storageInfo.value = info
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to load storage info", e)
            }
        }
    }

    fun toggleCategory(category: ScanCategory) {
        _selectedCategories.update { current ->
            if (category in current) {
                if (current.size > 1) current - category else current
            } else {
                current + category
            }
        }
    }

    fun selectAllCategories() {
        _selectedCategories.value = ScanCategory.entries.toSet()
    }

    fun clearAllCategories() {
        _selectedCategories.value = setOf(ScanCategory.PHOTOS)
    }

    /**
     * Taramayı başlatır ve ilerlemeyi [scanState] üzerinden yayınlar.
     */
    fun startScan(categories: Set<ScanCategory> = _selectedCategories.value) {
        if (_scanState.value is ScanState.Scanning) return

        viewModelScope.launch {
            try {
                // Tarama başlarken önceki sonucu taşı. Aksi halde `Scanning(partial = null)`
                // ekrandaki `Completed` durumunun üzerine yazıyor ve kartlar sıfıra düşüyordu:
                // kapsam dışı kategoriler silinmiş gibi görünüyordu. En belirgin hâli yalnızca
                // fotoğraf taraması seçildiğinde, çünkü ilk publishPartial() dakikalarca gelmiyor.
                // Veri hep yerindeydi; sorun yalnızca görüntülemedeydi.
                var partial: ScanResult? = (_scanState.value as? ScanState.Completed)?.result
                    ?: runCatching { repository.getCachedScanResults() }.getOrNull()

                _scanState.value = ScanState.Scanning(
                    progress = 0f,
                    currentTask = context.getString(R.string.scan_preparing),
                    partial = partial
                )
                analytics.logEvent(
                    com.storagemanager.analytics.Analytics.EVENT_SCAN_STARTED,
                    mapOf(com.storagemanager.analytics.Analytics.PARAM_CATEGORY to categories.size)
                )

                // Biten kategorilerin sonucu tarama sürerken de ekrana yansır; böylece
                // uzun süren foto taraması beklenmeden büyük dosyalar vb. görünür.
                val result = repository.customScan(
                    categories = categories,
                    onProgress = { progress, task ->
                        _scanState.value = ScanState.Scanning(
                            progress = progress,
                            currentTask = task,
                            partial = partial
                        )
                    },
                    onPartialResult = { snapshot ->
                        partial = snapshot
                        _storageInfo.value = snapshot.storageInfo
                        _scanState.value = (_scanState.value as? ScanState.Scanning)
                            ?.copy(partial = snapshot)
                            ?: ScanState.Scanning(partial = snapshot)
                    }
                )

                // Tarama sonrası storage bilgisini güncelle
                _storageInfo.value = result.storageInfo
                _scanState.value = ScanState.Completed(result)
                analytics.logEvent(
                    com.storagemanager.analytics.Analytics.EVENT_SCAN_COMPLETED,
                    mapOf(
                        com.storagemanager.analytics.Analytics.PARAM_FREED_BYTES to result.totalCleanableSize,
                        com.storagemanager.analytics.Analytics.PARAM_ITEM_COUNT to result.junkPhotos.size
                    )
                )
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(
                    message = e.localizedMessage ?: context.getString(R.string.unknown_error)
                )
            }
        }
    }
}
