package com.storagemanager

import android.app.Application
import android.util.Log
import com.storagemanager.data.preferences.ScanSettings
import com.storagemanager.worker.AutoScanScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StorageManagerApp : Application() {

    @Inject
    lateinit var scanSettings: ScanSettings

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Periyodik kontrolü kullanıcının tercihiyle eşitle (yeniden kurulum, sistem
        // tarafından iptal edilme vb. durumlarda kaydın kaybolmaması için).
        applicationScope.launch {
            try {
                AutoScanScheduler.apply(this@StorageManagerApp, scanSettings.current().autoScanEnabled)
            } catch (e: Exception) {
                Log.w("StorageManagerApp", "Otomatik tarama planlanamadı", e)
            }
        }
    }
}
