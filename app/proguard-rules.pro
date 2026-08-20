# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Gson — jenerik tip bilgisi TypeToken için gereklidir.
# (Gson 2.10+ kendi kurallarını da içerir; burada açıkça garanti altına alıyoruz.)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# StorageManager modelleri (Gson ile serileştiriliyor)
-keep class com.storagemanager.domain.model.** { *; }

# Room entity'leri
-keep class com.storagemanager.data.local.** { *; }

# WorkManager işçisi yansıma ile örneklenir
-keep class com.storagemanager.worker.AutoScanWorker { *; }

# Play Billing ve AdMob kendi consumer kurallarını getirir; yalnızca
# R8'in uyarı vermemesi için isteğe bağlı sınıflar susturuluyor.
-dontwarn com.google.android.gms.**
-dontwarn com.android.billingclient.**
