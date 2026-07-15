package com.storagemanager.domain.usecase

import com.storagemanager.domain.model.JunkPhoto
import com.storagemanager.domain.model.LargeFile
import com.storagemanager.domain.repository.StorageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Öğe silme kullanım senaryosu (use case).
 *
 * Fotoğraf ve dosya silme işlemlerini repository üzerinden yönetir.
 * Hata yönetimi ile güvenli silme sağlar.
 */
@Singleton
class DeleteItemsUseCase @Inject constructor(
    private val repository: StorageRepository
) {

    /**
     * Seçili fotoğrafları siler.
     *
     * ContentResolver üzerinden MediaStore'daki fotoğrafları kaldırır.
     *
     * @param photos Silinecek fotoğrafların listesi
     * @return Başarılı ise silinen fotoğraf sayısı, aksi halde hata
     */
    suspend fun deletePhotos(photos: List<JunkPhoto>): Result<Int> {
        return try {
            repository.deletePhotos(photos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Seçili dosyaları siler.
     *
     * Dosya sisteminden ve MediaStore'dan dosyaları kaldırır.
     *
     * @param files Silinecek dosyaların listesi
     * @return Başarılı ise silinen dosya sayısı, aksi halde hata
     */
    suspend fun deleteFiles(files: List<LargeFile>): Result<Int> {
        return try {
            repository.deleteFiles(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
