package com.storagemanager.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uygulama olay izleme arayüzü.
 *
 * Ekranlar doğrudan Firebase'e bağlanmaz; böylece sağlayıcı değiştirilebilir ve
 * `google-services.json` bulunmayan derlemelerde uygulama yine de çalışır.
 */
interface Analytics {
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap())
    fun setProStatus(isPro: Boolean)

    companion object {
        // Tarama hunisi
        const val EVENT_SCAN_STARTED = "scan_started"
        const val EVENT_SCAN_COMPLETED = "scan_completed"
        const val EVENT_CLEAN_CONFIRMED = "clean_confirmed"
        const val EVENT_CLEAN_COMPLETED = "clean_completed"

        // Gelir hunisi
        const val EVENT_PAYWALL_SHOWN = "paywall_shown"
        const val EVENT_PAYWALL_DISMISSED = "paywall_dismissed"
        const val EVENT_PURCHASE_STARTED = "purchase_started"
        const val EVENT_PURCHASE_SUCCESS = "purchase_success"
        const val EVENT_REWARDED_AD_STARTED = "rewarded_ad_started"
        const val EVENT_REWARDED_AD_EARNED = "rewarded_ad_earned"

        // Parametreler
        const val PARAM_SOURCE = "source"
        const val PARAM_PRODUCT_ID = "product_id"
        const val PARAM_FREED_BYTES = "freed_bytes"
        const val PARAM_ITEM_COUNT = "item_count"
        const val PARAM_CATEGORY = "category"
    }
}

/**
 * Firebase Analytics uygulaması.
 *
 * `google-services.json` projeye eklenmemişse Firebase ilklendirilemez; bu durumda
 * sınıf sessizce devre dışı kalır ve olaylar yalnızca hata ayıklama günlüğüne yazılır.
 */
@Singleton
class FirebaseAnalyticsTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : Analytics {

    private val firebase: FirebaseAnalytics? by lazy {
        try {
            FirebaseAnalytics.getInstance(context)
        } catch (e: Throwable) {
            Log.i(TAG, "Firebase yapılandırılmamış — analitik devre dışı", e)
            null
        }
    }

    override fun logEvent(name: String, params: Map<String, Any?>) {
        val analytics = firebase
        if (analytics == null) {
            Log.d(TAG, "event=$name params=$params")
            return
        }
        try {
            analytics.logEvent(name, params.toBundle())
        } catch (e: Exception) {
            Log.w(TAG, "Olay gönderilemedi: $name", e)
        }
    }

    override fun setProStatus(isPro: Boolean) {
        try {
            firebase?.setUserProperty(USER_PROPERTY_PRO, isPro.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Kullanıcı özelliği yazılamadı", e)
        }
    }

    private fun Map<String, Any?>.toBundle(): Bundle = Bundle().apply {
        forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> putString(key, value)
                is Int -> putLong(key, value.toLong())
                is Long -> putLong(key, value)
                is Float -> putDouble(key, value.toDouble())
                is Double -> putDouble(key, value)
                is Boolean -> putString(key, value.toString())
                else -> putString(key, value.toString())
            }
        }
    }

    private companion object {
        const val TAG = "Analytics"
        const val USER_PROPERTY_PRO = "is_pro"
    }
}
