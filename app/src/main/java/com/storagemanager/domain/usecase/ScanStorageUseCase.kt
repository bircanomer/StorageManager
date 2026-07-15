package com.storagemanager.domain.usecase

import com.storagemanager.domain.model.ScanState
import com.storagemanager.domain.repository.StorageRepository
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
            emit(ScanState.Scanning(progress = 0f, currentTask = "Tarama başlatılıyor..."))

            val result = repository.fullScan { progress, task ->
                onProgress(progress, task)
            }

            emit(ScanState.Completed(result))
        } catch (e: Exception) {
            emit(ScanState.Error(e.message ?: "Bilinmeyen bir hata oluştu"))
        }
    }
}
