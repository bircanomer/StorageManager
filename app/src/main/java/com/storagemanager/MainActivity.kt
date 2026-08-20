package com.storagemanager

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.storagemanager.ads.AdsManager
import com.storagemanager.billing.BillingRepository
import com.storagemanager.ui.navigation.NavGraph
import com.storagemanager.ui.theme.StorageManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Tek Activity.
 *
 * İzinler burada istenmiyor: önceki sürüm `onCreate` içinde hem çalışma zamanı izin
 * diyaloğunu açıyor hem de kullanıcıyı arka arkaya iki Ayarlar ekranına fırlatıyordu.
 * Artık çalışma zamanı izinleri onboarding ekranında, özel erişim izinleri ise
 * Ayarlar ekranında kullanıcı isteğiyle isteniyor.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var billingRepository: BillingRepository
    @Inject lateinit var adsManager: AdsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val screenshotMode = BuildConfig.DEBUG && intent.getBooleanExtra("screenshotMode", false)

        // Play ile bağlantıyı kurup mevcut satın almayı doğrular; iptal edilen
        // abonelikte Pro hakkı burada geri alınır.
        if (!screenshotMode) billingRepository.start()

        // AEA kullanıcıları için reklam onay formu — gerekmiyorsa hiçbir şey göstermez.
        if (!screenshotMode) adsManager.requestConsentIfNeeded(this)

        setContent {
            StorageManagerTheme {
                NavGraph()
            }
        }
    }
}
