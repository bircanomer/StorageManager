package com.storagemanager.ui.pro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagemanager.ads.AdsManager
import com.storagemanager.data.preferences.ProEntitlement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pro erişimini okuyan ortak ViewModel.
 *
 * Ekranlar kendi ViewModel'lerine Pro mantığı koymak yerine bunu `hiltViewModel()` ile
 * alır; böylece kilit kuralı tek yerde tanımlı kalır.
 */
@HiltViewModel
class ProViewModel @Inject constructor(
    entitlement: ProEntitlement,
    private val adsManager: AdsManager
) : ViewModel() {

    val isPro: StateFlow<Boolean> = entitlement.hasProAccess
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Ücretsiz kullanıcı için ödüllü reklamı önden hazırla.
        viewModelScope.launch { adsManager.preloadRewarded() }
    }

    /** Reklam bileşenlerinin ihtiyaç duyduğu yönetici. */
    fun ads(): AdsManager = adsManager

    companion object {
        /** Ücretsiz sürümde tek seferde silinebilecek öge sayısı. */
        const val FREE_BULK_LIMIT = 10
    }
}
