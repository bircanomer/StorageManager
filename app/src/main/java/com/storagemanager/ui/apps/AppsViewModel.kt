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
import com.storagemanager.ui.components.localeCollator

enum class AppSortBy {
    UNUSED_FIRST,
    SIZE_DESC,
    SIZE_ASC,
    NAME_ASC
}

data class AppsUiState(
    val unusedApps: List<UnusedApp> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val dayFilter: Int = 30,
    val sortBy: AppSortBy = AppSortBy.UNUSED_FIRST
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val repository: StorageRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()


    init {
        loadCachedApps()
    }

    private fun loadCachedApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val cachedResult = repository.getCachedScanResults()
                if (cachedResult != null && cachedResult.unusedApps.isNotEmpty()) {
                    _uiState.update { state ->
                        state.copy(
                            unusedApps = sortApps(cachedResult.unusedApps, state.sortBy),
                            isLoading = false
                        )
                    }
                } else {
                    loadApps()
                }
            } catch (e: Exception) {
                loadApps()
            }
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val apps = repository.scanUnusedApps(_uiState.value.dayFilter)
                _uiState.update {
                    it.copy(
                        unusedApps = sortApps(apps, it.sortBy),
                        isLoading = false,
                        selectedPackages = emptySet()
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("AppsVM", "Failed to load apps", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setSortBy(sortBy: AppSortBy) {
        _uiState.update { state ->
            state.copy(
                sortBy = sortBy,
                unusedApps = sortApps(state.unusedApps, sortBy)
            )
        }
    }

    private fun sortApps(apps: List<UnusedApp>, sortBy: AppSortBy): List<UnusedApp> {
        return when (sortBy) {
            AppSortBy.UNUSED_FIRST -> apps.sortedWith(
                compareBy<UnusedApp> { 
                    // lastUsed = 0 olanları (hiç kullanılmayanlar) en üste almak için sıralama önceliği
                    if (it.lastUsed <= 0L || it.lastUsed < 86400000L) 0 else 1 
                }.thenBy { it.lastUsed } // Diğerlerini de son kullanım tarihine göre eskiden yeniye (hiç kullanılmayanlara yakın) sırala
            )
            AppSortBy.SIZE_DESC -> apps.sortedByDescending { it.appSize + it.cacheSize }
            AppSortBy.SIZE_ASC -> apps.sortedBy { it.appSize + it.cacheSize }
            AppSortBy.NAME_ASC -> apps.sortedWith(compareBy(localeCollator()) { it.appName })
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



    fun setDayFilter(days: Int) {
        _uiState.update { it.copy(dayFilter = days) }
        loadApps()
    }

    private val uninstallQueue = mutableListOf<String>()
    private var lastAttemptedUninstallPackage: String? = null

    fun startUninstallQueue(packages: List<String>) {
        uninstallQueue.clear()
        uninstallQueue.addAll(packages)
        processNextUninstall()
    }

    fun processNextUninstall() {
        lastAttemptedUninstallPackage?.let { pkg ->
            checkAndRemoveIfUninstalled(pkg)
            lastAttemptedUninstallPackage = null
        }

        if (uninstallQueue.isEmpty()) {
            loadApps()
            return
        }

        val nextPackage = uninstallQueue.removeAt(0)
        lastAttemptedUninstallPackage = nextPackage
        uninstallApp(nextPackage)
    }

    fun checkAndRemoveIfUninstalled(packageName: String) {
        val isInstalled = try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }

        if (!isInstalled) {
            _uiState.update { state ->
                state.copy(
                    unusedApps = state.unusedApps.filterNot { it.packageName == packageName },
                    selectedPackages = state.selectedPackages - packageName
                )
            }
        }
    }

    fun refreshAfterUninstall() {
        lastAttemptedUninstallPackage?.let { pkg ->
            checkAndRemoveIfUninstalled(pkg)
            lastAttemptedUninstallPackage = null
        }
    }

    @Suppress("DEPRECATION")
    fun uninstallApp(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
