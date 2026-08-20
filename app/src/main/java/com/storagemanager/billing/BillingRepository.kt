package com.storagemanager.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.storagemanager.data.preferences.ProEntitlement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Billing 7 istemcisi.
 *
 * Sorumlulukları:
 *  - Play ile bağlantıyı kurmak ve kopmalarda yeniden bağlanmak,
 *  - Ürün bilgilerini (fiyat dâhil) çekmek,
 *  - Satın alma akışını başlatmak,
 *  - Satın almaları onaylamak (`acknowledge` — 3 gün içinde yapılmazsa Play parayı iade eder),
 *  - Doğrulanan hakkı [ProEntitlement] üzerine yazmak.
 *
 * Sunucu tarafı doğrulama yoktur; hak yerel olarak Play'in döndürdüğü satın alma
 * listesinden türetilir. Gelir kritikleştiğinde `purchaseToken`'ın kendi sunucunuzda
 * Play Developer API ile doğrulanması önerilir.
 */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlement: ProEntitlement
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Son satın alma denemesinin sonucu — paywall ekranı bunu gösterir. */
    private val _purchaseEvents = MutableStateFlow<PurchaseEvent?>(null)
    val purchaseEvents: StateFlow<PurchaseEvent?> = _purchaseEvents.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    /** Bağlantıyı kurar ve mevcut satın almaları geri yükler. Tekrar çağrılması güvenlidir. */
    fun start() {
        if (client.isReady) {
            scope.launch { refresh() }
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                _connected.value = ok
                if (ok) {
                    scope.launch { refresh() }
                } else {
                    Log.w(TAG, "Billing bağlantısı kurulamadı: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _connected.value = false
                Log.w(TAG, "Billing servisi koptu")
            }
        })
    }

    /** Ürünleri ve mevcut hakları Play'den yeniden okur. */
    suspend fun refresh() {
        queryProducts()
        restorePurchases()
    }

    private suspend fun queryProducts() {
        val subs = queryProductDetails(SUBSCRIPTION_IDS, BillingClient.ProductType.SUBS)
        val inApp = queryProductDetails(ONE_TIME_IDS, BillingClient.ProductType.INAPP)
        _products.value = subs + inApp
    }

    private suspend fun queryProductDetails(
        ids: List<String>,
        type: String
    ): List<ProductDetails> = try {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(type)
                        .build()
                }
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            result.productDetailsList.orEmpty()
        } else {
            Log.w(TAG, "Ürün bilgisi alınamadı ($type): ${result.billingResult.debugMessage}")
            emptyList()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Ürün sorgusu başarısız ($type)", e)
        emptyList()
    }

    /**
     * Play'deki gerçek durumu okuyup yerel hakkı günceller.
     * Aboneliği iptal edilen kullanıcının hakkı burada geri alınır.
     */
    suspend fun restorePurchases() {
        try {
            val subs = client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ).purchasesList

            val inApp = client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ).purchasesList

            val all = subs + inApp
            all.forEach { acknowledgeIfNeeded(it) }

            val active = all.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            entitlement.setPurchasedPro(active)
        } catch (e: Exception) {
            Log.w(TAG, "Satın almalar geri yüklenemedi", e)
        }
    }

    /** Satın alma akışını başlatır. Abonelikler için ilk teklif jetonu kullanılır. */
    fun launchPurchase(activity: Activity, productDetails: ProductDetails) {
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            val offerToken = productDetails.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken
            if (offerToken == null) {
                Log.w(TAG, "Abonelik teklif jetonu yok: ${productDetails.productId}")
                _purchaseEvents.value = PurchaseEvent.Failed
                return
            }
            paramsBuilder.setOfferToken(offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()

        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "Satın alma akışı açılamadı: ${result.debugMessage}")
            _purchaseEvents.value = PurchaseEvent.Failed
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                scope.launch {
                    purchases?.forEach { acknowledgeIfNeeded(it) }
                    val purchased = purchases?.any {
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                    } == true
                    if (purchased) {
                        entitlement.setPurchasedPro(true)
                        _purchaseEvents.value = PurchaseEvent.Success
                    } else {
                        _purchaseEvents.value = PurchaseEvent.Pending
                    }
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED ->
                _purchaseEvents.value = PurchaseEvent.Cancelled

            else -> {
                Log.w(TAG, "Satın alma başarısız: ${result.debugMessage}")
                _purchaseEvents.value = PurchaseEvent.Failed
            }
        }
    }

    /**
     * Onaylanmamış satın almaları onaylar.
     * Play, 3 gün içinde onaylanmayan satın almaları otomatik iade eder.
     */
    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.isAcknowledged) return
        try {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = client.acknowledgePurchase(params)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Satın alma onaylanamadı: ${result.debugMessage}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Satın alma onayı başarısız", e)
        }
    }

    fun consumeEvent() {
        _purchaseEvents.value = null
    }

    sealed interface PurchaseEvent {
        data object Success : PurchaseEvent
        data object Pending : PurchaseEvent
        data object Cancelled : PurchaseEvent
        data object Failed : PurchaseEvent
    }

    companion object {
        private const val TAG = "BillingRepository"

        const val PRODUCT_PRO_YEARLY = "pro_yearly"
        const val PRODUCT_PRO_MONTHLY = "pro_monthly"
        const val PRODUCT_PRO_LIFETIME = "pro_lifetime"

        val SUBSCRIPTION_IDS = listOf(PRODUCT_PRO_MONTHLY, PRODUCT_PRO_YEARLY)
        val ONE_TIME_IDS = listOf(PRODUCT_PRO_LIFETIME)
    }
}
