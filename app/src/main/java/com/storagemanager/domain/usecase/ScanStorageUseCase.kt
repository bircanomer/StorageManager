package com.storagemanager.domain.usecase

import android.content.Context
import com.storagemanager.R
import com.storagemanager.domain.model.ScanState
import com.storagemanager.domain.repository.StorageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Depolama tarama kullanım senaryosu (use case).
 *
 * Repository katmanı ile UI arasındaki köprüyü kurar.
 * Tarama ilerlemesini Flow olarak yayınlar ve hata yönetimini sağlar.
 */
@Singleton
class ScanStorageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StorageRepository
) {

    /**
     * Tam depolama taraması başlatır.
     *
     * Tarama durumlarını [ScanState] olarak yayınlar:
     * - [ScanState.Scanning]: Tarama devam ediyor (ilerleme ve görev bilgisi ile)
     * - [ScanState.Completed]: Tarama başarıyla tamamlandı (sonuç ile)
     * - [ScanState.Error]: Tarama sırasında hata oluştu (hata mesajı ile)
     *
     * @param onProgress İlerleme callback'i (yüzde, durum mesajı)
     * @return ScanState akışı (Flow)
     */
    operator fun invoke(onProgress: (Float, String) -> Unit = { _, _ -> }): Flow<ScanState> = flow {
        try {
            emit(
                ScanState.Scanning(
                    progress = 0f,
                    currentTask = context.getString(R.string.progress_starting)
                )
            )

            val result = repository.fullScan(
                onProgress = { progress, task -> onProgress(progress, task) }
            )

            emit(ScanState.Completed(result))
        } catch (e: Exception) {
            emit(ScanState.Error(e.message ?: context.getString(R.string.unknown_error)))
        }
    }
}
