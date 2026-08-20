package com.storagemanager.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.storagemanager.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pro erişim hakkının tek kaynağı.
 *
 * İki farklı hak birleştirilir:
 *  - **Kalıcı hak**: Play üzerinden yapılan abonelik veya ömür boyu satın alma.
 *    Gerçek doğrulama her uygulama açılışında [com.storagemanager.billing.BillingRepository]
 *    tarafından Play'e sorularak yenilenir; buradaki değer yalnızca çevrimdışı önbellektir.
 *  - **Geçici hak**: Ödüllü reklam izlenerek kazanılan süreli erişim.
 */
@Singleton
class ProEntitlement @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    object Keys {
        val IS_PRO = booleanPreferencesKey("is_pro")
        val PRO_SOURCE = longPreferencesKey("pro_source_ts")
        val REWARD_EXPIRES_AT = longPreferencesKey("reward_expires_at")

        /**
         * Geliştirme derlemesinde Pro'yu elle açan bayrak.
         *
         * Ayrı bir anahtar olmasının sebebi [IS_PRO]'nun Play'e ait olması:
         * [com.storagemanager.billing.BillingRepository.restorePurchases] her açılışta
         * satın alma listesine bakıp o değeri yeniden yazar, dolayısıyla elle
         * ayarlanan bir değer bir sonraki açılışta silinir.
         */
        val DEBUG_PRO = booleanPreferencesKey("debug_pro")
    }

    /** Play'den doğrulanmış kalıcı satın alma. */
    val isPurchasedPro: Flow<Boolean> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[Keys.IS_PRO] ?: false }

    /**
     * Geliştirici tarafından elle açılan Pro. Yalnızca debug derlemesinde etkilidir;
     * release'de bu akış her zaman `false` üretir.
     */
    val debugPro: Flow<Boolean> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { BuildConfig.DEBUG && (it[Keys.DEBUG_PRO] ?: false) }

    /** Ödüllü reklamla kazanılan geçici erişimin bitiş zamanı (epoch ms). */
    val rewardExpiresAt: Flow<Long> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[Keys.REWARD_EXPIRES_AT] ?: 0L }

    /**
     * Pro özelliklerinin şu anda açık olup olmadığı.
     *
     * Not: Bu akış zaman geçtikçe kendiliğinden `false`'a dönmez — ödül süresi
     * dolduğunda yeniden değerlendirilmesi için ekran her açıldığında toplanır.
     */
    val hasProAccess: Flow<Boolean> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            val purchased = prefs[Keys.IS_PRO] ?: false
            val rewardUntil = prefs[Keys.REWARD_EXPIRES_AT] ?: 0L
            // BuildConfig.DEBUG derleme zamanı sabiti olduğu için release'de bu terim
            // tamamen elenir; bayrak yanlışlıkla açık kalsa bile etkisi olmaz.
            val debugOverride = BuildConfig.DEBUG && (prefs[Keys.DEBUG_PRO] ?: false)
            purchased || rewardUntil > System.currentTimeMillis() || debugOverride
        }

    suspend fun currentlyPro(): Boolean = try {
        hasProAccess.first()
    } catch (e: Exception) {
        android.util.Log.w(TAG, "Pro durumu okunamadı", e)
        false
    }

    /** Play satın alma doğrulaması sonucunu yazar. */
    suspend fun setPurchasedPro(value: Boolean) = edit {
        it[Keys.IS_PRO] = value
        it[Keys.PRO_SOURCE] = System.currentTimeMillis()
    }

    /** Geliştirme derlemesinde Pro'yu açar/kapatır. */
    suspend fun setDebugPro(value: Boolean) = edit { it[Keys.DEBUG_PRO] = value }

    /** Ödüllü reklam sonrası geçici erişim tanımlar. */
    suspend fun grantReward(durationMs: Long) = edit {
        val current = it[Keys.REWARD_EXPIRES_AT] ?: 0L
        val base = maxOf(current, System.currentTimeMillis())
        it[Keys.REWARD_EXPIRES_AT] = base + durationMs
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Pro durumu yazılamadı", e)
        }
    }

    companion object {
        private const val TAG = "ProEntitlement"

        /** Ödüllü reklam karşılığında verilen erişim süresi. */
        const val REWARD_DURATION_MS = 24L * 60L * 60L * 1000L
    }
}
