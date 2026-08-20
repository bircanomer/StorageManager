package com.storagemanager.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.data.preferences.ScanSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * İlk açılışta onboarding, sonraki açılışlarda Dashboard gösterilmesini sağlar.
 *
 * Onboarding ekranı yazılmıştı ama hiçbir zaman başlangıç noktası olmadığı için
 * kullanıcıya hiç gösterilmiyordu.
 */
@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    scanSettings: ScanSettings
) : ViewModel() {

    /** null = tercih henüz okunmadı */
    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            val done = scanSettings.current().onboardingDone
            _startRoute.value = if (done) Screen.Dashboard.route else Screen.Onboarding.route
        }
    }
}
