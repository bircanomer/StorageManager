package com.storagemanager.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.storagemanager.ads.AdsManager
import com.storagemanager.analytics.Analytics
import com.storagemanager.billing.BillingRepository
import com.storagemanager.data.preferences.ProEntitlement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Paywall ekranının durumu.
 *
 * @param plans Play'den gelen gerçek fiyatlı planlar. Play'e ulaşılamazsa boş kalır ve
 *              ekran fiyat yerine "şu anda kullanılamıyor" gösterir — asla sabit fiyat yazılmaz.
 */
data class PaywallState(
    val plans: List<Plan> = emptyList(),
    val isPro: Boolean = false,
    val rewardActiveUntil: Long = 0L,
    val rewardedAdReady: Boolean = false,
    val message: PaywallMessage? = null,
    val loading: Boolean = true
)

data class Plan(
    val productId: String,
    val formattedPrice: String,
    val details: ProductDetails,
    val isSubscription: Boolean
)

enum class PaywallMessage { SUCCESS, PENDING, CANCELLED, FAILED, REWARD_GRANTED }

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingRepository,
    private val entitlement: ProEntitlement,
    private val adsManager: AdsManager,
    private val analytics: Analytics
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallState())
    val uiState: StateFlow<PaywallState> = _uiState.asStateFlow()

    init {
        billing.start()
        analytics.logEvent(Analytics.EVENT_PAYWALL_SHOWN)

        viewModelScope.launch {
            combine(
                billing.products,
                entitlement.isPurchasedPro,
                entitlement.rewardExpiresAt,
                billing.purchaseEvents
            ) { products, isPro, rewardUntil, event ->
                PaywallState(
                    plans = products.toPlans(),
                    isPro = isPro,
                    rewardActiveUntil = rewardUntil,
                    rewardedAdReady = adsManager.isRewardedReady,
                    message = event?.toMessage() ?: _uiState.value.message,
                    loading = products.isEmpty() && !isPro
                )
            }.collect { _uiState.value = it }
        }

        viewModelScope.launch { adsManager.preloadRewarded() }
    }

    private fun List<ProductDetails>.toPlans(): List<Plan> = mapNotNull { details ->
        val price = when (details.productType) {
            BillingClient.ProductType.SUBS ->
                details.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases
                    ?.pricingPhaseList
                    ?.firstOrNull()
                    ?.formattedPrice

            else -> details.oneTimePurchaseOfferDetails?.formattedPrice
        } ?: return@mapNotNull null

        Plan(
            productId = details.productId,
            formattedPrice = price,
            details = details,
            isSubscription = details.productType == BillingClient.ProductType.SUBS
        )
    }.sortedBy { PLAN_ORDER.indexOf(it.productId) }

    fun purchase(activity: Activity, plan: Plan) {
        analytics.logEvent(
            Analytics.EVENT_PURCHASE_STARTED,
            mapOf(Analytics.PARAM_PRODUCT_ID to plan.productId)
        )
        billing.launchPurchase(activity, plan.details)
    }

    fun restore() {
        viewModelScope.launch { billing.refresh() }
    }

    /** Ödüllü reklamı gösterir; ödül kazanılırsa geçici Pro erişimi tanımlar. */
    fun watchRewardedAd(activity: Activity) {
        analytics.logEvent(Analytics.EVENT_REWARDED_AD_STARTED)
        adsManager.showRewarded(activity) { earned ->
            if (earned) {
                viewModelScope.launch {
                    entitlement.grantReward(ProEntitlement.REWARD_DURATION_MS)
                    analytics.logEvent(Analytics.EVENT_REWARDED_AD_EARNED)
                    _uiState.value = _uiState.value.copy(message = PaywallMessage.REWARD_GRANTED)
                    adsManager.preloadRewarded()
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
        billing.consumeEvent()
    }

    private fun BillingRepository.PurchaseEvent.toMessage(): PaywallMessage = when (this) {
        BillingRepository.PurchaseEvent.Success -> {
            analytics.logEvent(Analytics.EVENT_PURCHASE_SUCCESS)
            analytics.setProStatus(true)
            PaywallMessage.SUCCESS
        }
        BillingRepository.PurchaseEvent.Pending -> PaywallMessage.PENDING
        BillingRepository.PurchaseEvent.Cancelled -> PaywallMessage.CANCELLED
        BillingRepository.PurchaseEvent.Failed -> PaywallMessage.FAILED
    }

    private companion object {
        val PLAN_ORDER = listOf(
            BillingRepository.PRODUCT_PRO_MONTHLY,
            BillingRepository.PRODUCT_PRO_YEARLY,
            BillingRepository.PRODUCT_PRO_LIFETIME
        )
    }
}
