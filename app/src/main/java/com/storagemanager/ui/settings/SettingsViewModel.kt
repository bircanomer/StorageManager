package com.storagemanager.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val minPhotoBlurThreshold: Float = 100f,
    val oldPhotoDays: Int = 180,
    val unusedAppDays: Int = 30,
    val minLargeFileSizeMB: Int = 50,
    val autoScanEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateBlurThreshold(value: Float) {
        _uiState.update { it.copy(minPhotoBlurThreshold = value) }
    }

    fun updateOldPhotoDays(days: Int) {
        _uiState.update { it.copy(oldPhotoDays = days) }
    }

    fun updateUnusedAppDays(days: Int) {
        _uiState.update { it.copy(unusedAppDays = days) }
    }

    fun updateMinFileSize(mb: Int) {
        _uiState.update { it.copy(minLargeFileSizeMB = mb) }
    }

    fun toggleAutoScan() {
        _uiState.update { it.copy(autoScanEnabled = !it.autoScanEnabled) }
    }
}
