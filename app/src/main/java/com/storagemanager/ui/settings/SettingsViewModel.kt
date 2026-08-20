package com.storagemanager.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.data.preferences.ProEntitlement
import com.storagemanager.data.preferences.ScanSettings
import com.storagemanager.domain.model.JunkPhoto
import com.storagemanager.domain.repository.StorageRepository
import com.storagemanager.worker.AutoScanScheduler
import kotlinx.coroutines.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val minPhotoBlurThreshold: Float = ScanSettings.Defaults.BLUR_THRESHOLD,
    val oldPhotoDays: Int = ScanSettings.Defaults.OLD_PHOTO_DAYS,
    val unusedAppDays: Int = ScanSettings.Defaults.UNUSED_APP_DAYS,
    val minLargeFileSizeMB: Int = ScanSettings.Defaults.MIN_FILE_SIZE_MB,
    val autoScanEnabled: Boolean = ScanSettings.Defaults.AUTO_SCAN,
    val aiClassificationEnabled: Boolean = ScanSettings.Defaults.AI_CLASSIFICATION,
    /** Netlik eşiği önizlemesinin durumu. */
    val blurPreview: BlurPreviewState = BlurPreviewState.Idle,
    /** Geliştirme derlemesinde Pro'yu elle açan bayrak. */
    val debugProEnabled: Boolean = false
)

/**
 * Eşiği ayarlarken "bu değer neyi işaretler" sorusunu tüm taramayı beklemeden
 * cevaplayan önizleme.
 */
sealed interface BlurPreviewState {
    data object Idle : BlurPreviewState
    data object Running : BlurPreviewState
    data class Ready(
        val flagged: List<JunkPhoto>,
        val sampleSize: Int,
        val threshold: Float
    ) : BlurPreviewState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val scanSettings: ScanSettings,
    private val repository: StorageRepository,
    private val proEntitlement: ProEntitlement,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            scanSettings.snapshots.collectLatest { snapshot ->
                // copy() kullanılıyor: eşik kaydırıldığında DataStore akışı yeniden yayın
                // yapıyor ve yeni bir SettingsUiState kurmak açık önizlemeyi kapatırdı.
                _uiState.update { state ->
                    state.copy(
                        minPhotoBlurThreshold = snapshot.blurThreshold,
                        oldPhotoDays = snapshot.oldPhotoDays,
                        unusedAppDays = snapshot.unusedAppDays,
                        minLargeFileSizeMB = snapshot.minLargeFileSizeMb,
                        autoScanEnabled = snapshot.autoScanEnabled,
                        aiClassificationEnabled = snapshot.aiClassificationEnabled
                    )
                }
            }
        }
        viewModelScope.launch {
            proEntitlement.debugPro.collectLatest { enabled ->
                _uiState.update { it.copy(debugProEnabled = enabled) }
            }
        }
    }

    /**
     * Pro'yu geliştirme derlemesinde açar/kapatır.
     *
     * Play'in yazdığı `is_pro` yerine ayrı bir bayrak kullanılır; aksi halde bir
     * sonraki açılışta satın alma doğrulaması değeri ezerdi.
     */
    fun toggleDebugPro() {
        val newValue = !_uiState.value.debugProEnabled
        viewModelScope.launch { proEntitlement.setDebugPro(newValue) }
    }

    // DataStore akışı UI durumunu geri beslediği için ayrıca _uiState güncellemeye gerek yok.
    fun updateBlurThreshold(value: Float) = viewModelScope.launch {
        scanSettings.setBlurThreshold(value)
    }

    fun updateOldPhotoDays(days: Int) = viewModelScope.launch {
        scanSettings.setOldPhotoDays(days)
    }

    fun updateUnusedAppDays(days: Int) = viewModelScope.launch {
        scanSettings.setUnusedAppDays(days)
    }

    fun updateMinFileSize(mb: Int) = viewModelScope.launch {
        scanSettings.setMinFileSizeMb(mb)
    }

    private companion object {
        /** Önizleme örneklemi: ~200 fotoğraf birkaç saniyede biter, eşik hakkında fikir verir. */
        const val BLUR_PREVIEW_SAMPLE = 200
    }

    private var previewJob: Job? = null

    /**
     * Mevcut eşiği rastgele bir örneklemde dener ve işaretlenen fotoğrafları döndürür.
     *
     * Eşik kaydırıldıkça yeniden çağrılabilir; önceki çalışma iptal edilir, böylece
     * arka arkaya tetiklenen önizlemeler birikmez.
     */
    fun runBlurPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(blurPreview = BlurPreviewState.Running) }
            val threshold = _uiState.value.minPhotoBlurThreshold
            val flagged = repository.previewBlurryPhotos(BLUR_PREVIEW_SAMPLE, threshold)
            _uiState.update {
                it.copy(
                    blurPreview = BlurPreviewState.Ready(
                        flagged = flagged,
                        sampleSize = BLUR_PREVIEW_SAMPLE,
                        threshold = threshold
                    )
                )
            }
        }
    }

    fun dismissBlurPreview() {
        previewJob?.cancel()
        _uiState.update { it.copy(blurPreview = BlurPreviewState.Idle) }
    }

    /**
     * ML Kit içerik sınıflandırmasını açar/kapatır.
     *
     * Kapalıyken tarama belirgin biçimde hızlanır; belge sekmesi boş kalır ve
     * kişi/evcil hayvan koruması devre dışı olur.
     */
    fun toggleAiClassification() {
        val newValue = !_uiState.value.aiClassificationEnabled
        viewModelScope.launch {
            scanSettings.setAiClassificationEnabled(newValue)
        }
    }

    /**
     * Otomatik taramayı açar/kapatır ve WorkManager kaydını da günceller.
     * Daha önce yalnızca tercih yazılıyor, hiçbir iş planlanmıyordu.
     */
    fun toggleAutoScan() {
        val newValue = !_uiState.value.autoScanEnabled
        viewModelScope.launch {
            scanSettings.setAutoScanEnabled(newValue)
            AutoScanScheduler.apply(context, newValue)
        }
    }
}
