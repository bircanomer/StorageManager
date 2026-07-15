package com.storagemanager.di

import com.storagemanager.data.repository.StorageRepositoryImpl
import com.storagemanager.domain.repository.StorageRepository
import com.storagemanager.ml.BlurDetector
import com.storagemanager.ml.DuplicateDetector
import com.storagemanager.scanner.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bağımlılık enjeksiyon modülü.
 *
 * Singleton kapsamındaki tüm bağımlılıkları yapılandırır:
 * - ML modülleri (BlurDetector, DuplicateDetector)
 * - Analizör arayüz bağlamaları
 * - Repository arayüz bağlaması
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * BlurDetector singleton örneğini sağlar.
     */
    @Provides
    @Singleton
    fun provideBlurDetector(): BlurDetector = BlurDetector()

    /**
     * DuplicateDetector singleton örneğini sağlar.
     */
    @Provides
    @Singleton
    fun provideDuplicateDetector(): DuplicateDetector = DuplicateDetector()
}

/**
 * Arayüz bağlama modülü.
 *
 * @Binds ile arayüzleri somut implementasyonlara bağlar.
 * Dagger bu bağlamaları derleme zamanında doğrular.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    /**
     * StorageAnalyzer arayüzünü StorageAnalyzerImpl'e bağlar.
     */
    @Binds
    @Singleton
    abstract fun bindStorageAnalyzer(impl: StorageAnalyzerImpl): StorageAnalyzer

    /**
     * PhotoAnalyzer arayüzünü PhotoAnalyzerImpl'e bağlar.
     */
    @Binds
    @Singleton
    abstract fun bindPhotoAnalyzer(impl: PhotoAnalyzerImpl): PhotoAnalyzer

    /**
     * AppAnalyzer arayüzünü AppAnalyzerImpl'e bağlar.
     */
    @Binds
    @Singleton
    abstract fun bindAppAnalyzer(impl: AppAnalyzerImpl): AppAnalyzer

    /**
     * FileAnalyzer arayüzünü FileAnalyzerImpl'e bağlar.
     */
    @Binds
    @Singleton
    abstract fun bindFileAnalyzer(impl: FileAnalyzerImpl): FileAnalyzer

    /**
     * StorageRepository arayüzünü StorageRepositoryImpl'e bağlar.
     */
    @Binds
    @Singleton
    abstract fun bindStorageRepository(impl: StorageRepositoryImpl): StorageRepository
}
