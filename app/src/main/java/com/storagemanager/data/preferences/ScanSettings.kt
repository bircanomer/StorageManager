package com.storagemanager.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tarama ayarlarının tek kaynağı.
 *
 * Hem [com.storagemanager.ui.settings.SettingsViewModel] hem de analizörler bu sınıf
 * üzerinden okur/yazar; böylece ayarlar gerçekten taramaya yansır.
 */
@Singleton
class ScanSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    object Keys {
        /**
         * Netlik eşiği. Anahtar bilinçli olarak yeni: eski `blur_threshold` değerleri
         * küçültülmüş thumbnail üzerinde ölçülen bambaşka bir ölçeğe aitti ve yeni
         * ölçütle karşılaştırılamaz. Eski anahtar okunsaydı kullanıcıların ayarı
         * sessizce anlamsız bir yere düşerdi.
         */
        val BLUR_THRESHOLD = floatPreferencesKey("sharpness_threshold_v2")
        val OLD_PHOTO_DAYS = intPreferencesKey("old_photo_days")
        val UNUSED_APP_DAYS = intPreferencesKey("unused_app_days")
        val MIN_FILE_SIZE_MB = intPreferencesKey("min_file_size_mb")
        val AUTO_SCAN = booleanPreferencesKey("auto_scan")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val AI_CLASSIFICATION = booleanPreferencesKey("ai_classification")
    }

    object Defaults {
        /** [com.storagemanager.ml.SharpnessProbe.DEFAULT_THRESHOLD] ile aynı kalmalı. */
        const val BLUR_THRESHOLD = 60f
        const val OLD_PHOTO_DAYS = 180
        const val UNUSED_APP_DAYS = 30
        const val MIN_FILE_SIZE_MB = 50
        const val AUTO_SCAN = false
        const val AI_CLASSIFICATION = true
    }

    data class Snapshot(
        val blurThreshold: Float = Defaults.BLUR_THRESHOLD,
        val oldPhotoDays: Int = Defaults.OLD_PHOTO_DAYS,
        val unusedAppDays: Int = Defaults.UNUSED_APP_DAYS,
        val minLargeFileSizeMb: Int = Defaults.MIN_FILE_SIZE_MB,
        val autoScanEnabled: Boolean = Defaults.AUTO_SCAN,
        val onboardingDone: Boolean = false,
        /** ML Kit içerik sınıflandırması — belge tespiti ve kişi/evcil hayvan koruması. */
        val aiClassificationEnabled: Boolean = Defaults.AI_CLASSIFICATION
    ) {
        val minLargeFileSizeBytes: Long get() = minLargeFileSizeMb.toLong() * 1024L * 1024L
    }

    /** Ayarların canlı akışı. Okuma hatasında varsayılanlara düşer. */
    val snapshots: Flow<Snapshot> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            Snapshot(
                blurThreshold = prefs[Keys.BLUR_THRESHOLD] ?: Defaults.BLUR_THRESHOLD,
                oldPhotoDays = prefs[Keys.OLD_PHOTO_DAYS] ?: Defaults.OLD_PHOTO_DAYS,
                unusedAppDays = prefs[Keys.UNUSED_APP_DAYS] ?: Defaults.UNUSED_APP_DAYS,
                minLargeFileSizeMb = prefs[Keys.MIN_FILE_SIZE_MB] ?: Defaults.MIN_FILE_SIZE_MB,
                autoScanEnabled = prefs[Keys.AUTO_SCAN] ?: Defaults.AUTO_SCAN,
                onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
                aiClassificationEnabled = prefs[Keys.AI_CLASSIFICATION] ?: Defaults.AI_CLASSIFICATION
            )
        }

    /** Anlık ayar okuması — analizörler tarama başında bunu çağırır. */
    suspend fun current(): Snapshot = try {
        snapshots.first()
    } catch (e: Exception) {
        android.util.Log.w(TAG, "Ayarlar okunamadı, varsayılanlar kullanılıyor", e)
        Snapshot()
    }

    suspend fun setBlurThreshold(value: Float) = edit { it[Keys.BLUR_THRESHOLD] = value }
    suspend fun setOldPhotoDays(value: Int) = edit { it[Keys.OLD_PHOTO_DAYS] = value }
    suspend fun setUnusedAppDays(value: Int) = edit { it[Keys.UNUSED_APP_DAYS] = value }
    suspend fun setMinFileSizeMb(value: Int) = edit { it[Keys.MIN_FILE_SIZE_MB] = value }
    suspend fun setAutoScanEnabled(value: Boolean) = edit { it[Keys.AUTO_SCAN] = value }
    suspend fun setOnboardingDone(value: Boolean) = edit { it[Keys.ONBOARDING_DONE] = value }
    suspend fun setAiClassificationEnabled(value: Boolean) = edit { it[Keys.AI_CLASSIFICATION] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Ayar yazılamadı", e)
        }
    }

    private companion object {
        const val TAG = "ScanSettings"
    }
}
