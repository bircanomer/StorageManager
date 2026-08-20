package com.storagemanager.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.storagemanager.data.repository.StorageRepositoryImpl
import com.storagemanager.domain.repository.StorageRepository
import com.storagemanager.ml.BlurDetector
import com.storagemanager.ml.DuplicateDetector
import com.storagemanager.scanner.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Hilt bağımlılık enjeksiyon modülü.
 *
 * Singleton kapsamındaki tüm bağımlılıkları yapılandırır:
 * - ML modülleri (BlurDetector, DuplicateDetector)
 * - DataStore ayar deposu
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

    /**
     * DataStore<Preferences> singleton örneğini sağlar.
     * SettingsViewModel tarafından ayar kalıcılığı için kullanılır.
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.dataStore
    }

    /**
     * v3 → v4: Gereksiz fotoğraflar JSON metninden ayrı tabloya taşındı.
     *
     * Gerçek bir migrasyon yazılıyor çünkü aynı veritabanında kullanıcının çöp kutusu
     * (`trash_items`) da duruyor; yıkıcı migrasyon çöp kutusundaki dosyaları
     * geri alınamaz şekilde sahipsiz bırakırdı.
     */
    private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_junk_photos` (
                    `rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `photoId` INTEGER NOT NULL,
                    `uri` TEXT NOT NULL,
                    `path` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `size` INTEGER NOT NULL,
                    `dateModified` INTEGER NOT NULL,
                    `width` INTEGER NOT NULL,
                    `height` INTEGER NOT NULL,
                    `junkType` TEXT NOT NULL,
                    `score` REAL NOT NULL,
                    `groupId` TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_junk_photos_photoId` ON `cached_junk_photos` (`photoId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_junk_photos_junkType` ON `cached_junk_photos` (`junkType`)")

            // photosJson sütununu kaldırmak için tabloyu yeniden oluştur
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_scan_results_new` (
                    `id` INTEGER NOT NULL,
                    `largeFilesJson` TEXT NOT NULL,
                    `unusedAppsJson` TEXT NOT NULL,
                    `cacheInfosJson` TEXT NOT NULL,
                    `duplicateDocsJson` TEXT NOT NULL,
                    `downloadJunksJson` TEXT NOT NULL,
                    `systemJunksJson` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `cached_scan_results_new`
                    (`id`, `largeFilesJson`, `unusedAppsJson`, `cacheInfosJson`,
                     `duplicateDocsJson`, `downloadJunksJson`, `systemJunksJson`)
                SELECT `id`, `largeFilesJson`, `unusedAppsJson`, `cacheInfosJson`,
                       `duplicateDocsJson`, `downloadJunksJson`, `systemJunksJson`
                FROM `cached_scan_results`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `cached_scan_results`")
            db.execSQL("ALTER TABLE `cached_scan_results_new` RENAME TO `cached_scan_results`")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): com.storagemanager.data.local.AppDatabase {
        return androidx.room.Room.databaseBuilder(
            context,
            com.storagemanager.data.local.AppDatabase::class.java,
            "storage_manager_db"
        )
            .addMigrations(MIGRATION_3_4)
            // v3 öncesi sürümler yalnızca tarama önbelleği içeriyordu; onlar için
            // yıkıcı geri dönüş kabul edilebilir.
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideScanResultDao(database: com.storagemanager.data.local.AppDatabase): com.storagemanager.data.local.ScanResultDao {
        return database.scanResultDao()
    }

    @Provides
    @Singleton
    fun provideTrashDao(database: com.storagemanager.data.local.AppDatabase): com.storagemanager.data.local.TrashDao {
        return database.trashDao()
    }

    @Provides
    @Singleton
    fun provideGson(): com.google.gson.Gson {
        return com.google.gson.Gson()
    }
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

    /**
     * MessengerAnalyzer arayüzünü MessengerAnalyzerImpl'e bağlar.
     */
    @Binds
    @Singleton
    abstract fun bindMessengerAnalyzer(impl: MessengerAnalyzerImpl): MessengerAnalyzer

    @Binds
    @Singleton
    abstract fun bindDocumentAnalyzer(impl: DocumentAnalyzerImpl): DocumentAnalyzer

    @Binds
    @Singleton
    abstract fun bindSystemJunkAnalyzer(impl: SystemJunkAnalyzerImpl): SystemJunkAnalyzer

    @Binds
    @Singleton
    abstract fun bindTreemapAnalyzer(impl: TreemapAnalyzerImpl): TreemapAnalyzer

    /**
     * Analytics arayüzünü Firebase uygulamasına bağlar.
     * google-services.json yoksa uygulama sessizce devre dışı kalır.
     */
    @Binds
    @Singleton
    abstract fun bindAnalytics(
        impl: com.storagemanager.analytics.FirebaseAnalyticsTracker
    ): com.storagemanager.analytics.Analytics
}
