package com.storagemanager.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.storagemanager.BuildConfig
import com.storagemanager.data.preferences.ProEntitlement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * AdMob yaşam döngüsü yöneticisi.
 *
 * Kurallar:
 *  - SDK, kullanıcı ilk kez reklam görebileceği bir noktaya gelene kadar ilklendirilmez
 *    (uygulama açılışını yavaşlatmamak için).
 *  - Pro kullanıcıya hiçbir reklam istenmez; istek bile yapılmaz.
 *  - AEA kullanıcıları için UMP onay formu reklam isteğinden önce gösterilir.
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlement: ProEntitlement
) {

    private val initialized = AtomicBoolean(false)
    private var rewardedAd: RewardedAd? = null
    private var rewardedLoading = false

    /** Reklam SDK'sını en fazla bir kez ilklendirir. */
    fun ensureInitialized() {
        if (!initialized.compareAndSet(false, true)) return
        try {
            MobileAds.initialize(context) {
                Log.d(TAG, "AdMob hazır")
            }
        } catch (e: Exception) {
            Log.w(TAG, "AdMob ilklendirilemedi", e)
            initialized.set(false)
        }
    }

    /**
     * AEA/GDPR onay akışını çalıştırır. Form gerekmiyorsa hemen döner.
     * Sonuç ne olursa olsun uygulamanın akışı durmaz.
     */
    fun requestConsentIfNeeded(activity: Activity, onComplete: () -> Unit = {}) {
        try {
            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .apply {
                    if (BuildConfig.DEBUG) {
                        setConsentDebugSettings(
                            ConsentDebugSettings.Builder(activity)
                                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                                .build()
                        )
                    }
                }
                .build()

            val consentInformation: ConsentInformation =
                UserMessagingPlatform.getConsentInformation(activity)

            consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                        if (error != null) {
                            Log.w(TAG, "Onay formu gösterilemedi: ${error.message}")
                        }
                        ensureInitialized()
                        onComplete()
                    }
                },
                { error ->
                    Log.w(TAG, "Onay bilgisi alınamadı: ${error.message}")
                    ensureInitialized()
                    onComplete()
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Onay akışı başarısız", e)
            ensureInitialized()
            onComplete()
        }
    }

    /** Pro kullanıcılar için reklam yüklenmez. */
    private suspend fun adsAllowed(): Boolean = !entitlement.currentlyPro()

    // ── Native reklam ────────────────────────────────────────────────────────

    /**
     * Tek bir native reklam yükler. Pro kullanıcıda veya hata durumunda `null` döner,
     * böylece çağıran taraf reklam alanını hiç çizmez.
     */
    suspend fun loadNativeAd(): NativeAd? {
        if (!adsAllowed()) return null
        ensureInitialized()
        return suspendCancellableCoroutine { continuation ->
            try {
                var resumed = false
                val loader = AdLoader.Builder(context, BuildConfig.ADMOB_NATIVE_UNIT_ID)
                    .forNativeAd { ad ->
                        if (continuation.isActive && !resumed) {
                            resumed = true
                            continuation.resume(ad)
                        } else {
                            ad.destroy()
                        }
                    }
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.d(TAG, "Native reklam yüklenemedi: ${error.message}")
                            if (continuation.isActive && !resumed) {
                                resumed = true
                                continuation.resume(null)
                            }
                        }
                    })
                    .withNativeAdOptions(
                        NativeAdOptions.Builder()
                            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                            .build()
                    )
                    .build()

                loader.loadAd(AdRequest.Builder().build())
            } catch (e: Exception) {
                Log.w(TAG, "Native reklam isteği başarısız", e)
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    // ── Ödüllü reklam ────────────────────────────────────────────────────────

    /** Ödüllü reklamı önceden yükler; gösterim anında bekleme olmaz. */
    suspend fun preloadRewarded() {
        if (!adsAllowed() || rewardedAd != null || rewardedLoading) return
        ensureInitialized()
        rewardedLoading = true
        try {
            RewardedAd.load(
                context,
                BuildConfig.ADMOB_REWARDED_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        rewardedLoading = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.d(TAG, "Ödüllü reklam yüklenemedi: ${error.message}")
                        rewardedAd = null
                        rewardedLoading = false
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Ödüllü reklam isteği başarısız", e)
            rewardedLoading = false
        }
    }

    val isRewardedReady: Boolean get() = rewardedAd != null

    /**
     * Ödüllü reklamı gösterir.
     *
     * @param onResult ödül kazanıldıysa `true` — çağıran taraf [ProEntitlement.grantReward] çağırır.
     */
    fun showRewarded(activity: Activity, onResult: (Boolean) -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            onResult(false)
            return
        }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                onResult(earned)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Ödüllü reklam gösterilemedi: ${error.message}")
                rewardedAd = null
                onResult(false)
            }
        }
        ad.show(activity) { earned = true }
    }

    private companion object {
        const val TAG = "AdsManager"
    }
}
