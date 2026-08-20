package com.storagemanager.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.data.preferences.ScanSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val scanSettings: ScanSettings
) : ViewModel() {

    /** Onboarding tamamlandı olarak işaretlenir; sonraki açılışlarda doğrudan Dashboard gelir. */
    fun markCompleted(onDone: () -> Unit) {
        viewModelScope.launch {
            scanSettings.setOnboardingDone(true)
            onDone()
        }
    }
}
