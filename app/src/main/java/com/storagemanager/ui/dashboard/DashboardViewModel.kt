package com.storagemanager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.domain.model.ScanResult
import com.storagemanager.domain.model.ScanState
import com.storagemanager.domain.model.StorageInfo
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: StorageRepository
) : ViewModel() {

    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    init {
        loadStorageInfo()
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
                // Sessiz hata — UI varsayılan değerleri gösterir
            }
        }
    }

    /**
     * Tam taramayı başlatır ve ilerlemeyi [scanState] üzerinden yayınlar.
     */
    fun startScan() {
        if (_scanState.value is ScanState.Scanning) return

        viewModelScope.launch {
            try {
                _scanState.value = ScanState.Scanning(progress = 0f, currentTask = "Hazırlanıyor…")

                val result = repository.fullScan { progress, task ->
                    _scanState.value = ScanState.Scanning(progress = progress, currentTask = task)
                }

                // Tarama sonrası storage bilgisini güncelle
                _storageInfo.value = result.storageInfo
                _scanState.value = ScanState.Completed(result)
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(
                    message = e.localizedMessage ?: "Bilinmeyen hata oluştu"
                )
            }
        }
    }
}
