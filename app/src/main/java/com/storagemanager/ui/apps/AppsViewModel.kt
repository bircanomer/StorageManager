package com.storagemanager.ui.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.domain.model.UnusedApp
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppsUiState(
    val unusedApps: List<UnusedApp> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val dayFilter: Int = 30
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val repository: StorageRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val apps = repository.scanUnusedApps(_uiState.value.dayFilter)
                _uiState.update {
                    it.copy(
                        unusedApps = apps,
                        isLoading = false,
                        selectedPackages = emptySet()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleSelection(packageName: String) {
        _uiState.update { state ->
            val newSelection = if (packageName in state.selectedPackages) {
                state.selectedPackages - packageName
            } else {
                state.selectedPackages + packageName
            }
            state.copy(selectedPackages = newSelection)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedPackages = state.unusedApps.map { it.packageName }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPackages = emptySet()) }
    }

    fun setDayFilter(days: Int) {
        _uiState.update { it.copy(dayFilter = days) }
        loadApps()
    }

    /**
     * Belirtilen paketi kaldırmak için uninstall intent oluşturur.
     * Döndürülen Intent'i Activity'den başlatmanız gerekir.
     */
    fun createUninstallIntent(packageName: String): Intent {
        return Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun uninstallApp(packageName: String) {
        val intent = createUninstallIntent(packageName)
        context.startActivity(intent)
    }
}
